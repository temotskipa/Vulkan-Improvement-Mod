package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.SectionMeshletStore;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherMixin {
    @Inject(method = "getRenderSectionSlice", at = @At("RETURN"))
    private void vim$recordTerrainDrawSlice(SectionMesh sectionMesh, ChunkSectionLayer layer, CallbackInfoReturnable<SectionRenderDispatcher.RenderSectionBufferSlice> cir) {
        SectionRenderDispatcher.RenderSectionBufferSlice slice = cir.getReturnValue();
        if (slice != null) {
            SectionMeshletStore.recordDrawSlice(sectionMesh, layer, slice);
        }
    }
}
