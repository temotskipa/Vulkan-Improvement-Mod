package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.DescriptorHeapTerrainResources;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainDrawContext;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRenderContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(VulkanRenderPass.class)
public class VulkanRenderPassMixin {
    @Shadow
    @Nullable
    protected VulkanRenderPipeline pipeline;
    @Shadow
    @Final
    private boolean hasDepth;
    @Shadow
    private boolean anyDescriptorDirty;
    
    @Shadow
    private VkCommandBuffer commandBuffer() {
        throw new AssertionError();
    }
    
    @Inject(method = "setPipeline", at = @At("TAIL"))
    private void vim$applyTerrainFragmentShadingRate(RenderPipeline pipeline, CallbackInfo ci) {
        if (TerrainRenderContext.isTerrainPass()) {
            TerrainRenderContext.setLayerForPipeline(pipeline);
        }
    }
    
    @Inject(method = "bindTexture", at = @At("TAIL"))
    private void vim$captureTerrainTextureBinding(String name, GpuTextureView textureView, GpuSampler sampler, CallbackInfo ci) {
        if (TerrainRenderContext.isTerrainPass()) {
            DescriptorHeapTerrainResources.get().recordTextureBinding(name, textureView, sampler);
        }
    }
    
    @Inject(method = "drawMultipleIndexed", at = @At("HEAD"), cancellable = true)
    private <T> void vim$drawMeshTerrain(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument, CallbackInfo ci) {
        TerrainDrawContext context = TerrainDrawContext.current(this.commandBuffer(), this.pipeline, this.hasDepth, draws);
        if (MeshTerrainRenderer.get().tryDrawMeshTerrain(context)) {
            vim$restoreVanillaPipelineState();
            ci.cancel();
        }
    }
    
    @Unique
    private void vim$restoreVanillaPipelineState() {
        if (this.pipeline == null || !this.pipeline.isValid()) {
            return;
        }
        long pipelineHandle = this.hasDepth ? this.pipeline.withDepthPipeline() : this.pipeline.withoutDepthPipeline();
        if (pipelineHandle == 0L) {
            return;
        }
        VK12.vkCmdBindPipeline(this.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineHandle);
        this.anyDescriptorDirty = true;
    }
}