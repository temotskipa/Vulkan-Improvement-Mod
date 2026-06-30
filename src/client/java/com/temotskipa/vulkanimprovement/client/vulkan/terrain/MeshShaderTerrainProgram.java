package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

@SuppressWarnings("resource")
public final class MeshShaderTerrainProgram {
    private static final MeshShaderTerrainProgram INSTANCE = new MeshShaderTerrainProgram();
    private static final int DEPTH_ATTACHMENT_FORMAT = 126;
    private final Map<PipelineKey, MeshPipeline> pipelineCache = new HashMap<>();
    private final LongAdder pipelineCompiles = new LongAdder();
    private final LongAdder meshTaskDispatches = new LongAdder();
    private final LongAdder meshTaskDirectDispatches = new LongAdder();
    private final LongAdder meshTaskIndirectDispatches = new LongAdder();
    private final LongAdder meshTaskGpuCommandPreparations = new LongAdder();
    private final LongAdder meshTasksDispatched = new LongAdder();
    private final LongAdder textureDescriptorBufferBinds = new LongAdder();
    private final LongAdder textureDescriptorBufferMissing = new LongAdder();
    private volatile VulkanDevice device;
    private volatile long taskModule;
    private volatile long meshModule;
    private volatile long fragmentModule;
    private volatile long commandModule;
    private volatile CommandPipeline commandPipeline;
    private volatile String lastError = "";
    private volatile String taskShaderCompileSource = "unconfigured";
    private volatile String meshShaderCompileSource = "unconfigured";
    private volatile String fragmentShaderCompileSource = "unconfigured";
    private volatile String commandShaderCompileSource = "unconfigured";
    
    private MeshShaderTerrainProgram() {
    }
    
    public static MeshShaderTerrainProgram get() {
        return INSTANCE;
    }
    
    private static void applyBlendInformation(VkPipelineColorBlendAttachmentState attachment, BlendFunction blendFunction) {
        attachment.blendEnable(true).colorBlendOp(VulkanConst.toVk(blendFunction.color().op())).alphaBlendOp(VulkanConst.toVk(blendFunction.alpha().op())).dstAlphaBlendFactor(VulkanConst.toVk(blendFunction.alpha().destFactor())).dstColorBlendFactor(VulkanConst.toVk(blendFunction.color().destFactor())).srcAlphaBlendFactor(VulkanConst.toVk(blendFunction.alpha().sourceFactor())).srcColorBlendFactor(VulkanConst.toVk(blendFunction.color().sourceFactor()));
    }
    
    private static void destroyModule(VulkanDevice device, long module) {
        if (module != 0L) {
            VK12.vkDestroyShaderModule(device.vkDevice(), module, null);
        }
    }
    
    private static void writeCameraPushConstants(ByteBuffer target) {
        double cameraX = 0.0;
        double cameraY = 0.0;
        double cameraZ = 0.0;
        float row0X = 1.0F;
        float row0Y = 0.0F;
        float row0Z = 0.0F;
        float row0W = 0.0F;
        float row1X = 0.0F;
        float row1Y = 1.0F;
        float row1Z = 0.0F;
        float row1W = 0.0F;
        float row2X = 0.0F;
        float row2Y = 0.0F;
        float row2Z = 1.0F;
        float row2W = 0.0F;
        float row3X = 0.0F;
        float row3Y = 0.0F;
        float row3Z = 0.0F;
        float row3W = 1.0F;
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (camera.isInitialized()) {
            Vec3 cameraPosition = camera.position();
            cameraX = cameraPosition.x();
            cameraY = cameraPosition.y();
            cameraZ = cameraPosition.z();
            Matrix4f viewRotationProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
            row0X = viewRotationProjection.m00();
            row0Y = viewRotationProjection.m10();
            row0Z = viewRotationProjection.m20();
            row0W = viewRotationProjection.m30();
            row1X = viewRotationProjection.m01();
            row1Y = viewRotationProjection.m11();
            row1Z = viewRotationProjection.m21();
            row1W = viewRotationProjection.m31();
            row2X = viewRotationProjection.m02();
            row2Y = viewRotationProjection.m12();
            row2Z = viewRotationProjection.m22();
            row2W = viewRotationProjection.m32();
            row3X = viewRotationProjection.m03();
            row3Y = viewRotationProjection.m13();
            row3Z = viewRotationProjection.m23();
            row3W = viewRotationProjection.m33();
        }
        putVec4(target, (float) cameraX, (float) cameraY, (float) cameraZ, 0.0F);
        putVec4(target, row0X, row0Y, row0Z, row0W);
        putVec4(target, row1X, row1Y, row1Z, row1W);
        putVec4(target, row2X, row2Y, row2Z, row2W);
        putVec4(target, row3X, row3Y, row3Z, row3W);
    }
    
    private static int meshletFrustumCullingPushConstant() {
        return TerrainRendererDebugConfig.meshletFrustumCullingEnabled() ? 1 : 0;
    }
    
    private static void putVec4(ByteBuffer target, float x, float y, float z, float w) {
        target.putFloat(x);
        target.putFloat(y);
        target.putFloat(z);
        target.putFloat(w);
    }
    
    private static DescriptorHeapTerrainResources.TextureDescriptorLayout queryTextureDescriptorLayout(VulkanDevice device, long descriptorSetLayout) {
        long[] layoutSize = new long[1];
        long[] blockAtlasImageOffset = new long[1];
        long[] blockAtlasSamplerOffset = new long[1];
        long[] lightmapImageOffset = new long[1];
        long[] lightmapSamplerOffset = new long[1];
        EXTDescriptorBuffer.vkGetDescriptorSetLayoutSizeEXT(device.vkDevice(), descriptorSetLayout, layoutSize);
        EXTDescriptorBuffer.vkGetDescriptorSetLayoutBindingOffsetEXT(device.vkDevice(), descriptorSetLayout, 0, blockAtlasImageOffset);
        EXTDescriptorBuffer.vkGetDescriptorSetLayoutBindingOffsetEXT(device.vkDevice(), descriptorSetLayout, 1, blockAtlasSamplerOffset);
        EXTDescriptorBuffer.vkGetDescriptorSetLayoutBindingOffsetEXT(device.vkDevice(), descriptorSetLayout, 2, lightmapImageOffset);
        EXTDescriptorBuffer.vkGetDescriptorSetLayoutBindingOffsetEXT(device.vkDevice(), descriptorSetLayout, 3, lightmapSamplerOffset);
        DescriptorHeapTerrainResources.Layout terrainLayout = DescriptorHeapTerrainResources.get().layout();
        return new DescriptorHeapTerrainResources.TextureDescriptorLayout(layoutSize[0], blockAtlasImageOffset[0], blockAtlasSamplerOffset[0], lightmapImageOffset[0], lightmapSamplerOffset[0], Math.max(terrainLayout.descriptorBufferSampledImageDescriptorBytes(), 1L), Math.max(terrainLayout.descriptorBufferSamplerDescriptorBytes(), 1L));
    }
    
    public synchronized void configure(VulkanDevice device) {
        shutdown();
        this.device = device;
        try (Compiler compiler = new Compiler()) {
            this.taskModule = compiler.compile(device, TerrainShaderSource.TASK_SHADER, TerrainShaderSource.TASK_SHADER_SPIRV, Shaderc.shaderc_task_shader, TerrainShaderSource.load(TerrainShaderSource.TASK_SHADER));
            this.taskShaderCompileSource = compiler.lastCompileSource();
            this.meshModule = compiler.compile(device, TerrainShaderSource.MESH_SHADER, TerrainShaderSource.MESH_SHADER_SPIRV, Shaderc.shaderc_mesh_shader, TerrainShaderSource.load(TerrainShaderSource.MESH_SHADER));
            this.meshShaderCompileSource = compiler.lastCompileSource();
            this.fragmentModule = compiler.compile(device, TerrainShaderSource.FRAGMENT_SHADER, TerrainShaderSource.FRAGMENT_SHADER_SPIRV, Shaderc.shaderc_fragment_shader, TerrainShaderSource.load(TerrainShaderSource.FRAGMENT_SHADER));
            this.fragmentShaderCompileSource = compiler.lastCompileSource();
            this.commandModule = compiler.compile(device, TerrainShaderSource.MESH_TASK_COMMAND_SHADER, TerrainShaderSource.MESH_TASK_COMMAND_SHADER_SPIRV, Shaderc.shaderc_compute_shader, TerrainShaderSource.load(TerrainShaderSource.MESH_TASK_COMMAND_SHADER));
            this.commandShaderCompileSource = compiler.lastCompileSource();
            this.commandPipeline = createCommandPipeline(device);
            this.lastError = "";
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_SHADER_MODULE, this.taskModule, "VIM Terrain Task Shader");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_SHADER_MODULE, this.meshModule, "VIM Terrain Mesh Shader");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_SHADER_MODULE, this.fragmentModule, "VIM Terrain Fragment Shader");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_SHADER_MODULE, this.commandModule, "VIM Terrain Mesh Task Command Shader");
        } catch (RuntimeException ex) {
            shutdown();
            this.lastError = ex.getMessage();
            throw ex;
        }
    }
    
    public synchronized void shutdown() {
        VulkanDevice activeDevice = this.device;
        if (activeDevice != null) {
            for (MeshPipeline pipeline : this.pipelineCache.values()) {
                pipeline.destroy(activeDevice);
            }
            this.pipelineCache.clear();
            CommandPipeline activeCommandPipeline = this.commandPipeline;
            if (activeCommandPipeline != null) {
                activeCommandPipeline.destroy(activeDevice);
            }
            this.commandPipeline = null;
            destroyModule(activeDevice, this.commandModule);
            destroyModule(activeDevice, this.fragmentModule);
            destroyModule(activeDevice, this.meshModule);
            destroyModule(activeDevice, this.taskModule);
        }
        this.fragmentModule = 0L;
        this.meshModule = 0L;
        this.taskModule = 0L;
        this.commandModule = 0L;
        this.device = null;
    }
    
    public boolean ready() {
        return this.taskModule != 0L && this.meshModule != 0L && this.fragmentModule != 0L && this.commandModule != 0L && this.commandPipeline != null;
    }
    
    public synchronized boolean drawTerrain(VkCommandBuffer commandBuffer, VulkanRenderPipeline vanillaPipeline, boolean hasDepth, TerrainMeshTaskDispatch dispatch, long meshletHeaderAddress, long vertexPayloadAddress, long indexPayloadAddress) {
        if (!ready() || dispatch == null || !dispatch.ready() || meshletHeaderAddress == 0L || vertexPayloadAddress == 0L) {
            return false;
        }
        MeshPipeline meshPipeline = this.pipelineCache.computeIfAbsent(new PipelineKey(vanillaPipeline.info(), hasDepth), key -> createPipeline(vanillaPipeline.device(), key.info(), key.hasDepth()));
        if (!bindTerrainTextureDescriptorBuffer(commandBuffer, meshPipeline)) {
            return false;
        }
        VK12.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.handle());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstants = stack.malloc(TerrainGpuLayout.TERRAIN_PUSH_CONSTANT_BYTES);
            pushConstants.putLong(meshletHeaderAddress);
            pushConstants.putLong(vertexPayloadAddress);
            pushConstants.putLong(indexPayloadAddress);
            pushConstants.putLong(DescriptorHeapTerrainResources.get().terrainDebugCounterAddress());
            pushConstants.putLong(dispatch.visibleMeshletListAddress());
            pushConstants.putLong(dispatch.workQueueAddress());
            pushConstants.putLong(DescriptorHeapTerrainResources.get().materialTableAddress());
            pushConstants.putInt(dispatch.taskCount());
            pushConstants.putInt(meshletFrustumCullingPushConstant());
            pushConstants.putInt(dispatch.meshletOffset());
            pushConstants.putInt(dispatch.layerOrdinal());
            writeCameraPushConstants(pushConstants);
            pushConstants.flip();
            VK12.vkCmdPushConstants(commandBuffer, meshPipeline.layout(), EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT, 0, pushConstants);
        }
        FragmentShadingRateController.get().applyToTerrain(commandBuffer);
        if (dispatch.usesIndirectCommand()) {
            EXTMeshShader.vkCmdDrawMeshTasksIndirectEXT(commandBuffer, dispatch.indirectCommandBuffer(), dispatch.indirectCommandOffset(), 1, dispatch.indirectCommandStride());
            this.meshTaskIndirectDispatches.increment();
        } else {
            EXTMeshShader.vkCmdDrawMeshTasksEXT(commandBuffer, dispatch.taskCount(), 1, 1);
            this.meshTaskDirectDispatches.increment();
        }
        this.meshTaskDispatches.increment();
        this.meshTasksDispatched.add(dispatch.taskCount());
        return true;
    }
    
    private boolean bindTerrainTextureDescriptorBuffer(VkCommandBuffer commandBuffer, MeshPipeline meshPipeline) {
        DescriptorHeapTerrainResources resources = DescriptorHeapTerrainResources.get();
        long descriptorBufferAddress = resources.terrainDescriptorBufferAddress();
        if (descriptorBufferAddress == 0L || !resources.writeTerrainTextureDescriptors(this.device, meshPipeline.textureDescriptorLayout())) {
            this.textureDescriptorBufferMissing.increment();
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferBindingInfoEXT.Buffer bindings = VkDescriptorBufferBindingInfoEXT.calloc(1, stack);
            bindings.get(0).sType$Default().address$(descriptorBufferAddress).usage(EXTDescriptorBuffer.VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT);
            EXTDescriptorBuffer.vkCmdBindDescriptorBuffersEXT(commandBuffer, bindings);
            EXTDescriptorBuffer.vkCmdSetDescriptorBufferOffsetsEXT(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.layout(), 0, stack.ints(0), stack.longs(0L));
            this.textureDescriptorBufferBinds.increment();
            return true;
        }
    }
    
    public synchronized boolean prepareWorkQueueIndirectCommand(TerrainMeshTaskDispatch dispatch) {
        VulkanDevice activeDevice = this.device;
        if (activeDevice == null || this.commandPipeline == null || dispatch == null || !dispatch.ready() || !dispatch.usesIndirectCommand() || !dispatch.usesWorkQueue()) {
            return false;
        }
        VulkanCommandEncoder encoder = activeDevice.createCommandEncoder();
        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
        if (!recordWorkQueueIndirectCommand(commandBuffer, dispatch)) {
            return false;
        }
        VulkanUtils.crashIfFailure(activeDevice, VK12.vkEndCommandBuffer(commandBuffer), "Failed to end VIM terrain mesh-task command generation buffer");
        encoder.execute(commandBuffer);
        return true;
    }
    
    private boolean recordWorkQueueIndirectCommand(VkCommandBuffer commandBuffer, TerrainMeshTaskDispatch dispatch) {
        CommandPipeline pipeline = this.commandPipeline;
        if (pipeline == null || dispatch.workQueueAddress() == 0L || dispatch.indirectCommandAddress() == 0L || dispatch.indirectCommandStride() <= 0) {
            return false;
        }
        long commandBaseAddress = dispatch.indirectCommandAddress() - dispatch.indirectCommandOffset();
        int commandIndex = (int) (dispatch.indirectCommandOffset() / dispatch.indirectCommandStride());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstants = stack.malloc(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES);
            pushConstants.putLong(dispatch.workQueueAddress());
            pushConstants.putLong(commandBaseAddress);
            pushConstants.putInt(commandIndex);
            pushConstants.putInt(dispatch.taskCount());
            pushConstants.putInt(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_USE_PUSH_TASK_COUNT_FLAG);
            pushConstants.putInt(0);
            pushConstants.flip();
            VK12.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.handle());
            VK12.vkCmdPushConstants(commandBuffer, pipeline.layout(), VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
            VK12.vkCmdDispatch(commandBuffer, 1, 1, 1);
            VkMemoryBarrier.Buffer memoryBarrier = VkMemoryBarrier.calloc(1, stack).sType$Default().srcAccessMask(VK10.VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK10.VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            VK10.vkCmdPipelineBarrier(commandBuffer, VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK10.VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT, 0, memoryBarrier, null, null);
        }
        this.meshTaskGpuCommandPreparations.increment();
        return true;
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ready", ready());
        map.put("taskModule", this.taskModule);
        map.put("meshModule", this.meshModule);
        map.put("fragmentModule", this.fragmentModule);
        map.put("commandModule", this.commandModule);
        map.put("taskStage", EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT);
        map.put("meshStage", EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT);
        map.put("commandStage", VK10.VK_SHADER_STAGE_COMPUTE_BIT);
        map.put("taskShaderResource", TerrainShaderSource.TASK_SHADER);
        map.put("meshShaderResource", TerrainShaderSource.MESH_SHADER);
        map.put("fragmentShaderResource", TerrainShaderSource.FRAGMENT_SHADER);
        map.put("meshTaskCommandShaderResource", TerrainShaderSource.MESH_TASK_COMMAND_SHADER);
        map.put("taskShaderCompileSource", this.taskShaderCompileSource);
        map.put("meshShaderCompileSource", this.meshShaderCompileSource);
        map.put("fragmentShaderCompileSource", this.fragmentShaderCompileSource);
        map.put("commandShaderCompileSource", this.commandShaderCompileSource);
        map.put("enableMeshletFrustumCulling", meshletFrustumCullingPushConstant() != 0);
        map.put("pipelineCacheSize", this.pipelineCache.size());
        map.put("commandPipelineReady", this.commandPipeline != null);
        map.put("pipelineCompiles", this.pipelineCompiles.sum());
        map.put("meshTaskDispatches", this.meshTaskDispatches.sum());
        map.put("meshTaskDirectDispatches", this.meshTaskDirectDispatches.sum());
        map.put("meshTaskIndirectDispatches", this.meshTaskIndirectDispatches.sum());
        map.put("meshTaskGpuCommandPreparations", this.meshTaskGpuCommandPreparations.sum());
        map.put("meshTasksDispatched", this.meshTasksDispatched.sum());
        map.put("textureDescriptorBufferBinds", this.textureDescriptorBufferBinds.sum());
        map.put("textureDescriptorBufferMissing", this.textureDescriptorBufferMissing.sum());
        DescriptorHeapTerrainResources.TextureDescriptorLayout descriptorLayout = null;
        for (MeshPipeline pipeline : this.pipelineCache.values()) {
            descriptorLayout = pipeline.textureDescriptorLayout();
            break;
        }
        map.put("textureDescriptorLayout", descriptorLayout == null ? Map.of() : descriptorLayout.asMap());
        map.put("lastError", this.lastError);
        return map;
    }
    
    private MeshPipeline createPipeline(VulkanDevice device, RenderPipeline pipelineInfo, boolean hasDepth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer textureBindings = VkDescriptorSetLayoutBinding.calloc(4, stack);
            textureBindings.get(0).binding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE).descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            textureBindings.get(1).binding(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLER).descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            textureBindings.get(2).binding(2).descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE).descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            textureBindings.get(3).binding(3).descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLER).descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().flags(EXTDescriptorBuffer.VK_DESCRIPTOR_SET_LAYOUT_CREATE_DESCRIPTOR_BUFFER_BIT_EXT).pBindings(textureBindings);
            LongBuffer descriptorSetLayoutHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK12.vkCreateDescriptorSetLayout(device.vkDevice(), descriptorSetLayoutCreateInfo, null, descriptorSetLayoutHandle), "Failed to create VIM terrain texture descriptor-buffer layout for " + pipelineInfo.getLocation());
            long textureDescriptorSetLayout = descriptorSetLayoutHandle.get(0);
            DescriptorHeapTerrainResources.TextureDescriptorLayout textureDescriptorLayout = queryTextureDescriptorLayout(device, textureDescriptorSetLayout);
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack).stageFlags(EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT).offset(0).size(TerrainGpuLayout.TERRAIN_PUSH_CONSTANT_BYTES);
            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(textureDescriptorSetLayout)).pPushConstantRanges(pushConstantRanges);
            LongBuffer layoutHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK12.vkCreatePipelineLayout(device.vkDevice(), layoutCreateInfo, null, layoutHandle), "Failed to create VIM terrain mesh pipeline layout for " + pipelineInfo.getLocation());
            long pipelineLayout = layoutHandle.get(0);
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(3, stack);
            ByteBuffer entryPoint = stack.UTF8("main");
            shaderStages.get(0).sType$Default().stage(EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT).module(this.taskModule).pName(entryPoint);
            shaderStages.get(1).sType$Default().stage(EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT).module(this.meshModule).pName(entryPoint);
            shaderStages.get(2).sType$Default().stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT).module(this.fragmentModule).pName(entryPoint);
            VkPipelineRasterizationStateCreateInfo rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default().polygonMode(VulkanConst.toVk(pipelineInfo.getPolygonMode())).cullMode(VK10.VK_CULL_MODE_NONE).frontFace(VK10.VK_FRONT_FACE_CLOCKWISE).lineWidth(1.0F);
            DepthStencilState depthStencilStateInfo = hasDepth ? pipelineInfo.getDepthStencilState() : null;
            VkPipelineDepthStencilStateCreateInfo depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default();
            if (depthStencilStateInfo != null) {
                rasterizationState.depthBiasEnable(depthStencilStateInfo.depthBiasConstant() != 0.0F && depthStencilStateInfo.depthBiasScaleFactor() != 0.0F).depthBiasConstantFactor(depthStencilStateInfo.depthBiasConstant()).depthBiasSlopeFactor(depthStencilStateInfo.depthBiasScaleFactor());
                depthStencilState.depthTestEnable(true).depthWriteEnable(depthStencilStateInfo.writeDepth()).depthCompareOp(VulkanConst.toVk(depthStencilStateInfo.depthTest()));
            }
            ColorTargetState[] colorTargets = pipelineInfo.getColorTargetStates();
            VkPipelineColorBlendAttachmentState.Buffer colorAttachments = VkPipelineColorBlendAttachmentState.calloc(colorTargets.length, stack);
            for (int i = 0; i < colorTargets.length; i++) {
                ColorTargetState colorTarget = colorTargets[i];
                VkPipelineColorBlendAttachmentState attachment = colorAttachments.get(i);
                attachment.colorWriteMask(colorTarget == null ? 0 : VulkanConst.toVk(colorTarget));
                if (colorTarget != null) {
                    colorTarget.blendFunction().ifPresent(blendFunction -> applyBlendInformation(attachment, blendFunction));
                }
            }
            VkPipelineColorBlendStateCreateInfo colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default().pAttachments(colorAttachments);
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineMultisampleStateCreateInfo multisampleState = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default().rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT).sampleShadingEnable(false);
            IntBuffer dynamicStates = stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR, KHRFragmentShadingRate.VK_DYNAMIC_STATE_FRAGMENT_SHADING_RATE_KHR);
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default().pDynamicStates(dynamicStates);
            IntBuffer colorAttachmentFormats = stack.mallocInt(colorTargets.length);
            for (int i = 0; i < colorTargets.length; i++) {
                ColorTargetState colorTarget = colorTargets[i];
                colorAttachmentFormats.put(i, colorTarget == null ? 0 : VulkanConst.toVk(colorTarget.format()));
            }
            VkPipelineRenderingCreateInfoKHR renderingInfo = VkPipelineRenderingCreateInfoKHR.calloc(stack).sType$Default().pColorAttachmentFormats(colorAttachmentFormats).depthAttachmentFormat(hasDepth ? DEPTH_ATTACHMENT_FORMAT : 0);
            VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack).sType$Default().flags(EXTDescriptorBuffer.VK_PIPELINE_CREATE_DESCRIPTOR_BUFFER_BIT_EXT).pStages(shaderStages).pRasterizationState(rasterizationState).pDepthStencilState(depthStencilState).pColorBlendState(colorBlendState).pViewportState(viewportState).pMultisampleState(multisampleState).pDynamicState(dynamicState).layout(pipelineLayout).pNext(renderingInfo);
            LongBuffer pipelineHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK12.vkCreateGraphicsPipelines(device.vkDevice(), 0L, pipelineCreateInfo, null, pipelineHandle), "Failed to create VIM terrain mesh pipeline for " + pipelineInfo.getLocation());
            long pipeline = pipelineHandle.get(0);
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_PIPELINE_LAYOUT, pipelineLayout, "VIM Terrain Mesh Pipeline Layout");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, textureDescriptorSetLayout, "VIM Terrain Texture Descriptor-Buffer Layout");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, "VIM Terrain Mesh Pipeline " + pipelineInfo.getLocation());
            this.pipelineCompiles.increment();
            this.lastError = "";
            return new MeshPipeline(pipeline, pipelineLayout, textureDescriptorSetLayout, textureDescriptorLayout);
        } catch (RuntimeException ex) {
            this.lastError = ex.getMessage();
            throw ex;
        }
    }
    
    private CommandPipeline createCommandPipeline(VulkanDevice device) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES);
            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pPushConstantRanges(pushConstantRanges);
            LongBuffer layoutHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK12.vkCreatePipelineLayout(device.vkDevice(), layoutCreateInfo, null, layoutHandle), "Failed to create VIM terrain mesh-task command pipeline layout");
            long pipelineLayout = layoutHandle.get(0);
            ByteBuffer entryPoint = stack.UTF8("main");
            VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default().stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(this.commandModule).pName(entryPoint);
            VkComputePipelineCreateInfo.Buffer pipelineCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack).sType$Default().stage(shaderStage).layout(pipelineLayout);
            LongBuffer pipelineHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK12.vkCreateComputePipelines(device.vkDevice(), 0L, pipelineCreateInfo, null, pipelineHandle), "Failed to create VIM terrain mesh-task command compute pipeline");
            long pipeline = pipelineHandle.get(0);
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_PIPELINE_LAYOUT, pipelineLayout, "VIM Terrain Mesh Task Command Pipeline Layout");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, "VIM Terrain Mesh Task Command Pipeline");
            this.pipelineCompiles.increment();
            return new CommandPipeline(pipeline, pipelineLayout);
        }
    }
    
    private record MeshPipeline(long handle, long layout, long textureDescriptorSetLayout,
                                DescriptorHeapTerrainResources.TextureDescriptorLayout textureDescriptorLayout) {
        private void destroy(VulkanDevice device) {
            if (this.handle != 0L) {
                VK12.vkDestroyPipeline(device.vkDevice(), this.handle, null);
            }
            if (this.layout != 0L) {
                VK12.vkDestroyPipelineLayout(device.vkDevice(), this.layout, null);
            }
            if (this.textureDescriptorSetLayout != 0L) {
                VK12.vkDestroyDescriptorSetLayout(device.vkDevice(), this.textureDescriptorSetLayout, null);
            }
        }
    }
    
    private record CommandPipeline(long handle, long layout) {
        private void destroy(VulkanDevice device) {
            if (this.handle != 0L) {
                VK12.vkDestroyPipeline(device.vkDevice(), this.handle, null);
            }
            if (this.layout != 0L) {
                VK12.vkDestroyPipelineLayout(device.vkDevice(), this.layout, null);
            }
        }
    }
    
    private record PipelineKey(RenderPipeline info, boolean hasDepth) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PipelineKey(
                    RenderPipeline info1, boolean depth
            ) && this.info == info1 && this.hasDepth == depth;
        }
        
        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.info) + Boolean.hashCode(this.hasDepth);
        }
    }
    
    private static final class Compiler implements AutoCloseable {
        private final long compiler = Shaderc.shaderc_compiler_initialize();
        private final long options = Shaderc.shaderc_compile_options_initialize();
        private String lastCompileSource = "unconfigured";
        
        private Compiler() {
            Shaderc.shaderc_compile_options_set_target_env(this.options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_4);
            Shaderc.shaderc_compile_options_set_target_spirv(this.options, Shaderc.shaderc_spirv_version_1_6);
            Shaderc.shaderc_compile_options_set_optimization_level(this.options, Shaderc.shaderc_optimization_level_zero);
            Shaderc.shaderc_compile_options_set_generate_debug_info(this.options);
        }
        
        private static long createShaderModule(VulkanDevice device, String filename, ByteBuffer spirv) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                moduleCreateInfo.pCode(Objects.requireNonNull(spirv));
                long[] module = new long[1];
                VulkanUtils.crashIfFailure(device, VK12.vkCreateShaderModule(device.vkDevice(), moduleCreateInfo, null, module), "Failed to create " + filename);
                return module[0];
            }
        }
        
        private String lastCompileSource() {
            return this.lastCompileSource;
        }
        
        private long compile(VulkanDevice device, String glslResource, String spirvResource, int shaderKind, String source) {
            ByteBuffer precompiled = TerrainShaderSource.loadSpirv(spirvResource);
            if (precompiled != null) {
                this.lastCompileSource = "precompiled-spirv";
                return createShaderModule(device, glslResource, precompiled);
            }
            this.lastCompileSource = "runtime-shaderc";
            return compileFromSource(device, glslResource, shaderKind, source);
        }
        
        private long compileFromSource(VulkanDevice device, String filename, int shaderKind, String source) {
            long result = Shaderc.shaderc_compile_into_spv(this.compiler, source, shaderKind, filename, "main", this.options);
            try {
                int status = Shaderc.shaderc_result_get_compilation_status(result);
                if (status != Shaderc.shaderc_compilation_status_success) {
                    throw new IllegalStateException("Failed to compile " + filename + ": " + Shaderc.shaderc_result_get_error_message(result));
                }
                ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
                return createShaderModule(device, filename, spirv);
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        }
        
        @Override
        public void close() {
            Shaderc.shaderc_compile_options_release(this.options);
            Shaderc.shaderc_compiler_release(this.compiler);
        }
    }
}