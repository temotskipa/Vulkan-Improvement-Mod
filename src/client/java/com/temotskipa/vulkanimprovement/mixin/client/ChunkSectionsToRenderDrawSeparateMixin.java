package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender.DrawSeparate;
import net.minecraft.client.renderer.oit.OitStage;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft's terrain path that issues one draw per section layer.
 * The mesh path draws the layer once Minecraft has bound the layer's pipeline, and Minecraft's own
 * draws for the layer are skipped only when the mesh path says it covered them.
 */
@Mixin(DrawSeparate.class)
public abstract class ChunkSectionsToRenderDrawSeparateMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V", shift = At.Shift.AFTER), cancellable = true)
    private void vim$drawMeshTerrainLayer(ChunkSectionLayer layer, RenderPass renderPass, @Nullable GpuBuffer defaultIndexBuffer, @Nullable IndexType defaultIndexType, boolean improvedFog, boolean wireframe, @Nullable OitStage oitStage, CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive() && MeshTerrainRenderer.get().drawTerrainLayer(layer, renderPass)) {
            ci.cancel();
        }
    }
}