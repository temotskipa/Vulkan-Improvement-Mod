package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline.CreateInfo;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline.CreateInfo.Uniform;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.mojang.renderpearl.util.ShaderCompileException;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.VulkanObjectNames;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.Buffer;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The mesh pipelines that stand in for Minecraft's entity pipelines in GPU model draws
 * ({@link ModelMeshShader}). While Minecraft compiles a pipeline that draws the entity format in
 * quads, its vertex shader is compiled a second time, by Minecraft's compiler, as the mesh stage.
 * When Minecraft builds the Vulkan pipeline, its descriptors are made visible to the mesh stage and
 * its push constant range grows by the mesh stage's constants; the mesh pipeline is built with that
 * layout, Minecraft's fragment module and fixed-function state, for the same render pass, so the
 * descriptors Minecraft binds for a draw serve the mesh pipeline too. It is destroyed with
 * Minecraft's.
 *
 * <p>Pipelines get mesh pipelines only while {@code vim.gpuModelVertices} and
 * {@code vim.gpuModelMeshShaders} are both on when Minecraft builds them; Minecraft's pipelines are
 * untouched otherwise. Turning the flags on later takes a resource reload to rebuild them.
 */
public final class ModelMeshPipelines {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Mesh stages compiled for pipelines Minecraft is about to build, by pipeline name.
     */
    private static final Map<String, PendingModelMeshStage> PENDING = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> COMPILING_MESH_STAGE = ThreadLocal.withInitial(() -> false);
    private static final LongAdder BUILT = new LongAdder();
    private static final LongAdder UNSUPPORTED = new LongAdder();
    private static final LongAdder FAILED = new LongAdder();
    
    private ModelMeshPipelines() {
    }
    
    /**
     * Whether Minecraft's compiler is compiling a mesh stage now, on this thread.
     */
    public static boolean compilingMeshStage() {
        return COMPILING_MESH_STAGE.get();
    }
    
    /**
     * Whether a pipeline gets a mesh pipeline: GPU model draws are on and drawn by mesh shaders, the
     * device has them, and the pipeline draws Minecraft's entity format in quads.
     */
    public static boolean wanted(RenderPipeline pipeline) {
        return TerrainRendererDebugConfig.gpuModelVerticesEnabled() && TerrainRendererDebugConfig.gpuModelMeshShadersEnabled() && ModelGpuVertices.get().meshShadersAvailable() && pipeline.getPrimitiveTopology() == PrimitiveTopology.QUADS && readsEntityFormatOnly(pipeline.getVertexFormatBindings());
    }
    
    /**
     * Whether a pipeline's vertex buffers are Minecraft's entity format in the first slot and
     * nothing in the others, which Minecraft lists as empty.
     */
    static boolean readsEntityFormatOnly(List<@Nullable VertexFormat> bindings) {
        if (bindings.isEmpty() || bindings.getFirst() != DefaultVertexFormat.ENTITY) {
            return false;
        }
        for (int slot = 1; slot < bindings.size(); slot++) {
            if (bindings.get(slot) != null) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Compiles the mesh stage of a pipeline's vertex shader with Minecraft's compiler, which
     * {@code compiler} calls with the mesh stage's source, for when Minecraft builds the pipeline.
     */
    public static void compileMeshStage(RenderPipeline pipeline, String vertexSource, Function<String, ByteBuffer> compiler) {
        String name = pipeline.getLocation().toString();
        discard(name);
        int pushConstantOffset = Math.ceilDiv(pipeline.pushConstantSize(), 16) * 16;
        String meshSource = ModelMeshShader.generate(vertexSource, pushConstantOffset);
        if (meshSource == null) {
            UNSUPPORTED.increment();
            LOGGER.info("[Vulkan Improvement] No mesh stage for {}: its vertex shader is not one the mesh stage takes", name);
            return;
        }
        COMPILING_MESH_STAGE.set(true);
        try {
            PENDING.put(name, new PendingModelMeshStage(compiler.apply(meshSource), pushConstantOffset));
        } catch (Exception e) {
            FAILED.increment();
            LOGGER.warn("[Vulkan Improvement] Couldn't compile the mesh stage for {}", name, e);
        } finally {
            COMPILING_MESH_STAGE.set(false);
        }
    }
    
    /**
     * The mesh stage compiled for a pipeline Minecraft is building, if any.
     */
    public static @Nullable PendingModelMeshStage pending(String pipelineName) {
        return PENDING.get(pipelineName);
    }
    
    private static void discard(String pipelineName) {
        PendingModelMeshStage pending = PENDING.remove(pipelineName);
        if (pending != null) {
            MemoryUtil.memFree(pending.spirv());
        }
    }
    
    /**
     * Builds the mesh pipeline for a Vulkan pipeline Minecraft just built, if a mesh stage was
     * compiled for it, and keeps it on Minecraft's pipeline.
     *
     * @param pipeline       Minecraft's Vulkan pipeline
     * @param pipelineLayout its layout, which the mesh pipeline shares
     * @param blend          applies a blend function to a color attachment as Minecraft does
     */
    public static void build(VulkanDevice device, CreateInfo createInfo, BackendRenderPipeline pipeline, long pipelineLayout, BiConsumer<Buffer, BlendFunction> blend) {
        PendingModelMeshStage pending = PENDING.remove(createInfo.name());
        if (pending == null) {
            return;
        }
        try (SpvModule module = RenderSystem.getDevice().createSpvModule(createInfo.name() + ModelMeshShader.MODULE_SUFFIX, pending.spirv(), ShaderType.VERTEX, "main")) {
            ModelMeshPipelineHolder holder = (ModelMeshPipelineHolder) pipeline;
            bindAsMinecraft(module, createInfo.uniforms());
            long shaderModule = createShaderModule(device, module.spv(), createInfo.name());
            try {
                long meshPipeline = createPipeline(device, createInfo, pipelineLayout, holder, shaderModule, blend);
                holder.vim$setMeshPipeline(new ModelMeshPipeline(meshPipeline, shaderModule, pending.pushConstantOffset()));
                BUILT.increment();
            } catch (RuntimeException e) {
                VK10.vkDestroyShaderModule(device.vkDevice(), shaderModule, null);
                throw e;
            }
        } catch (Exception e) {
            FAILED.increment();
            LOGGER.warn("[Vulkan Improvement] Couldn't build the mesh pipeline for {}", createInfo.name(), e);
        }
    }
    
    /**
     * Gives the mesh stage's descriptors the bindings Minecraft gave the same uniforms in the
     * pipeline's own stages.
     */
    private static void bindAsMinecraft(SpvModule module, List<Uniform> uniforms) throws ShaderCompileException {
        Map<String, CompiledRenderPipeline.CreateInfo.Uniform> uniformsByName = new HashMap<>();
        for (CompiledRenderPipeline.CreateInfo.Uniform uniform : uniforms) {
            uniformsByName.put(uniform.name(), uniform);
        }
        for (SpvModule.Reflection.Descriptor descriptor : module.reflect().descriptors()) {
            CompiledRenderPipeline.CreateInfo.Uniform uniform = uniformsByName.get(descriptor.name());
            if (uniform == null) {
                throw new ShaderCompileException("The mesh stage uses a uniform the pipeline does not have: " + descriptor.name());
            }
            descriptor.binding(uniform.binding());
            descriptor.descriptorSetIndex(0);
        }
    }
    
    private static long createShaderModule(VulkanDevice device, ByteBuffer spirv, String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(spirv);
            LongBuffer handle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK10.vkCreateShaderModule(device.vkDevice(), createInfo, null, handle), "Failed to create the VIM model mesh module for " + name);
            VulkanObjectNames.name(device, VK10.VK_OBJECT_TYPE_SHADER_MODULE, handle.get(0), "VIM Model Mesh " + name);
            return handle.get(0);
        }
    }
    
    /**
     * The mesh pipeline: the mesh stage and Minecraft's fragment stage, with the rasterization,
     * depth and blend state Minecraft built its pipeline with, its layout and its render pass.
     */
    private static long createPipeline(VulkanDevice device, CompiledRenderPipeline.CreateInfo createInfo, long pipelineLayout, ModelMeshPipelineHolder pipeline, long meshModule, BiConsumer<VkPipelineColorBlendAttachmentState.Buffer, BlendFunction> blend) {
        int fragmentStage = -1;
        for (int i = 0; i < createInfo.shaders().size(); i++) {
            if (createInfo.shaders().get(i).type() == ShaderType.FRAGMENT) {
                fragmentStage = i;
            }
        }
        if (fragmentStage < 0) {
            throw new IllegalStateException("Pipeline " + createInfo.name() + " has no fragment stage");
        }
        long fragmentModule = pipeline.vim$shaderModule(fragmentStage);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer entryPoint = stack.UTF8("main");
            VkPipelineShaderStageCreateInfo.Buffer stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.apply(0, stage -> stage.sType$Default().stage(EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT).module(meshModule).pName(entryPoint));
            stages.apply(1, stage -> stage.sType$Default().stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule).pName(entryPoint));
            
            VkPipelineRasterizationStateCreateInfo rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default().polygonMode(VulkanConst.toVk(createInfo.polygonMode())).cullMode(createInfo.cull() ? VK10.VK_CULL_MODE_BACK_BIT : VK10.VK_CULL_MODE_NONE).frontFace(VK10.VK_FRONT_FACE_CLOCKWISE).lineWidth(1.0F);
            VkPipelineDepthStencilStateCreateInfo depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default();
            DepthStencilState depthStencil = createInfo.depthStencilState();
            if (depthStencil != null) {
                rasterizationState.depthBiasEnable(depthStencil.depthBiasConstant() != 0.0F || depthStencil.depthBiasScaleFactor() != 0.0F).depthBiasConstantFactor(depthStencil.depthBiasConstant()).depthBiasSlopeFactor(depthStencil.depthBiasScaleFactor());
                depthStencilState.depthTestEnable(true).depthWriteEnable(depthStencil.writeDepth()).depthCompareOp(VulkanConst.toVk(depthStencil.depthTest()));
            }
            
            List<@Nullable ColorTargetState> colorTargets = createInfo.colorTargetStates();
            VkPipelineColorBlendAttachmentState.Buffer attachments = VkPipelineColorBlendAttachmentState.calloc(colorTargets.size(), stack);
            for (ColorTargetState colorTarget : colorTargets) {
                attachments.colorWriteMask(colorTarget == null ? 0 : VulkanConst.toVk(colorTarget));
                if (colorTarget != null) {
                    colorTarget.blendFunction().ifPresent(blendFunction -> blend.accept(attachments, blendFunction));
                }
                attachments.position(attachments.position() + 1);
            }
            attachments.position(0);
            VkPipelineColorBlendStateCreateInfo colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default().pAttachments(attachments);
            
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default().viewportCount(1).scissorCount(1);
            VkPipelineMultisampleStateCreateInfo multisampleState = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default().rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT).sampleShadingEnable(false);
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default().pDynamicStates(stack.ints(VK10.VK_DYNAMIC_STATE_VIEWPORT, VK10.VK_DYNAMIC_STATE_SCISSOR));
            long renderPass = device.renderpassCache().getRenderPass(depthStencil != null ? VulkanConst.toVk(createInfo.depthStencilFormat()) : 0, colorTargets);
            
            VkGraphicsPipelineCreateInfo.Buffer pipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack).sType$Default().pStages(stages).pRasterizationState(rasterizationState).pDepthStencilState(depthStencilState).pColorBlendState(colorBlendState).pViewportState(viewportState).pMultisampleState(multisampleState).pDynamicState(dynamicState).layout(pipelineLayout).renderPass(renderPass).subpass(0);
            LongBuffer handle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK10.vkCreateGraphicsPipelines(device.vkDevice(), VK10.VK_NULL_HANDLE, pipelineCreateInfo, null, handle), "Failed to create the VIM model mesh pipeline for " + createInfo.name());
            VulkanObjectNames.name(device, VK10.VK_OBJECT_TYPE_PIPELINE, handle.get(0), "VIM Model Mesh " + createInfo.name());
            return handle.get(0);
        }
    }
    
    /**
     * The mesh pipeline kept on a Minecraft pipeline, if any.
     */
    public static @Nullable ModelMeshPipeline of(@Nullable BackendRenderPipeline pipeline) {
        return pipeline instanceof ModelMeshPipelineHolder holder ? holder.vim$meshPipeline() : null;
    }
    
    /**
     * Whether every pipeline a render type may draw with has a mesh pipeline: its own, and those of
     * its order-independent transparency stages.
     */
    static boolean drawsWithMesh(PreparedRenderType renderType) {
        if (!hasMeshPipeline(renderType.pipeline())) {
            return false;
        }
        OitPipelineSet oit = renderType.oitPipelineSet();
        return oit == null || hasMeshPipeline(oit.depthBoundsPipeline()) && hasMeshPipeline(oit.transmittancePipeline()) && hasMeshPipeline(oit.accumulatePipeline());
    }
    
    private static boolean hasMeshPipeline(RenderPipeline pipeline) {
        return RenderSystem.getCompiledPipelineNullable(pipeline) instanceof FrontendRenderPipeline compiled && of(compiled.backendRenderPipeline()) != null;
    }
    
    public static Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("built", BUILT.sum());
        map.put("unsupported", UNSUPPORTED.sum());
        map.put("failed", FAILED.sum());
        return map;
    }
}