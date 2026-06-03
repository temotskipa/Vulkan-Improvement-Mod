package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.textures.GpuSampler;
import com.temotskipa.vulkanimprovement.client.vulkan.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.TerrainRenderContext;
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
        TerrainRenderContext.enter();
        MeshTerrainRenderer.get().observeTerrainGroup((ChunkSectionsToRender) (Object) this, group);
    }

    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void vim$leaveTerrainGroup(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        MeshTerrainRenderer.get().finalizeTerrainGroupObservation();
        TerrainRenderContext.exit();
    }
}