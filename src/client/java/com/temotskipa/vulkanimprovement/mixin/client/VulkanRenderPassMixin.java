package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.DescriptorHeapTerrainResources;
import com.temotskipa.vulkanimprovement.client.vulkan.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.TerrainDrawContext;
import com.temotskipa.vulkanimprovement.client.vulkan.TerrainRenderContext;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    private VkCommandBuffer secondaryCommandBuffer() {
        throw new AssertionError();
    }

    @Inject(method = "setPipeline", at = @At("TAIL"))
    private void vim$applyTerrainFragmentShadingRate(RenderPipeline pipeline, CallbackInfo ci) {
        if (TerrainRenderContext.isTerrainPass()) {
            TerrainRenderContext.setLayerForPipeline(pipeline);
        }
    }

    @Inject(method = "bindTexture", at = @At("TAIL"))
    private void vim$captureTerrainTextureBinding(String name, GpuTextureView view, GpuSampler sampler, CallbackInfo ci) {
        if (TerrainRenderContext.isTerrainPass()) {
            DescriptorHeapTerrainResources.get().recordTextureBinding(name, view, sampler);
        }
    }

    @Inject(method = "drawMultipleIndexed", at = @At("HEAD"), cancellable = true)
    private <T> void vim$drawMeshTerrain(Collection<RenderPass.Draw<T>> draws, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType, Collection<String> dynamicUniforms, T uniformArgument, CallbackInfo ci) {
        TerrainDrawContext context = TerrainDrawContext.current(this.secondaryCommandBuffer(), this.pipeline, this.hasDepth, draws);
        if (MeshTerrainRenderer.get().tryDrawMeshTerrain(context)) {
            ci.cancel();
        }
    }
}
