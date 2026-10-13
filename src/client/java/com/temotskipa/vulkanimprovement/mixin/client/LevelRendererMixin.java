package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {
    /**
     * Minecraft collects the section layers of a frame here for both of its terrain draw paths.
     */
    @Inject(method = "extractSectionDrawGroups", at = @At("HEAD"))
    private void vim$beginVisibleFrame(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
        if (VulkanImprovementRuntime.isVulkanBackendActive() && TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            SectionMeshletStore.beginVisibleFrame();
        }
    }

    /**
     * The frame's sections are known and no render pass is open yet: the mesh path prepares here.
     */
    @Inject(method = "extractSectionDrawGroups", at = @At("RETURN"))
    private void vim$endVisibleFrame(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
        SectionMeshletStore.endVisibleFrame();
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            MeshTerrainRenderer.get().prepareVisibleFrame();
        }
    }

    @Inject(method = "invalidateCompiledGeometry", at = @At("HEAD"))
    private void vim$clearMeshletCacheForGeometryInvalidation(ClientLevel level, Options options, Camera camera, BlockColors blockColors, CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive() && TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            SectionMeshletStore.clearAll("invalidateCompiledGeometry");
        }
    }

    @Inject(method = "resetLevelRenderData", at = @At("HEAD"))
    private void vim$clearMeshletCacheForLevelReset(CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive() && TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            SectionMeshletStore.clearAll("resetLevelRenderData");
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void vim$clearMeshletCacheForRendererClose(CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive() && TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            SectionMeshletStore.clearAll("close");
        }
    }
}
