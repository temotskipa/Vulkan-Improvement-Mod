package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainPerformanceMonitor;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    
    /**
     * Minecraft collects the section layers of a frame here for both of its terrain draw paths.
     */
    @Inject(method = "extractSectionDrawGroups", at = @At("HEAD"))
    private void vim$beginVisibleFrame(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
        if (VulkanImprovementRuntime.isVulkanBackendActive() && TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            SectionMeshletStore.beginVisibleFrame(MeshTerrainRenderer.get().takesOverSectionLayers(this.levelRenderState.renderWireframeTerrain));
        }
    }

    /**
     * In strict replacement a section layer the mesh path draws is never looked up by Minecraft,
     * and so gets no draw of its own.
     */
    @WrapOperation(method = "extractSectionDrawGroups", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;getRenderSectionSlice(Lnet/minecraft/client/renderer/chunk/SectionMesh;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSectionBufferSlice;"))
    private SectionRenderDispatcher.RenderSectionBufferSlice vim$takeSectionLayer(SectionRenderDispatcher dispatcher, SectionMesh sectionMesh, ChunkSectionLayer layer, Operation<SectionRenderDispatcher.RenderSectionBufferSlice> original, @Local SectionRenderDispatcher.RenderSection section, @Local(ordinal = 0) long fadeDuration, @Local(ordinal = 1) long now) {
        if (SectionMeshletStore.takeSectionLayer(sectionMesh, layer, section, now, fadeDuration)) {
            return null;
        }
        return original.call(dispatcher, sectionMesh, layer);
    }
    
    /**
     * The frame's sections are known and no render pass is open yet: the mesh path prepares here.
     */
    @Inject(method = "extractSectionDrawGroups", at = @At("RETURN"))
    private void vim$endVisibleFrame(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
        SectionMeshletStore.endVisibleFrame();
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            TerrainPerformanceMonitor.get().beginFrame();
            MeshTerrainRenderer.get().prepareVisibleFrame(this.levelRenderState.renderWireframeTerrain);
            TerrainPerformanceMonitor.get().endPrepare();
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