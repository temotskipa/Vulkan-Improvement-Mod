package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline.CreateInfo;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.model.*;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.EXTMeshShader;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Builds the GPU model mesh pipeline beside a Minecraft pipeline that has a mesh stage, and destroys
 * it with Minecraft's ({@link ModelMeshPipelines}). Such a pipeline's descriptors are made visible
 * to the mesh stage and its push constant range grows by the mesh stage's constants, so the mesh
 * pipeline shares its layout.
 */
@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineMeshMixin implements ModelMeshPipelineHolder {
    @Shadow
    @Final
    private VulkanDevice device;
    
    @Shadow
    @Final
    private LongList shaderModules;
    
    @Unique
    private @Nullable ModelMeshPipeline vim$meshPipeline;
    
    @Shadow
    private static void applyBlendInformation(VkPipelineColorBlendAttachmentState.Buffer blendAttachments, BlendFunction blendFunction) {
        throw new AssertionError();
    }
    
    /**
     * Shows the mesh stage what Minecraft shows the vertex stage it stands in for. Minecraft sets a
     * binding's stages twice, the fragment stage and then the vertex stage too unless the binding is
     * an input attachment, which only the fragment stage may read.
     */
    @ModifyArg(method = "compile", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;stageFlags(I)Lorg/lwjgl/vulkan/VkDescriptorSetLayoutBinding;"))
    private static int vim$showDescriptorsToMeshStage(int stageFlags, @Local(argsOnly = true, name = "pipelineCreateInfo") CreateInfo pipelineCreateInfo) {
        if ((stageFlags & VK10.VK_SHADER_STAGE_VERTEX_BIT) == 0 || ModelMeshPipelines.pending(pipelineCreateInfo) == null) {
            return stageFlags;
        }
        return stageFlags | EXTMeshShader.VK_SHADER_STAGE_MESH_BIT_EXT;
    }
    
    @ModifyExpressionValue(method = "compile", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline$CreateInfo;pushConstantsSize()I"))
    private static int vim$makeRoomForMeshConstants(int pushConstantsSize, @Local(argsOnly = true, name = "pipelineCreateInfo") CreateInfo pipelineCreateInfo) {
        PendingModelMeshStage pending = ModelMeshPipelines.pending(pipelineCreateInfo);
        return pending == null ? pushConstantsSize : pending.pushConstantOffset() + ModelMeshShader.PUSH_CONSTANT_BYTES;
    }
    
    @Inject(method = "compile", at = @At("RETURN"))
    private static void vim$buildMeshPipeline(VulkanDevice device, CreateInfo pipelineCreateInfo, CallbackInfoReturnable<VulkanRenderPipeline> cir) {
        VulkanRenderPipeline pipeline = cir.getReturnValue();
        ModelMeshPipelines.build(device, pipelineCreateInfo, pipeline, pipeline.pipelineLayout(), VulkanRenderPipelineMeshMixin::applyBlendInformation);
    }
    
    @Inject(method = "destroy", at = @At("HEAD"))
    private void vim$destroyMeshPipeline(CallbackInfo ci) {
        if (this.vim$meshPipeline != null) {
            this.vim$meshPipeline.destroy(this.device);
            this.vim$meshPipeline = null;
        }
    }
    
    @Override
    public @Nullable ModelMeshPipeline vim$meshPipeline() {
        return this.vim$meshPipeline;
    }
    
    @Override
    public void vim$setMeshPipeline(ModelMeshPipeline meshPipeline) {
        this.vim$meshPipeline = meshPipeline;
    }
    
    @Override
    public long vim$shaderModule(int stage) {
        return this.shaderModules.getLong(stage);
    }
}