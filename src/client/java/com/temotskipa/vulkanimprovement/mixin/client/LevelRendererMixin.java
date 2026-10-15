package com.temotskipa.vulkanimprovement.mixin.client;

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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(LevelRenderer.class)
public final class LevelRendererMixin {
    @Shadow
    @Final
    private LevelRenderState levelRenderState;
    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow
    @Final
    private OptionsRenderState optionsRenderState;
    // The visible sections Minecraft collects in a GPU-driven frame, in its order.
    @Unique
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> vim$collectedSections = new ObjectArrayList<>();
    @Unique
    private boolean vim$gpuDrivenFrame;
    
    /**
     * Minecraft collects the section layers of a frame here for both of its terrain draw paths. The
     * mesh path first decides how it takes the frame's terrain, and in a GPU-driven frame has the
     * GPU write the draws of every visible section's opaque terrain.
     */
    @Inject(method = "extractSectionDrawGroups", at = @At("HEAD"))
    private void vim$beginVisibleFrame(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
        boolean gpuDriven = false;
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            TerrainPerformanceMonitor.get().beginFrame();
            if (TerrainRendererDebugConfig.terrainCaptureEnabled()) {
                gpuDriven = MeshTerrainRenderer.get().beginVisibleFrame(this.levelRenderState.renderWireframeTerrain, Util.toMillis(this.optionsRenderState.chunkSectionFadeInTime), this.visibleSections, this.vim$collectedSections);
            }
        }
        this.vim$gpuDrivenFrame = gpuDriven;
    }
    
    /**
     * In a GPU-driven frame Minecraft walks only the visible sections that draw something the GPU
     * does not, and leaves out the rest, which the GPU draws whole.
     */
    @Redirect(method = "extractSectionDrawGroups", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"))
    private ObjectListIterator<SectionRenderDispatcher.RenderSection> vim$collectSections(ObjectArrayList<SectionRenderDispatcher.RenderSection> sections) {
        return this.vim$gpuDrivenFrame ? this.vim$collectedSections.iterator() : sections.iterator();
    }
    
    /**
     * Records the section layers Minecraft collects for a frame. In strict replacement a section
     * layer the mesh path draws is never looked up by Minecraft, and so gets no draw of its own.
     * Nor is a layer the section has nothing in: Minecraft ignores its slice, and the lookup costs
     * a hash probe or two for every section and layer. This runs for every layer of every visible
     * section, so it calls Minecraft's lookup directly and does nothing else while no frame is
     * being collected.
     */
    @Redirect(method = "extractSectionDrawGroups", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;getRenderSectionSlice(Lnet/minecraft/client/renderer/chunk/SectionMesh;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSectionBufferSlice;"))
    private SectionRenderDispatcher.RenderSectionBufferSlice vim$takeSectionLayer(SectionRenderDispatcher dispatcher, SectionMesh sectionMesh, ChunkSectionLayer layer, @Local SectionMesh.SectionDraw draw, @Local SectionRenderDispatcher.RenderSection section, @Local(ordinal = 0) long fadeDuration, @Local(ordinal = 1) long now) {
        // A frame is collected only while the Vulkan backend is active and terrain capture is on.
        if (!SectionMeshletStore.collectingFrame()) {
            return dispatcher.getRenderSectionSlice(sectionMesh, layer);
        }
        if (draw == null && SectionMeshletStore.collectingTakeoverFrame() || SectionMeshletStore.takeSectionLayer(sectionMesh, layer, section, now, fadeDuration)) {
            return null;
        }
        SectionRenderDispatcher.RenderSectionBufferSlice slice = dispatcher.getRenderSectionSlice(sectionMesh, layer);
        SectionMeshletStore.recordDrawSlice(sectionMesh, layer, slice);
        return slice;
    }
    
    /**
     * The frame's sections are known and no render pass is open yet: the mesh path prepares here.
     */
    @Inject(method = "extractSectionDrawGroups", at = @At("RETURN"))
    private void vim$endVisibleFrame(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
        SectionMeshletStore.endVisibleFrame();
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            MeshTerrainRenderer.get().prepareVisibleFrame(this.levelRenderState.renderWireframeTerrain);
            TerrainPerformanceMonitor.get().endPrepare();
        }
        if (this.vim$gpuDrivenFrame) {
            this.vim$gpuDrivenFrame = false;
            this.vim$collectedSections.clear();
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