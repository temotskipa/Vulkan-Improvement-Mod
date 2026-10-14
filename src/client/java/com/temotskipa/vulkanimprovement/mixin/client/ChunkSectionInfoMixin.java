package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import net.minecraft.client.renderer.DynamicGpuData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicGpuData.ChunkSectionInfo.class)
public final class ChunkSectionInfoMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void vim$recordChunkVisibility(int x, int y, int z, float visibility, CallbackInfo ci) {
        // Minecraft makes one for every section it draws, so this does nothing unless a frame is
        // collected, which happens only while the Vulkan backend is active and terrain capture is on.
        if (SectionMeshletStore.collectingFrame()) {
            SectionMeshletStore.recordSectionVisibility(x, y, z, visibility);
        }
    }
}
