package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.presentation.PresentPacingController;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererCoreServices;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererDomainObserver;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererDomainRegistry;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererLifecycleState;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class MeshTerrainRenderer {
    private static final MeshTerrainRenderer INSTANCE = new MeshTerrainRenderer();

    private final LongAdder terrainGroupCalls = new LongAdder();
    private final LongAdder improvedFogTerrainGroups = new LongAdder();
    private final LongAdder capturedSections = new LongAdder();
    private final LongAdder releasedSections = new LongAdder();
    private final LongAdder capturedVertexBytes = new LongAdder();
    private final LongAdder capturedIndexBytes = new LongAdder();
    private final LongAdder captureCpuNanos = new LongAdder();
    private final LongAdder estimatedMeshlets = new LongAdder();
    private final LongAdder meshReplacementDrawCalls = new LongAdder();
    private final LongAdder meshReplacementTasks = new LongAdder();
    private final LongAdder meshReplacementVisibleListDrawCalls = new LongAdder();
    private final LongAdder meshReplacementWorkQueueDrawCalls = new LongAdder();
    private final LongAdder meshReplacementIndirectCommandDrawCalls = new LongAdder();
    private final LongAdder meshReplacementPreparedGpuCommandDrawCalls = new LongAdder();
    private final LongAdder meshReplacementPreparedGpuCommands = new LongAdder();
    private final LongAdder meshReplacementPreparedFullLayerGpuCommands = new LongAdder();
    private final LongAdder meshReplacementPreparedVisibleGpuCommands = new LongAdder();
    private final LongAdder meshReplacementPreparedGpuCommandDisabled = new LongAdder();
    private final LongAdder meshReplacementPreparedGpuCommandRefusals = new LongAdder();
    private final LongAdder meshReplacementPreparedDispatchQueueLeaks = new LongAdder();
    private final LongAdder meshReplacementVisibleMeshletFallbackRefusals = new LongAdder();
    private final LongAdder meshReplacementTaskTruncations = new LongAdder();
    private final LongAdder meshReplacementLayerFilteredDrawCalls = new LongAdder();
    private final LongAdder meshReplacementWholeSetDrawCalls = new LongAdder();
    private final LongAdder meshReplacementDuplicateCancellations = new LongAdder();
    private final LongAdder meshReplacementRefusals = new LongAdder();
    private final LongAdder meshReplacementCustomIndexFallbacks = new LongAdder();
    private final LongAdder meshReplacementIndexedDrawCalls = new LongAdder();
    private final LongAdder[] meshReplacementCustomIndexFallbacksByLayer = createLayerCounters();
    private final LongAdder[] meshReplacementIndexedDrawCallsByLayer = createLayerCounters();
    private final LongAdder[] meshReplacementDrawCallsByLayer = createLayerCounters();
    private volatile TerrainMeshTaskDispatch lastTerrainDispatch = TerrainMeshTaskDispatch.unavailable("not dispatched");

    private MeshTerrainRenderer() {
    }

    public static MeshTerrainRenderer get() {
        return INSTANCE;
    }

    private static LongAdder[] createLayerCounters() {
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        LongAdder[] counters = new LongAdder[layers.length];
        for (int i = 0; i < counters.length; i++) {
            counters[i] = new LongAdder();
        }
        return counters;
    }

    private static Map<String, Object> layerCounterMap(LongAdder[] counters) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            int ordinal = layer.ordinal();
            map.put(layer.label(), ordinal < counters.length ? counters[ordinal].sum() : 0L);
        }
        return map;
    }

    /**
     * Called when Minecraft starts drawing a group of terrain layers, before it sets a pipeline.
     * Wireframe terrain is left to Minecraft: its pipelines are not ones the mesh path stands in for.
     *
     * @param improvedFog whether the group fades into the sky and cloud images instead of the fog
     *                    colour, which Minecraft draws when improved transparency is on
     */
    public void observeTerrainGroup(ChunkSectionLayerGroup group, boolean wireframe, boolean improvedFog) {
        if (!TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        this.terrainGroupCalls.increment();
        if (improvedFog) {
            this.improvedFogTerrainGroups.increment();
        }
        TerrainRenderContext.setCurrentTerrainGroup(group);
        TerrainRenderContext.setReplacementAllowed(!wireframe);
        TerrainRenderContext.setImprovedFog(improvedFog);
    }

    /**
     * Called once Minecraft has collected the frame's section layers and before it opens a render
     * pass. Minecraft hands the terrain renderers a pass that is already open, and neither an
     * upload nor the compute step that writes mesh-task commands can be recorded inside one, so
     * everything the layers need from the GPU this frame is prepared here.
     *
     * @param wireframe whether Minecraft draws this frame's terrain as wireframe
     */
    public void prepareVisibleFrame(boolean wireframe) {
        if (!TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        DescriptorHeapTerrainResources.get().uploadDirtyTerrainData();
        int unconsumedPreparedDispatches = TerrainRenderContext.beginPreparedFrame();
        if (unconsumedPreparedDispatches > 0) {
            this.meshReplacementPreparedDispatchQueueLeaks.add(unconsumedPreparedDispatches);
        }
        // Wireframe terrain is Minecraft's to draw, so commands prepared for it would never be used.
        if (!wireframe) {
            prepareTerrainCommands();
        }
    }

    /**
     * Called for each layer once Minecraft has bound that layer's pipeline. Draws the layer's
     * meshlets and says whether Minecraft's own draws for the layer are to be skipped.
     */
    public boolean drawTerrainLayer(ChunkSectionLayer layer, Object renderPass) {
        if (!TerrainRenderContext.isTerrainPass() || !TerrainRendererDebugConfig.replaceVanillaTerrain()) {
            return false;
        }
        TerrainRenderContext.setCurrentLayer(layer);
        TerrainRenderPassAccess access = TerrainRenderPassAccess.of(renderPass);
        if (access == null) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        DrawResult drawResult = tryDrawMeshTerrain(TerrainDrawContext.forLayer(access, layer.ordinal()));
        if (drawResult.drewMeshTerrain()) {
            access.vim$restoreVanillaPipeline();
        }
        return drawResult.cancelVanillaDraw();
    }

    public void recordSectionCapture(int vertexBytes, int indexBytes, int meshlets, long cpuNanos) {
        this.capturedSections.increment();
        this.capturedVertexBytes.add(vertexBytes);
        this.capturedIndexBytes.add(indexBytes);
        this.captureCpuNanos.add(Math.max(cpuNanos, 0L));
        this.estimatedMeshlets.add(meshlets);
    }

    public void recordSectionRelease() {
        this.releasedSections.increment();
    }

    public DrawResult tryDrawMeshTerrain(TerrainDrawContext context) {
        if (!canAttemptMeshTerrain(context)) {
            return DrawResult.passThrough();
        }
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        if (context.useVisibleDrawList()) {
            return tryDrawVisibleMeshTerrain(context, terrainResources);
        }

        return tryDrawLayerMeshTerrain(context, terrainResources);
    }

    private DrawResult tryDrawLayerMeshTerrain(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources) {
        int totalMeshletCount = terrainResources.meshletsUploaded();

        int layerOrdinal = context.layerOrdinal();
        if (!TerrainLayerReplacementPolicy.canReplaceLayer(layerOrdinal)) {
            this.meshReplacementRefusals.increment();
            return DrawResult.passThrough();
        }
        int meshletOffset = layerOrdinal < 0 ? 0 : terrainResources.meshletOffsetForLayer(layerOrdinal);
        int meshletCount = layerOrdinal < 0 ? totalMeshletCount : terrainResources.meshletCountForLayer(layerOrdinal);
        if (meshletCount <= 0) {
            this.meshReplacementRefusals.increment();
            return DrawResult.passThrough();
        }
        if (!TerrainRenderContext.markDispatchRequired(layerOrdinal)) {
            this.meshReplacementDuplicateCancellations.increment();
            return DrawResult.passThrough();
        }

        TerrainMeshTaskDispatch dispatch = TerrainRenderContext.pollPreparedLayerDispatch(layerOrdinal);
        if (dispatch != null) {
            return drawPreparedLayerDispatches(context, dispatch, terrainResources);
        }
        if (!context.drawAllCapturedTerrainLayers()) {
            this.meshReplacementRefusals.increment();
            return DrawResult.passThrough();
        }
        return drawFullLayerSegments(context, terrainResources, meshletOffset, layerOrdinal, meshletCount);
    }

    private DrawResult tryDrawVisibleMeshTerrain(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources) {
        int layerOrdinal = context.layerOrdinal();
        if (!TerrainLayerReplacementPolicy.canReplaceLayer(layerOrdinal)) {
            this.meshReplacementRefusals.increment();
            return DrawResult.passThrough();
        }

        TerrainMeshTaskDispatch preparedDispatch = TerrainRenderContext.pollPreparedLayerDispatch(layerOrdinal);
        if (preparedDispatch != null) {
            return drawPreparedLayerDispatches(context, preparedDispatch, terrainResources);
        }

        List<SectionMeshletStore.MeshletRange> ranges = SectionMeshletStore.visibleMeshletRanges(layerOrdinal);
        if (ranges == null || ranges.isEmpty()) {
            this.meshReplacementRefusals.increment();
            return DrawResult.passThrough();
        }

        boolean drewAny = false;
        for (List<SectionMeshletStore.MeshletRange> batch : TerrainLayerReplacementPolicy.dispatchBatches(ranges)) {
            TerrainMeshTaskDispatch dispatch = buildVisibleMeshDispatch(terrainResources, batch, layerOrdinal);
            if (dispatch == null || !drawMeshTerrain(context, dispatch, terrainResources)) {
                this.meshReplacementRefusals.increment();
                return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();
            }
            drewAny = true;
        }
        return visibleDrawResult();
    }

    private boolean canAttemptMeshTerrain(TerrainDrawContext context) {
        if (!TerrainRendererDebugConfig.replaceVanillaTerrain() || !context.terrainPass() || !TerrainRenderContext.replacementAllowed()) {
            return false;
        }
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        if (!RendererCoreServices.get().currentLifecycleState().drawable() || context.vanillaPipeline() == null || !terrainResources.hasGpuTerrainData() || !MeshShaderTerrainProgram.get().ready() || terrainResources.meshletsUploaded() <= 0) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        return true;
    }

    private DrawResult drawPreparedLayerDispatches(TerrainDrawContext context, TerrainMeshTaskDispatch firstDispatch, DescriptorHeapTerrainResources terrainResources) {
        boolean drewAny = false;
        TerrainMeshTaskDispatch dispatch = firstDispatch;
        while (dispatch != null) {
            if (!dispatch.ready() || !drawMeshTerrain(context, dispatch, terrainResources)) {
                this.meshReplacementRefusals.increment();
                return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();
            }
            drewAny = true;
            dispatch = TerrainRenderContext.pollPreparedLayerDispatch(context.layerOrdinal());
        }
        return drewAny ? visibleDrawResult() : DrawResult.passThrough();
    }

    private DrawResult visibleDrawResult() {
        return TerrainRendererDebugConfig.strictMeshTerrainReplacement() ? DrawResult.replaced() : DrawResult.overlayOnly();
    }

    private DrawResult drawFullLayerSegments(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources, int meshletOffset, int layerOrdinal, int meshletCount) {
        int segments = TerrainLayerReplacementPolicy.dispatchCountForMeshlets(meshletCount);
        boolean drewAny = false;
        for (int segment = 0; segment < segments; segment++) {
            int segmentOffset = TerrainLayerReplacementPolicy.segmentMeshletOffset(meshletOffset, segment);
            int segmentCount = TerrainLayerReplacementPolicy.segmentMeshletCount(meshletCount, segment);
            TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(segmentOffset, layerOrdinal, segmentCount);
            if (!dispatch.ready()) {
                this.meshReplacementRefusals.increment();
                return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();
            }
            if (!drawMeshTerrain(context, dispatch, terrainResources)) {
                this.meshReplacementRefusals.increment();
                return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();
            }
            drewAny = true;
        }
        return drewAny ? DrawResult.overlayOnly() : DrawResult.passThrough();
    }

    private void prepareTerrainCommands() {
        if (!TerrainRendererDebugConfig.replaceVanillaTerrain()) {
            return;
        }
        if (!TerrainRendererDebugConfig.enableGpuGeneratedMeshTaskCommands()) {
            this.meshReplacementPreparedGpuCommandDisabled.increment();
            return;
        }

        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        if (!RendererCoreServices.get().currentLifecycleState().drawable() || !terrainResources.hasGpuTerrainData() || !MeshShaderTerrainProgram.get().ready() || terrainResources.meshletsUploaded() <= 0) {
            this.meshReplacementPreparedGpuCommandRefusals.increment();
            return;
        }

        if (!TerrainRendererDebugConfig.drawAllCapturedTerrainLayers()) {
            prepareVisibleTerrainCommands(terrainResources);
        }
    }

    /**
     * Uploads the work queue of every layer the mesh path can stand in for and has the GPU write
     * its mesh-task commands. The visible set is the one Minecraft collected for its own draws
     * this frame.
     */
    private void prepareVisibleTerrainCommands(DescriptorHeapTerrainResources terrainResources) {
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            if (!TerrainLayerReplacementPolicy.canReplaceLayer(layer)) {
                continue;
            }
            int layerOrdinal = layer.ordinal();
            List<SectionMeshletStore.MeshletRange> ranges = SectionMeshletStore.visibleMeshletRanges(layerOrdinal);
            if (ranges == null) {
                this.meshReplacementPreparedGpuCommandRefusals.increment();
                continue;
            }

            for (List<SectionMeshletStore.MeshletRange> batch : TerrainLayerReplacementPolicy.dispatchBatches(ranges)) {
                DescriptorHeapTerrainResources.TerrainWorkQueueUpload workQueue = terrainResources.writeVisibleWorkQueue(batch, layerOrdinal);
                TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuWorkQueue(workQueue, layerOrdinal, workQueue.count());
                DescriptorHeapTerrainResources.TerrainMeshTaskCommandUpload commandUpload = terrainResources.reserveMeshTaskCommand(dispatch.taskCount());
                TerrainMeshTaskDispatch preparedDispatch = dispatch.withIndirectCommand(commandUpload);
                if (preparedDispatch.ready() && preparedDispatch.usesWorkQueue() && preparedDispatch.usesIndirectCommand() && MeshShaderTerrainProgram.get().prepareWorkQueueIndirectCommand(preparedDispatch)) {
                    TerrainRenderContext.enqueuePreparedLayerDispatch(layerOrdinal, preparedDispatch);
                    this.meshReplacementPreparedGpuCommands.increment();
                    this.meshReplacementPreparedVisibleGpuCommands.increment();
                } else {
                    this.meshReplacementPreparedGpuCommandRefusals.increment();
                }
            }
        }
    }

    private @Nullable TerrainMeshTaskDispatch buildVisibleMeshDispatch(DescriptorHeapTerrainResources terrainResources, List<SectionMeshletStore.MeshletRange> ranges, int layerOrdinal) {
        DescriptorHeapTerrainResources.TerrainWorkQueueUpload workQueue = terrainResources.writeVisibleWorkQueue(ranges, layerOrdinal);
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuWorkQueue(workQueue, layerOrdinal, workQueue.count());
        if (!dispatch.ready()) {
            if (!TerrainRendererDebugConfig.allowCpuVisibleMeshletFallback()) {
                this.meshReplacementVisibleMeshletFallbackRefusals.increment();
                return null;
            }
            DescriptorHeapTerrainResources.VisibleMeshletUpload visibleMeshlets = terrainResources.writeVisibleMeshletList(ranges);
            if (!visibleMeshlets.ready()) {
                return null;
            }
            dispatch = TerrainMeshTaskDispatch.cpuVisibleList(visibleMeshlets, layerOrdinal);
        }
        return dispatch.ready() ? dispatch : null;
    }

    private boolean drawMeshTerrain(TerrainDrawContext context, TerrainMeshTaskDispatch dispatch, DescriptorHeapTerrainResources terrainResources) {
        TerrainPipelineState vanillaPipeline = context.vanillaPipeline();
        if (vanillaPipeline == null) {
            return false;
        }
        TerrainMeshTaskDispatch drawDispatch = dispatch;
        if (!drawDispatch.usesIndirectCommand()) {
            DescriptorHeapTerrainResources.TerrainMeshTaskCommandUpload commandUpload = terrainResources.writeMeshTaskCommand(dispatch.taskCount());
            drawDispatch = dispatch.withIndirectCommand(commandUpload);
        }
        if (MeshShaderTerrainProgram.get().drawTerrain(context.commandBuffer(), vanillaPipeline, drawDispatch, terrainResources.meshletHeaderAddress(), terrainResources.meshletVertexPayloadAddress(), terrainResources.meshletIndexPayloadAddress())) {
            terrainResources.markTerrainReadInCurrentSubmit();
            this.meshReplacementDrawCalls.increment();
            this.meshReplacementTasks.add(drawDispatch.taskCount());
            this.lastTerrainDispatch = drawDispatch;
            if (drawDispatch.usesIndirectCommand()) {
                this.meshReplacementIndirectCommandDrawCalls.increment();
            }
            if (drawDispatch.usesGpuGeneratedIndirectCommand()) {
                this.meshReplacementPreparedGpuCommandDrawCalls.increment();
            }
            if (drawDispatch.source() == TerrainMeshTaskDispatch.Source.CPU_VISIBLE_LIST) {
                this.meshReplacementVisibleListDrawCalls.increment();
            } else if (drawDispatch.source() == TerrainMeshTaskDispatch.Source.CPU_WORK_QUEUE) {
                this.meshReplacementWorkQueueDrawCalls.increment();
            }
            if (drawDispatch.truncated()) {
                this.meshReplacementTaskTruncations.increment();
            }
            int layerOrdinal = drawDispatch.layerOrdinal();
            if (layerOrdinal < 0) {
                this.meshReplacementWholeSetDrawCalls.increment();
            } else {
                this.meshReplacementLayerFilteredDrawCalls.increment();
                if (layerOrdinal < this.meshReplacementDrawCallsByLayer.length) {
                    this.meshReplacementDrawCallsByLayer[layerOrdinal].increment();
                }
                if (terrainResources.customIndexMeshletCountForLayer(layerOrdinal) > 0) {
                    this.meshReplacementIndexedDrawCalls.increment();
                    if (layerOrdinal < this.meshReplacementIndexedDrawCallsByLayer.length) {
                        this.meshReplacementIndexedDrawCallsByLayer[layerOrdinal].increment();
                    }
                }
            }
            return true;
        }
        return false;
    }

    public void configure(VulkanDevice device, VulkanImprovementCapabilities.Snapshot capabilities) {
        RendererCoreServices.get().beginConfiguration("configuring Vulkan renderer services");
        try {
            DescriptorHeapTerrainResources.get().configure(device, capabilities);
            MeshShaderTerrainProgram.get().configure(device);
            RendererCoreServices.get().configure(capabilities);
            RendererDomainObserver.observeRendererReady();
            RendererCoreServices.get().markReady("renderer services configured");
        } catch (RuntimeException | Error ex) {
            RendererCoreServices.get().markFailed(ex.getClass().getSimpleName() + " during renderer configuration");
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    public void markDeviceLost(String reason) {
        RendererCoreServices.get().markDeviceLost(reason);
    }

    @SuppressWarnings("unused")
    public void shutdown() {
        RendererCoreServices.get().beginShutdown("deferred renderer shutdown requested");
        MeshShaderTerrainProgram.get().shutdown();
        DescriptorHeapTerrainResources.get().shutdown();
        RendererCoreServices.get().completeShutdown("renderer shutdown complete");
    }

    public void shutdownNow() {
        RendererCoreServices.get().beginShutdown("immediate renderer shutdown requested");
        MeshShaderTerrainProgram.get().shutdown();
        DescriptorHeapTerrainResources.get().shutdownNow();
        RendererCoreServices.get().completeShutdown("renderer immediate shutdown complete");
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        RendererCoreServices coreServices = RendererCoreServices.get();
        map.put("configured", coreServices.currentLifecycleState().drawable());
        map.put("lifecycleState", coreServices.currentLifecycleState().name());
        map.put("lifecycleReason", coreServices.lifecycleReason());
        map.put("mode", TerrainRendererDebugConfig.rendererMode());
        map.put("terrainGroupCalls", this.terrainGroupCalls.sum());
        map.put("improvedFogTerrainGroups", this.improvedFogTerrainGroups.sum());
        map.put("fog", TerrainFogState.asMap());
        map.put("capturedSections", this.capturedSections.sum());
        map.put("releasedSections", this.releasedSections.sum());
        map.put("capturedVertexBytes", this.capturedVertexBytes.sum());
        map.put("capturedIndexBytes", this.capturedIndexBytes.sum());
        map.put("captureCpuMicros", this.captureCpuNanos.sum() / 1_000L);
        map.put("estimatedMeshlets", this.estimatedMeshlets.sum());
        map.put("meshReplacementDrawCalls", this.meshReplacementDrawCalls.sum());
        map.put("meshReplacementTasks", this.meshReplacementTasks.sum());
        map.put("meshReplacementVisibleListDrawCalls", this.meshReplacementVisibleListDrawCalls.sum());
        map.put("meshReplacementWorkQueueDrawCalls", this.meshReplacementWorkQueueDrawCalls.sum());
        map.put("meshReplacementIndirectCommandDrawCalls", this.meshReplacementIndirectCommandDrawCalls.sum());
        map.put("meshReplacementPreparedGpuCommandDrawCalls", this.meshReplacementPreparedGpuCommandDrawCalls.sum());
        map.put("meshReplacementPreparedGpuCommands", this.meshReplacementPreparedGpuCommands.sum());
        map.put("meshReplacementPreparedFullLayerGpuCommands", this.meshReplacementPreparedFullLayerGpuCommands.sum());
        map.put("meshReplacementPreparedVisibleGpuCommands", this.meshReplacementPreparedVisibleGpuCommands.sum());
        map.put("meshReplacementPreparedGpuCommandDisabled", this.meshReplacementPreparedGpuCommandDisabled.sum());
        map.put("meshReplacementPreparedGpuCommandRefusals", this.meshReplacementPreparedGpuCommandRefusals.sum());
        map.put("meshReplacementPreparedDispatchQueueLeaks", this.meshReplacementPreparedDispatchQueueLeaks.sum());
        map.put("meshReplacementVisibleMeshletFallbackRefusals", this.meshReplacementVisibleMeshletFallbackRefusals.sum());
        map.put("meshReplacementTaskTruncations", this.meshReplacementTaskTruncations.sum());
        map.put("meshReplacementLayerFilteredDrawCalls", this.meshReplacementLayerFilteredDrawCalls.sum());
        map.put("meshReplacementWholeSetDrawCalls", this.meshReplacementWholeSetDrawCalls.sum());
        map.put("meshReplacementDrawCallsByLayer", layerCounterMap(this.meshReplacementDrawCallsByLayer));
        map.put("meshReplacementCustomIndexFallbacks", this.meshReplacementCustomIndexFallbacks.sum());
        map.put("meshReplacementCustomIndexFallbacksByLayer", layerCounterMap(this.meshReplacementCustomIndexFallbacksByLayer));
        map.put("meshReplacementIndexedDrawCalls", this.meshReplacementIndexedDrawCalls.sum());
        map.put("meshReplacementIndexedDrawCallsByLayer", layerCounterMap(this.meshReplacementIndexedDrawCallsByLayer));
        map.put("meshReplacementDuplicateCancellations", this.meshReplacementDuplicateCancellations.sum());
        map.put("meshReplacementRefusals", this.meshReplacementRefusals.sum());
        map.put("lastTerrainDispatch", this.lastTerrainDispatch.asMap());
        map.put("gpuTerrainDataReady", DescriptorHeapTerrainResources.get().hasGpuTerrainData());
        map.put("domains", RendererDomainRegistry.get().asMap());
        map.put("meshShaderProgram", MeshShaderTerrainProgram.get().asMap());
        map.put("sectionStore", SectionMeshletStore.asMap());
        map.put("descriptorHeap", DescriptorHeapTerrainResources.get().asMap());
        map.put("presentPacing", PresentPacingController.get().asMap());
        map.put("fragmentShadingRate", FragmentShadingRateController.get().asMap());
        return map;
    }

    @SuppressWarnings("unused")
    RendererLifecycleState currentLifecycleState() {
        return RendererCoreServices.get().currentLifecycleState();
    }

    public record DrawResult(boolean drewMeshTerrain, boolean cancelVanillaDraw) {
        private static final DrawResult PASS_THROUGH = new DrawResult(false, false);
        private static final DrawResult REPLACED = new DrawResult(true, true);
        private static final DrawResult OVERLAY_ONLY = new DrawResult(true, false);
        private static final DrawResult FALLBACK_AFTER_PARTIAL_DRAW = new DrawResult(true, false);

        static DrawResult passThrough() {
            return PASS_THROUGH;
        }

        static DrawResult replaced() {
            return REPLACED;
        }

        static DrawResult overlayOnly() {
            return OVERLAY_ONLY;
        }

        static DrawResult fallbackAfterPartialDraw() {
            return FALLBACK_AFTER_PARTIAL_DRAW;
        }
    }
}
