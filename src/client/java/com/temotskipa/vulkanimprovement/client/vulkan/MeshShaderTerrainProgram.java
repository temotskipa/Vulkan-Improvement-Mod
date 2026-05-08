package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class MeshShaderTerrainProgram {
    private static final MeshShaderTerrainProgram INSTANCE = new MeshShaderTerrainProgram();
    private static final int DEPTH_ATTACHMENT_FORMAT = 126;
    private static final int TERRAIN_PUSH_CONSTANT_BYTES = 128;
    private static final String TASK_SHADER_SOURCE = """
            #version 460
            #extension GL_EXT_mesh_shader : require
            #extension GL_EXT_scalar_block_layout : require
            #extension GL_EXT_shader_explicit_arithmetic_types_int64 : require
            
            taskPayloadSharedEXT uint visibleMeshlet;
            layout(push_constant, scalar) uniform TerrainPushConstants {
                uint64_t meshletHeaders;
                uint64_t vertexPayload;
                uint64_t indexPayload;
                uint64_t debugCounters;
                uint meshletCount;
                uint debugFlags;
                uint meshletOffset;
                int layerOrdinal;
                vec4 cameraPosition;
                vec4 cameraRight;
                vec4 cameraUp;
                vec4 cameraForward;
                vec4 projection;
            } pc;
            
            void main() {
                visibleMeshlet = pc.meshletOffset + min(gl_WorkGroupID.x, pc.meshletCount - 1u);
                EmitMeshTasksEXT(1, 1, 1);
            }
            """;
    private static final String MESH_SHADER_SOURCE = """
            #version 460
            #extension GL_EXT_mesh_shader : require
            #extension GL_EXT_buffer_reference : require
            #extension GL_EXT_scalar_block_layout : require
            #extension GL_EXT_shader_explicit_arithmetic_types_int64 : require
            
            layout(local_size_x = 1) in;
            layout(triangles, max_vertices = 64, max_primitives = 32) out;
            
            struct MeshletHeader {
                uint64_t sectionNode;
                int sectionX;
                int sectionY;
                int sectionZ;
                int layer;
                int firstVertex;
                int vertexCount;
                int firstIndex;
                int indexCount;
                int vertexByteOffset;
                int indexByteOffset;
                int vertexBytes;
                int indexBytes;
                int flags0;
                int flags1;
            };
            
            struct BlockVertex {
                vec3 position;
                uint color;
                vec2 uv0;
                uint uv2;
            };
            
            layout(buffer_reference, scalar, buffer_reference_align = 8) readonly buffer MeshletHeaders {
                MeshletHeader headers[];
            };
            
            layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer TerrainVertices {
                BlockVertex vertices[];
            };

            layout(buffer_reference, scalar, buffer_reference_align = 4) buffer TerrainDebugCounters {
                uint candidateMeshlets;
                uint culledMeshlets;
                uint emittedMeshlets;
                uint reserved;
            };
            
            layout(push_constant, scalar) uniform TerrainPushConstants {
                uint64_t meshletHeaders;
                uint64_t vertexPayload;
                uint64_t indexPayload;
                uint64_t debugCounters;
                uint meshletCount;
                uint debugFlags;
                uint meshletOffset;
                int layerOrdinal;
                vec4 cameraPosition;
                vec4 cameraRight;
                vec4 cameraUp;
                vec4 cameraForward;
                vec4 projection;
            } pc;
            
            taskPayloadSharedEXT uint visibleMeshlet;
            layout(location = 0) out vec4 meshColor[];
            layout(location = 1) out vec2 meshUv0[];
            layout(location = 2) out vec2 meshLightUv[];

            bool cullMeshlet(MeshletHeader header) {
                vec3 center = vec3(
                    float(header.sectionX * 16) + 8.0,
                    float(header.sectionY * 16) + 8.0,
                    float(header.sectionZ * 16) + 8.0
                );
                vec3 relative = center - pc.cameraPosition.xyz;
                float viewX = dot(relative, pc.cameraRight.xyz);
                float viewY = dot(relative, pc.cameraUp.xyz);
                float viewZ = dot(relative, pc.cameraForward.xyz);
                float focal = max(pc.projection.x, 0.01);
                float aspect = max(pc.projection.y, 0.01);
                float nearPlane = max(pc.projection.z, 0.01);
                float farPlane = max(pc.projection.w, nearPlane + 1.0);
                float radius = 14.0;
                float positiveZ = max(viewZ, nearPlane);
                float maxX = positiveZ * aspect / focal + radius;
                float maxY = positiveZ / focal + radius;
                return viewZ < -radius || viewZ > farPlane + radius || abs(viewX) > maxX || abs(viewY) > maxY;
            }
            
            void main() {
                MeshletHeaders meshlets = MeshletHeaders(pc.meshletHeaders);
                TerrainDebugCounters counters = TerrainDebugCounters(pc.debugCounters);
                MeshletHeader header = meshlets.headers[visibleMeshlet];
                atomicAdd(counters.candidateMeshlets, 1u);
                if (cullMeshlet(header)) {
                    atomicAdd(counters.culledMeshlets, 1u);
                    SetMeshOutputsEXT(0u, 0u);
                    return;
                }
                atomicAdd(counters.emittedMeshlets, 1u);
                uint vertexCount = uint(clamp(header.vertexCount, 0, 64));
                uint quadCount = vertexCount / 4u;
                uint primitiveCount = min(quadCount * 2u, 32u);
                TerrainVertices payload = TerrainVertices(
                    pc.vertexPayload
                    + uint64_t(max(header.vertexByteOffset, 0))
                    + uint64_t(max(header.firstVertex, 0)) * uint64_t(28)
                );
                SetMeshOutputsEXT(vertexCount, primitiveCount);
                for (uint vertex = 0u; vertex < vertexCount; vertex++) {
                    BlockVertex blockVertex = payload.vertices[vertex];
                    vec3 world = vec3(
                        float(header.sectionX * 16) + blockVertex.position.x,
                        float(header.sectionY * 16) + blockVertex.position.y,
                        float(header.sectionZ * 16) + blockVertex.position.z
                    );
                    vec3 relative = world - pc.cameraPosition.xyz;
                    float viewX = dot(relative, pc.cameraRight.xyz);
                    float viewY = dot(relative, pc.cameraUp.xyz);
                    float viewZ = dot(relative, pc.cameraForward.xyz);
                    float focal = max(pc.projection.x, 0.01);
                    float aspect = max(pc.projection.y, 0.01);
                    gl_MeshVerticesEXT[vertex].gl_Position = vec4(viewX * focal / aspect, viewY * focal, viewZ * 0.2, viewZ);
                    meshColor[vertex] = unpackUnorm4x8(blockVertex.color);
                    meshUv0[vertex] = blockVertex.uv0;
                    meshLightUv[vertex] = vec2(
                        float(blockVertex.uv2 & 65535u) / 256.0,
                        float((blockVertex.uv2 >> 16) & 65535u) / 256.0
                    );
                }
                for (uint quad = 0u; quad < quadCount; quad++) {
                    uint baseVertex = quad * 4u;
                    uint primitive = quad * 2u;
                    gl_PrimitiveTriangleIndicesEXT[primitive] = uvec3(baseVertex, baseVertex + 1u, baseVertex + 2u);
                    gl_PrimitiveTriangleIndicesEXT[primitive + 1u] = uvec3(baseVertex, baseVertex + 2u, baseVertex + 3u);
                }
            }
            """;
    private static final String FRAGMENT_SHADER_SOURCE = """
            #version 460
            
            layout(location = 0) in vec4 meshColor;
            layout(location = 1) in vec2 meshUv0;
            layout(location = 2) in vec2 meshLightUv;
            layout(set = 0, binding = 0) uniform texture2D blockAtlasTexture;
            layout(set = 0, binding = 1) uniform sampler blockAtlasSampler;
            layout(set = 0, binding = 2) uniform texture2D lightmapTexture;
            layout(set = 0, binding = 3) uniform sampler lightmapSampler;
            layout(location = 0) out vec4 fragColor;
            
            void main() {
                vec4 atlas = texture(sampler2D(blockAtlasTexture, blockAtlasSampler), meshUv0);
                float alpha = atlas.a * meshColor.a;
                if (alpha < 0.05) {
                    discard;
                }
                vec3 light = max(texture(sampler2D(lightmapTexture, lightmapSampler), clamp(meshLightUv, vec2(0.0), vec2(0.99))).rgb, vec3(0.35));
                fragColor = vec4(atlas.rgb * light * meshColor.rgb, alpha);
            }
            """;
    private final Map<PipelineKey, MeshPipeline> pipelineCache = new HashMap<>();
    private final LongAdder pipelineCompiles = new LongAdder();
    private final LongAdder meshTaskDispatches = new LongAdder();
    private final LongAdder meshTasksDispatched = new LongAdder();
    private final LongAdder textureDescriptorBufferBinds = new LongAdder();
    private final LongAdder textureDescriptorBufferMissing = new LongAdder();
    private volatile VulkanDevice device;
    private volatile long taskModule;
    private volatile long meshModule;
    private volatile long fragmentModule;
    private volatile String lastError = "";
    
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
        float rightX = 1.0F;
        float rightY = 0.0F;
        float rightZ = 0.0F;
        float upX = 0.0F;
        float upY = 1.0F;
        float upZ = 0.0F;
        float forwardX = 0.0F;
        float forwardY = 0.0F;
        float forwardZ = 1.0F;
        float aspect = 16.0F / 9.0F;
        float fovDegrees = 70.0F;
        
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            Camera camera = minecraft.gameRenderer.mainCamera();
            if (camera.isInitialized()) {
                Vec3 cameraPosition = camera.position();
                Vector3fc left = camera.leftVector();
                Vector3fc up = camera.upVector();
                Vector3fc forward = camera.forwardVector();
                cameraX = cameraPosition.x();
                cameraY = cameraPosition.y();
                cameraZ = cameraPosition.z();
                rightX = -left.x();
                rightY = -left.y();
                rightZ = -left.z();
                upX = up.x();
                upY = up.y();
                upZ = up.z();
                forwardX = forward.x();
                forwardY = forward.y();
                forwardZ = forward.z();
                fovDegrees = Math.max(camera.getFov(), 1.0F);
            }
            int width = Math.max(minecraft.getWindow().getWidth(), 1);
            int height = Math.max(minecraft.getWindow().getHeight(), 1);
            aspect = (float) width / (float) height;
        }
        
        float focal = 1.0F / (float) Math.tan(Math.toRadians(fovDegrees) * 0.5F);
        putVec4(target, (float) cameraX, (float) cameraY, (float) cameraZ, 0.0F);
        putVec4(target, rightX, rightY, rightZ, 0.0F);
        putVec4(target, upX, upY, upZ, 0.0F);
        putVec4(target, forwardX, forwardY, forwardZ, 0.0F);
        putVec4(target, focal, aspect, 0.05F, 512.0F);
    }
    
    private static void putVec4(ByteBuffer target, float x, float y, float z, float w) {
        target.putFloat(x);
        target.putFloat(y);
        target.putFloat(z);
        target.putFloat(w);
    }
    
    public synchronized void configure(VulkanDevice device) {
        shutdown();
        this.device = device;
        try (Compiler compiler = new Compiler()) {
            this.taskModule = compiler.compile(device, "vim_terrain.task", Shaderc.shaderc_task_shader, TASK_SHADER_SOURCE);
            this.meshModule = compiler.compile(device, "vim_terrain.mesh", Shaderc.shaderc_mesh_shader, MESH_SHADER_SOURCE);
            this.fragmentModule = compiler.compile(device, "vim_terrain.frag", Shaderc.shaderc_fragment_shader, FRAGMENT_SHADER_SOURCE);
            this.lastError = "";
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_SHADER_MODULE, this.taskModule, "VIM Terrain Task Shader");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_SHADER_MODULE, this.meshModule, "VIM Terrain Mesh Shader");
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_SHADER_MODULE, this.fragmentModule, "VIM Terrain Fragment Shader");
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
            destroyModule(activeDevice, this.fragmentModule);
            destroyModule(activeDevice, this.meshModule);
            destroyModule(activeDevice, this.taskModule);
        }
        this.fragmentModule = 0L;
        this.meshModule = 0L;
        this.taskModule = 0L;
        this.device = null;
    }
    
    public boolean ready() {
        return this.taskModule != 0L && this.meshModule != 0L && this.fragmentModule != 0L;
    }
    
    public synchronized boolean drawTerrain(VkCommandBuffer commandBuffer, VulkanRenderPipeline vanillaPipeline, boolean hasDepth, int meshletOffset, int layerOrdinal, int taskCount, long meshletHeaderAddress, long vertexPayloadAddress, long indexPayloadAddress) {
        if (!ready() || taskCount <= 0 || meshletHeaderAddress == 0L || vertexPayloadAddress == 0L) {
            return false;
        }
        MeshPipeline meshPipeline = this.pipelineCache.computeIfAbsent(new PipelineKey(vanillaPipeline.info(), hasDepth), key -> createPipeline(vanillaPipeline.device(), key.info(), key.hasDepth()));
        VK12.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.handle());
        if (!bindTerrainTextureDescriptorBuffer(commandBuffer, meshPipeline)) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstants = stack.malloc(TERRAIN_PUSH_CONSTANT_BYTES);
            pushConstants.putLong(meshletHeaderAddress);
            pushConstants.putLong(vertexPayloadAddress);
            pushConstants.putLong(indexPayloadAddress);
            pushConstants.putLong(DescriptorHeapTerrainResources.get().terrainDebugCounterAddress());
            pushConstants.putInt(taskCount);
            pushConstants.putInt(0);
            pushConstants.putInt(meshletOffset);
            pushConstants.putInt(layerOrdinal);
            writeCameraPushConstants(pushConstants);
            pushConstants.flip();
            VK12.vkCmdPushConstants(commandBuffer, meshPipeline.layout(), EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT, 0, pushConstants);
        }
        FragmentShadingRateController.get().applyToTerrain(commandBuffer);
        EXTMeshShader.vkCmdDrawMeshTasksEXT(commandBuffer, taskCount, 1, 1);
        this.meshTaskDispatches.increment();
        this.meshTasksDispatched.add(taskCount);
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
            bindings.get(0)
                    .sType$Default()
                    .address$(descriptorBufferAddress)
                    .usage(EXTDescriptorBuffer.VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT);
            EXTDescriptorBuffer.vkCmdBindDescriptorBuffersEXT(commandBuffer, bindings);
            EXTDescriptorBuffer.vkCmdSetDescriptorBufferOffsetsEXT(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.layout(), 0, stack.ints(0), stack.longs(0L));
            this.textureDescriptorBufferBinds.increment();
            return true;
        }
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ready", ready());
        map.put("taskModule", this.taskModule);
        map.put("meshModule", this.meshModule);
        map.put("fragmentModule", this.fragmentModule);
        map.put("taskStage", EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT);
        map.put("meshStage", EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT);
        map.put("pipelineCacheSize", this.pipelineCache.size());
        map.put("pipelineCompiles", this.pipelineCompiles.sum());
        map.put("meshTaskDispatches", this.meshTaskDispatches.sum());
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
            textureBindings.get(0)
                    .binding(0)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            textureBindings.get(1)
                    .binding(1)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            textureBindings.get(2)
                    .binding(2)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            textureBindings.get(3)
                    .binding(3)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(EXTDescriptorBuffer.VK_DESCRIPTOR_SET_LAYOUT_CREATE_DESCRIPTOR_BUFFER_BIT_EXT)
                    .pBindings(textureBindings);
            LongBuffer descriptorSetLayoutHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(VK12.vkCreateDescriptorSetLayout(device.vkDevice(), descriptorSetLayoutCreateInfo, null, descriptorSetLayoutHandle), "Failed to create VIM terrain texture descriptor-buffer layout for " + pipelineInfo.getLocation());
            long textureDescriptorSetLayout = descriptorSetLayoutHandle.get(0);
            DescriptorHeapTerrainResources.TextureDescriptorLayout textureDescriptorLayout = queryTextureDescriptorLayout(device, textureDescriptorSetLayout);
            
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(EXTMeshShader.VK_SHADER_STAGE_TASK_BIT_EXT | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT)
                    .offset(0)
                    .size(TERRAIN_PUSH_CONSTANT_BYTES);
            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(textureDescriptorSetLayout))
                    .pPushConstantRanges(pushConstantRanges);
            LongBuffer layoutHandle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(VK12.vkCreatePipelineLayout(device.vkDevice(), layoutCreateInfo, null, layoutHandle), "Failed to create VIM terrain mesh pipeline layout for " + pipelineInfo.getLocation());
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
            VulkanUtils.crashIfFailure(VK12.vkCreateGraphicsPipelines(device.vkDevice(), 0L, pipelineCreateInfo, null, pipelineHandle), "Failed to create VIM terrain mesh pipeline for " + pipelineInfo.getLocation());
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
        return new DescriptorHeapTerrainResources.TextureDescriptorLayout(
                layoutSize[0],
                blockAtlasImageOffset[0],
                blockAtlasSamplerOffset[0],
                lightmapImageOffset[0],
                lightmapSamplerOffset[0],
                Math.max(terrainLayout.descriptorBufferSampledImageDescriptorBytes(), 1L),
                Math.max(terrainLayout.descriptorBufferSamplerDescriptorBytes(), 1L)
        );
    }
    
    private record MeshPipeline(long handle, long layout, long textureDescriptorSetLayout, DescriptorHeapTerrainResources.TextureDescriptorLayout textureDescriptorLayout) {
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
        
        private Compiler() {
            Shaderc.shaderc_compile_options_set_target_env(this.options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_4);
            Shaderc.shaderc_compile_options_set_target_spirv(this.options, Shaderc.shaderc_spirv_version_1_6);
            Shaderc.shaderc_compile_options_set_optimization_level(this.options, Shaderc.shaderc_optimization_level_zero);
            Shaderc.shaderc_compile_options_set_generate_debug_info(this.options);
        }
        
        private long compile(VulkanDevice device, String filename, int shaderKind, String source) {
            long result = Shaderc.shaderc_compile_into_spv(this.compiler, source, shaderKind, filename, "main", this.options);
            try {
                int status = Shaderc.shaderc_result_get_compilation_status(result);
                if (status != Shaderc.shaderc_compilation_status_success) {
                    throw new IllegalStateException("Failed to compile " + filename + ": " + Shaderc.shaderc_result_get_error_message(result));
                }
                ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    VkShaderModuleCreateInfo moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
                    moduleCreateInfo.pCode(spirv);
                    long[] module = new long[1];
                    VulkanUtils.crashIfFailure(VK12.vkCreateShaderModule(device.vkDevice(), moduleCreateInfo, null, module), "Failed to create " + filename);
                    return module[0];
                }
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
