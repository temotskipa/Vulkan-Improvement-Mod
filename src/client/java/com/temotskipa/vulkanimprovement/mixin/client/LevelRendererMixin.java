package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {
    @Inject(method = "prepareChunkRenders", at = @At("HEAD"))
    private void vim$beginChunkVisibilityFrame(Matrix4fc modelViewMatrix, CallbackInfoReturnable<?> cir) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            SectionMeshletStore.clearSectionVisibilityFrame();
        }
    }

    @Inject(method = "invalidateCompiledGeometry", at = @At("HEAD"))
    private void vim$clearMeshletCacheForGeometryInvalidation(ClientLevel level, Options options, Camera camera, BlockColors blockColors, CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            SectionMeshletStore.clearAll("invalidateCompiledGeometry");
        }
    }

    @Inject(method = "resetLevelRenderData", at = @At("HEAD"))
    private void vim$clearMeshletCacheForLevelReset(CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            SectionMeshletStore.clearAll("resetLevelRenderData");
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void vim$clearMeshletCacheForRendererClose(CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            SectionMeshletStore.clearAll("close");
        }
    }
}
