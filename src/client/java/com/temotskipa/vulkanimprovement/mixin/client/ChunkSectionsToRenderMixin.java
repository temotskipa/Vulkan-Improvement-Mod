package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRenderContext;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSectionsToRender.class)
public final class ChunkSectionsToRenderMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"))
    private void vim$observeTerrainGroup(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (!VulkanImprovementRuntime.isVulkanBackendActive()) {
            return;
        }
        TerrainRenderContext.enter();
        MeshTerrainRenderer.get().observeTerrainGroup((ChunkSectionsToRender) (Object) this, group);
    }

    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void vim$leaveTerrainGroup(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (!TerrainRenderContext.isTerrainPass()) {
            return;
        }
        MeshTerrainRenderer.get().finalizeTerrainGroupObservation();
        TerrainRenderContext.exit();
    }
}
