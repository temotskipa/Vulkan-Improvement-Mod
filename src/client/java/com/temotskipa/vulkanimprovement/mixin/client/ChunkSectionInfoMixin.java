package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import net.minecraft.client.renderer.DynamicUniforms;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicUniforms.ChunkSectionInfo.class)
public final class ChunkSectionInfoMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void vim$recordChunkVisibility(Matrix4fc modelView, int x, int y, int z, float visibility, int textureAtlasWidth, int textureAtlasHeight, CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            SectionMeshletStore.recordSectionVisibility(x, y, z, visibility);
        }
    }
}