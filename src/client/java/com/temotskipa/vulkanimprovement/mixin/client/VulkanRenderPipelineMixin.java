package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainPipelineState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Remembers the state a Vulkan pipeline was compiled with, which the pipeline itself no longer keeps.
 */
@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineMixin implements TerrainPipelineState.Holder {
    @Unique
    private @Nullable TerrainPipelineState vim$terrainPipelineState;

    @Inject(method = "compile", at = @At("RETURN"))
    private static void vim$rememberPipelineState(VulkanDevice device, CompiledRenderPipeline.CreateInfo pipelineCreateInfo, CallbackInfoReturnable<VulkanRenderPipeline> cir) {
        if ((Object) cir.getReturnValue() instanceof TerrainPipelineState.Holder holder) {
            holder.vim$rememberTerrainPipelineState(TerrainPipelineState.from(pipelineCreateInfo));
        }
    }

    @Override
    public @Nullable TerrainPipelineState vim$terrainPipelineState() {
        return this.vim$terrainPipelineState;
    }

    @Override
    public void vim$rememberTerrainPipelineState(TerrainPipelineState state) {
        this.vim$terrainPipelineState = state;
    }
}
