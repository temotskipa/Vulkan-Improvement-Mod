package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanCapabilitySnapshot;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDatabase;
import com.temotskipa.vulkanimprovement.client.vulkan.presentation.PresentPacingController;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererCoreServices;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererDomainObserver;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererDomainRegistry;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;

public final class MeshTerrainRenderer {
    private static final MeshTerrainRenderer INSTANCE = new MeshTerrainRenderer();
    
    private final LongAdder visibilityCommandsEmitted = new LongAdder();
    private final LongAdder visibilityFallbackLayers = new LongAdder();
    private final long[] lastVisibilityCounters = new long[3];
    private final LongAdder terrainGroupCalls = new LongAdder();
    private final LongAdder improvedFogTerrainGroups = new LongAdder();
    private final LongAdder capturedSections = new LongAdder();
    private final LongAdder releasedSections = new LongAdder();
    private final LongAdder capturedVertexBytes = new LongAdder();
    private final LongAdder capturedIndexBytes = new LongAdder();
    private final LongAdder captureCpuNanos = new LongAdder();
    private final LongAdder estimatedMeshlets = new LongAdder();
    private final LongAdder meshReplacementDrawCalls = new LongAdder();
    private final LongAdder gpuDrivenFrames = new LongAdder();
    private final LongAdder gpuDrivenFrameRefusals = new LongAdder();
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
    // A GPU-driven frame's state, used on the render thread only and reused from frame to frame.
    private final VisibleSectionGrid visibleSectionGrid = new VisibleSectionGrid();
    private final int[] gpuDrivenLayerTasks = new int[ChunkSectionLayer.values().length];
    private final int[] gpuDrivenLayerOutputs = new int[TerrainGpuLayout.SECTION_PAGES_LAYERS];
    private final int[] gpuDrivenLayerCommands = new int[TerrainGpuLayout.SECTION_PAGES_LAYERS];
    private final TerrainMeshTaskDispatch[] gpuDrivenDispatches = new TerrainMeshTaskDispatch[ChunkSectionLayer.values().length];
    private volatile TerrainMeshTaskDispatch lastTerrainDispatch = TerrainMeshTaskDispatch.unavailable("not dispatched");
    private boolean gpuDrivenFrame;
    
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
     * Where the run of a layer's pages that starts at {@code first} ends: as many pages, in their
     * order, as one draw can launch the tasks of. A page has far fewer tasks than a draw can launch,
     * so a run holds at least one.
     */
    private static int taskBatchEnd(MeshletPageList pages, int first) {
        int end = first;
        int tasks = 0;
        while (end < pages.size() && tasks + pages.tasks(end) <= TerrainMeshTaskDispatch.MAX_DIRECT_TASKS) {
            tasks += pages.tasks(end);
            end++;
        }
        return end;
    }
    
    /**
     * Called when Minecraft starts drawing a group of terrain layers, before it sets a pipeline.
     * Wireframe terrain is left to Minecraft: its pipelines are not ones the mesh path stands in for.
     *
     * @param improvedFog whether the group fades into the sky and cloud images instead of the fog
     *                    colour, which Minecraft draws when improved transparency is on
     */
    public void observeTerrainGroup(boolean wireframe, boolean improvedFog) {
        if (!TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        this.terrainGroupCalls.increment();
        if (improvedFog) {
            this.improvedFogTerrainGroups.increment();
        }
        TerrainRenderContext.setReplacementAllowed(!wireframe);
        TerrainRenderContext.setImprovedFog(improvedFog);
    }
    
    /**
     * Whether the mesh path takes the section layers it can draw out of Minecraft's draw lists for
     * the frame about to be collected. Decided before Minecraft collects the frame: a layer taken
     * then is drawn by nobody else, so it is only done when the mesh path is ready to draw.
     *
     * @param wireframe whether Minecraft draws this frame's terrain as wireframe, which is left to it
     */
    public boolean takesOverSectionLayers(boolean wireframe) {
        if (wireframe || !TerrainRendererDebugConfig.replaceVanillaTerrain() || !TerrainRendererDebugConfig.strictMeshTerrainReplacement() || TerrainRendererDebugConfig.drawAllCapturedTerrainLayers()) {
            return false;
        }
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        return RendererCoreServices.get().currentLifecycleState().drawable() && terrainResources.hasGpuTerrainData() && MeshShaderTerrainProgram.get().ready() && terrainResources.meshletsUploaded() > 0;
    }
    
    /**
     * Called before Minecraft collects a frame's section layers, while no render pass is open.
     * Uploads the pages that wait for one, so which section layers have pages is settled for the
     * frame, and decides how the mesh path takes the frame's terrain. In a GPU-driven frame the
     * GPU then writes the draws of the opaque terrain of every section in view, told only which
     * sections Minecraft found in view, and Minecraft collects only the sections that draw
     * something the GPU does not, which this lists in {@code collected} in Minecraft's order.
     *
     * @param wireframe  whether Minecraft draws this frame's terrain as wireframe
     * @param fadeMillis how long Minecraft takes to fade a section in
     * @return whether the frame is GPU-driven, so that Minecraft collects only {@code collected}
     */
    public boolean beginVisibleFrame(boolean wireframe, long fadeMillis, ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections, ObjectArrayList<SectionRenderDispatcher.RenderSection> collected) {
        collected.clear();
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        terrainResources.uploadDirtyTerrainData();
        publishVisibilityStats(terrainResources);
        int unconsumedPreparedDispatches = TerrainRenderContext.beginPreparedFrame();
        if (unconsumedPreparedDispatches > 0) {
            this.meshReplacementPreparedDispatchQueueLeaks.add(unconsumedPreparedDispatches);
        }
        boolean takeover = takesOverSectionLayers(wireframe);
        int fade = TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled() ? (int) Math.clamp(fadeMillis, 0L, Integer.MAX_VALUE) : 0;
        this.gpuDrivenFrame = takeover && prepareGpuDrivenFrame(terrainResources, fade, visibleSections, collected);
        SectionMeshletStore.beginVisibleFrame(takeover, this.gpuDrivenFrame);
        return this.gpuDrivenFrame;
    }
    
    /**
     * Called once Minecraft has collected the frame's section layers and before it opens a render
     * pass. Minecraft hands the terrain renderers a pass that is already open, and the compute
     * step that writes mesh-task commands cannot be recorded inside one, so what the layers need
     * from the GPU this frame and was not prepared before Minecraft collected it is prepared here.
     *
     * @param wireframe whether Minecraft draws this frame's terrain as wireframe
     */
    public void prepareVisibleFrame(boolean wireframe) {
        if (!TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        // Wireframe terrain is Minecraft's to draw, so commands prepared for it would never be
        // used; a GPU-driven frame's were prepared before Minecraft collected the frame.
        if (!wireframe && !this.gpuDrivenFrame) {
            prepareTerrainCommands();
        }
        this.gpuDrivenFrame = false;
    }
    
    /**
     * Has the GPU write the draws of the frame's opaque terrain from its own page table, given the
     * sections Minecraft found in view as a grid of bits, and lists in {@code collected} the
     * sections Minecraft still has to collect. When this returns false the GPU draws nothing of
     * the kind and Minecraft collects every section as usual.
     */
    private boolean prepareGpuDrivenFrame(DescriptorHeapTerrainResources terrainResources, int fadeMillis, ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections, ObjectArrayList<SectionRenderDispatcher.RenderSection> collected) {
        if (!TerrainRendererDebugConfig.gpuDrivenTerrainEnabled()) {
            return false;
        }
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        int maxTasks = VulkanImprovementCapabilities.maxTaskWorkGroups();
        int drawnTasks = 0;
        for (ChunkSectionLayer layer : layers) {
            int ordinal = layer.ordinal();
            int tasks = TerrainLayerReplacementPolicy.canReplaceLayer(layer) ? SectionMeshletStore.layerTasks(ordinal) : 0;
            // One draw launches a layer's tasks, so a layer with more than a draw may launch is
            // left to the listed path, which splits it. The pass has room for SECTION_PAGES_LAYERS
            // layers; Minecraft 26.4 has three.
            if (tasks > maxTasks) {
                this.gpuDrivenFrameRefusals.increment();
                return false;
            }
            this.gpuDrivenLayerTasks[ordinal] = tasks;
            drawnTasks += tasks;
        }
        if (drawnTasks == 0 || !this.visibleSectionGrid.build(visibleSections, collected)) {
            collected.clear();
            return false;
        }
        TerrainGpuDrivenFrame frame = terrainResources.writeGpuDrivenFrame(this.visibleSectionGrid, this.gpuDrivenLayerTasks);
        if (!frame.ready()) {
            return refuseGpuDrivenFrame(collected);
        }
        Arrays.fill(this.gpuDrivenLayerCommands, -1);
        Arrays.fill(this.gpuDrivenDispatches, null);
        int output = frame.firstTaskRecord();
        for (ChunkSectionLayer layer : layers) {
            int ordinal = layer.ordinal();
            int tasks = this.gpuDrivenLayerTasks[ordinal];
            if (tasks == 0) {
                continue;
            }
            TerrainMeshTaskCommandUpload command = terrainResources.reserveMeshTaskCommand(tasks);
            TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.gpuDrivenPages(frame, output, tasks, ordinal).withIndirectCommand(command);
            if (!dispatch.ready() || !dispatch.usesIndirectCommand()) {
                return refuseGpuDrivenFrame(collected);
            }
            this.gpuDrivenLayerOutputs[ordinal] = output;
            this.gpuDrivenLayerCommands[ordinal] = command.commandIndex();
            this.gpuDrivenDispatches[ordinal] = dispatch;
            output += tasks;
        }
        if (!MeshShaderTerrainProgram.get().prepareGpuDrivenPages(frame, this.visibleSectionGrid, SectionMeshletStore.pageSlotHighWater(), this.gpuDrivenLayerOutputs, this.gpuDrivenLayerCommands, fadeMillis)) {
            return refuseGpuDrivenFrame(collected);
        }
        for (TerrainMeshTaskDispatch dispatch : this.gpuDrivenDispatches) {
            if (dispatch != null) {
                TerrainRenderContext.enqueuePreparedLayerDispatch(dispatch.layerOrdinal(), dispatch);
                this.meshReplacementPreparedGpuCommands.increment();
            }
        }
        this.gpuDrivenFrames.increment();
        return true;
    }
    
    private boolean refuseGpuDrivenFrame(ObjectArrayList<SectionRenderDispatcher.RenderSection> collected) {
        collected.clear();
        this.gpuDrivenFrameRefusals.increment();
        return false;
    }
    
    /**
     * Called for each layer once Minecraft has bound that layer's pipeline. Draws the layer's
     * meshlets and says whether Minecraft's own draws for the layer are to be skipped.
     */
    public boolean drawTerrainLayer(ChunkSectionLayer layer, Object renderPass) {
        if (!TerrainRenderContext.isTerrainPass() || !TerrainRendererDebugConfig.replaceVanillaTerrain()) {
            return false;
        }
        TerrainRenderPassAccess access = TerrainRenderPassAccess.of(renderPass);
        if (access == null) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        TerrainDrawResult drawResult = tryDrawMeshTerrain(TerrainDrawContext.forLayer(access, layer.ordinal()));
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
    
    public TerrainDrawResult tryDrawMeshTerrain(TerrainDrawContext context) {
        if (!canAttemptMeshTerrain(context)) {
            return TerrainDrawResult.passThrough();
        }
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        if (context.useVisibleDrawList()) {
            return tryDrawVisibleMeshTerrain(context, terrainResources);
        }
        
        return tryDrawLayerMeshTerrain(context, terrainResources);
    }
    
    private TerrainDrawResult tryDrawLayerMeshTerrain(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources) {
        int layerOrdinal = context.layerOrdinal();
        if (!TerrainLayerReplacementPolicy.canReplaceLayer(layerOrdinal)) {
            this.meshReplacementRefusals.increment();
            return TerrainDrawResult.passThrough();
        }
        if (terrainResources.meshletCountForLayer(layerOrdinal) <= 0) {
            this.meshReplacementRefusals.increment();
            return TerrainDrawResult.passThrough();
        }
        if (!TerrainRenderContext.markDispatchRequired(layerOrdinal)) {
            this.meshReplacementDuplicateCancellations.increment();
            return TerrainDrawResult.passThrough();
        }
        
        TerrainMeshTaskDispatch dispatch = TerrainRenderContext.pollPreparedLayerDispatch(layerOrdinal);
        if (dispatch != null) {
            return drawPreparedLayerDispatches(context, dispatch, terrainResources);
        }
        if (!context.drawAllCapturedTerrainLayers()) {
            this.meshReplacementRefusals.increment();
            return TerrainDrawResult.passThrough();
        }
        return drawFullLayerSegments(context, terrainResources, layerOrdinal);
    }
    
    private TerrainDrawResult tryDrawVisibleMeshTerrain(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources) {
        int layerOrdinal = context.layerOrdinal();
        if (!TerrainLayerReplacementPolicy.canReplaceLayer(layerOrdinal)) {
            this.meshReplacementRefusals.increment();
            return TerrainDrawResult.passThrough();
        }
        
        TerrainMeshTaskDispatch preparedDispatch = TerrainRenderContext.pollPreparedLayerDispatch(layerOrdinal);
        if (preparedDispatch != null) {
            return drawPreparedLayerDispatches(context, preparedDispatch, terrainResources);
        }
        
        List<MeshletRange> ranges = SectionMeshletStore.visibleMeshletRanges(layerOrdinal);
        if (ranges == null || ranges.isEmpty()) {
            this.meshReplacementRefusals.increment();
            return TerrainDrawResult.passThrough();
        }
        
        boolean drewAny = false;
        for (List<MeshletRange> batch : TerrainLayerReplacementPolicy.dispatchBatches(ranges)) {
            TerrainMeshTaskDispatch dispatch = buildVisibleMeshDispatch(terrainResources, batch, layerOrdinal);
            if (dispatch == null || failedToDrawMeshTerrain(context, dispatch, terrainResources)) {
                this.meshReplacementRefusals.increment();
                return drewAny ? TerrainDrawResult.fallbackAfterPartialDraw() : TerrainDrawResult.passThrough();
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
    
    private TerrainDrawResult drawPreparedLayerDispatches(TerrainDrawContext context, TerrainMeshTaskDispatch firstDispatch, DescriptorHeapTerrainResources terrainResources) {
        boolean drewAny = false;
        TerrainMeshTaskDispatch dispatch = firstDispatch;
        while (dispatch != null) {
            if (!dispatch.ready() || failedToDrawMeshTerrain(context, dispatch, terrainResources)) {
                this.meshReplacementRefusals.increment();
                return drewAny ? TerrainDrawResult.fallbackAfterPartialDraw() : TerrainDrawResult.passThrough();
            }
            drewAny = true;
            dispatch = TerrainRenderContext.pollPreparedLayerDispatch(context.layerOrdinal());
        }
        return drewAny ? visibleDrawResult() : TerrainDrawResult.passThrough();
    }
    
    /**
     * In strict replacement Minecraft's own draws for the layer are what the mesh path did not
     * take, so they run too; otherwise they are drawn over.
     */
    private TerrainDrawResult visibleDrawResult() {
        return TerrainDrawResult.overlayOnly();
    }
    
    /**
     * Draws every page a layer has on the GPU, visible or not, one page at a time. A page's
     * meshlets are one range of the meshlet headers; a layer's are not.
     */
    private TerrainDrawResult drawFullLayerSegments(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources, int layerOrdinal) {
        boolean drewAny = false;
        for (MeshletRange page : SectionMeshletStore.meshletRangesForLayer(layerOrdinal)) {
            int segments = TerrainLayerReplacementPolicy.dispatchCountForMeshlets(page.count());
            for (int segment = 0; segment < segments; segment++) {
                int segmentOffset = TerrainLayerReplacementPolicy.segmentMeshletOffset(page.offset(), segment);
                int segmentCount = TerrainLayerReplacementPolicy.segmentMeshletCount(page.count(), segment);
                TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(segmentOffset, layerOrdinal, segmentCount);
                if (!dispatch.ready() || failedToDrawMeshTerrain(context, dispatch, terrainResources)) {
                    this.meshReplacementRefusals.increment();
                    return drewAny ? TerrainDrawResult.fallbackAfterPartialDraw() : TerrainDrawResult.passThrough();
                }
                drewAny = true;
            }
        }
        return drewAny ? TerrainDrawResult.overlayOnly() : TerrainDrawResult.passThrough();
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
     * Hands every layer the mesh path can stand in for to the GPU visibility pass. The pages of
     * the section layers Minecraft collected for the frame are listed, a few bytes each; the GPU
     * keeps the ones in view and writes the mesh-task command that draws them. A layer with a
     * section layer that has no page yet is left to Minecraft for the frame.
     */
    private void prepareVisibleTerrainCommands(DescriptorHeapTerrainResources terrainResources) {
        if (TerrainRendererDebugConfig.taskStagePageCullingEnabled()) {
            prepareListedPages(terrainResources);
            return;
        }
        List<TerrainPreparedPages> prepared = new ArrayList<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            if (!TerrainLayerReplacementPolicy.canReplaceLayer(layer)) {
                continue;
            }
            int layerOrdinal = layer.ordinal();
            MeshletPageList pages = SectionMeshletStore.visiblePages(layerOrdinal);
            if (pages == null) {
                this.meshReplacementPreparedGpuCommandRefusals.increment();
                this.visibilityFallbackLayers.increment();
                continue;
            }
            
            for (int first = 0, end; first < pages.size(); first = end) {
                end = taskBatchEnd(pages, first);
                TerrainVisiblePageUpload upload = terrainResources.writeVisiblePageList(pages, first, end);
                TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.gpuVisiblePages(upload, layerOrdinal);
                TerrainMeshTaskCommandUpload commandUpload = terrainResources.reserveMeshTaskCommand(dispatch.taskCount());
                TerrainMeshTaskDispatch preparedDispatch = dispatch.withIndirectCommand(commandUpload);
                if (preparedDispatch.ready() && preparedDispatch.usesIndirectCommand()) {
                    prepared.add(new TerrainPreparedPages(preparedDispatch, upload));
                }
                else {
                    this.meshReplacementPreparedGpuCommandRefusals.increment();
                    this.visibilityFallbackLayers.increment();
                }
            }
        }
        if (prepared.isEmpty()) {
            return;
        }
        // Every layer's pass goes into one command buffer, with one barrier behind them all.
        if (!MeshShaderTerrainProgram.get().prepareVisiblePageCommands(prepared)) {
            this.meshReplacementPreparedGpuCommandRefusals.add(prepared.size());
            this.visibilityFallbackLayers.add(prepared.size());
            return;
        }
        for (TerrainPreparedPages layer : prepared) {
            TerrainRenderContext.enqueuePreparedLayerDispatch(layer.dispatch().layerOrdinal(), layer.dispatch());
            this.meshReplacementPreparedGpuCommands.increment();
            this.meshReplacementPreparedVisibleGpuCommands.increment();
            this.visibilityCommandsEmitted.increment();
        }
    }
    
    /**
     * Hands each layer's listed pages straight to the task stage in the order Minecraft collected
     * them, nearest first, one task per 32 of a page's meshlets. Nothing runs before the draw: the task stage drops
     * pages and meshlets out of view or seen from behind, and the draw keeps Minecraft's order,
     * which lets the GPU reject hidden terrain by depth as early as vanilla does.
     */
    private void prepareListedPages(DescriptorHeapTerrainResources terrainResources) {
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            if (!TerrainLayerReplacementPolicy.canReplaceLayer(layer)) {
                continue;
            }
            int layerOrdinal = layer.ordinal();
            MeshletPageList pages = SectionMeshletStore.visiblePages(layerOrdinal);
            if (pages == null) {
                this.meshReplacementPreparedGpuCommandRefusals.increment();
                this.visibilityFallbackLayers.increment();
                continue;
            }
            for (int first = 0, end; first < pages.size(); first = end) {
                end = taskBatchEnd(pages, first);
                TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.listedPages(terrainResources.writeVisiblePageList(pages, first, end), layerOrdinal);
                if (dispatch.ready()) {
                    TerrainRenderContext.enqueuePreparedLayerDispatch(layerOrdinal, dispatch);
                    this.meshReplacementPreparedVisibleGpuCommands.increment();
                }
                else {
                    this.meshReplacementPreparedGpuCommandRefusals.increment();
                    this.visibilityFallbackLayers.increment();
                }
            }
        }
    }
    
    /**
     * Tells the GPU world diagnostics what the visibility pass did since this was last called. The
     * page counts come from counters the GPU keeps, so they describe frames that have been drawn
     * and run behind the commands, which are counted as they are recorded.
     */
    private void publishVisibilityStats(DescriptorHeapTerrainResources terrainResources) {
        long[] counters = terrainResources.visibilityCounters();
        long[] sinceLast = new long[counters.length];
        for (int i = 0; i < counters.length; i++) {
            // The counters start again from zero when the buffers are replaced.
            sinceLast[i] = counters[i] >= this.lastVisibilityCounters[i] ? counters[i] - this.lastVisibilityCounters[i] : counters[i];
            this.lastVisibilityCounters[i] = counters[i];
        }
        long emitted = this.visibilityCommandsEmitted.sumThenReset();
        long fallback = this.visibilityFallbackLayers.sumThenReset();
        if (sinceLast[0] == 0L && emitted == 0L && fallback == 0L) {
            return;
        }
        long culled = Math.min(sinceLast[1], sinceLast[0]);
        long visible = Math.min(sinceLast[2], sinceLast[0] - culled);
        GpuWorldDatabase.get().recordVisibilityCommandPass(sinceLast[0], culled, visible, emitted, 0L, fallback);
    }
    
    private @Nullable TerrainMeshTaskDispatch buildVisibleMeshDispatch(DescriptorHeapTerrainResources terrainResources, List<MeshletRange> ranges, int layerOrdinal) {
        TerrainWorkQueueUpload workQueue = terrainResources.writeVisibleWorkQueue(ranges, layerOrdinal);
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuWorkQueue(workQueue, layerOrdinal, workQueue.count());
        if (!dispatch.ready()) {
            if (!TerrainRendererDebugConfig.allowCpuVisibleMeshletFallback()) {
                this.meshReplacementVisibleMeshletFallbackRefusals.increment();
                return null;
            }
            TerrainVisibleMeshletUpload visibleMeshlets = terrainResources.writeVisibleMeshletList(ranges);
            if (!visibleMeshlets.ready()) {
                return null;
            }
            dispatch = TerrainMeshTaskDispatch.cpuVisibleList(visibleMeshlets, layerOrdinal);
        }
        return dispatch.ready() ? dispatch : null;
    }
    
    /**
     * Draws a dispatch's terrain, and says whether it could not, leaving the layer to Minecraft.
     */
    private boolean failedToDrawMeshTerrain(TerrainDrawContext context, TerrainMeshTaskDispatch dispatch, DescriptorHeapTerrainResources terrainResources) {
        TerrainPipelineState vanillaPipeline = context.vanillaPipeline();
        if (vanillaPipeline == null) {
            return true;
        }
        TerrainMeshTaskDispatch drawDispatch = dispatch;
        if (!drawDispatch.usesIndirectCommand()) {
            TerrainMeshTaskCommandUpload commandUpload = terrainResources.writeMeshTaskCommand(dispatch.taskCount());
            drawDispatch = dispatch.withIndirectCommand(commandUpload);
        }
        if (MeshShaderTerrainProgram.get().drawTerrain(context.commandBuffer(), vanillaPipeline, drawDispatch, terrainResources.meshletHeaderAddress(), terrainResources.meshletVertexPayloadAddress(), terrainResources.meshletIndexPayloadAddress())) {
            this.meshReplacementDrawCalls.increment();
            this.meshReplacementTasks.add(drawDispatch.taskCount());
            this.lastTerrainDispatch = drawDispatch;
            if (drawDispatch.usesIndirectCommand()) {
                this.meshReplacementIndirectCommandDrawCalls.increment();
            }
            if (drawDispatch.usesGpuGeneratedIndirectCommand()) {
                this.meshReplacementPreparedGpuCommandDrawCalls.increment();
            }
            if (drawDispatch.source() == TerrainMeshTaskSource.CPU_VISIBLE_LIST) {
                this.meshReplacementVisibleListDrawCalls.increment();
            }
            else if (drawDispatch.source() == TerrainMeshTaskSource.CPU_WORK_QUEUE) {
                this.meshReplacementWorkQueueDrawCalls.increment();
            }
            if (drawDispatch.truncated()) {
                this.meshReplacementTaskTruncations.increment();
            }
            int layerOrdinal = drawDispatch.layerOrdinal();
            if (layerOrdinal < 0) {
                this.meshReplacementWholeSetDrawCalls.increment();
            }
            else {
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
            return false;
        }
        return true;
    }
    
    public void configure(VulkanDevice device, VulkanCapabilitySnapshot capabilities) {
        RendererCoreServices.get().beginConfiguration();
        try {
            DescriptorHeapTerrainResources.get().configure(device, capabilities);
            MeshShaderTerrainProgram.get().configure(device);
            TerrainPerformanceMonitor.get().configure(device);
            RendererCoreServices.get().configure(capabilities);
            RendererDomainObserver.observeRendererReady();
            RendererCoreServices.get().markReady();
        } catch (RuntimeException | Error ex) {
            RendererCoreServices.get().markFailed(ex.getClass().getSimpleName() + " during renderer configuration");
            throw ex;
        }
    }
    
    
    public void shutdownNow() {
        RendererCoreServices.get().beginShutdown("immediate renderer shutdown requested");
        TerrainPerformanceMonitor.get().shutdown();
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
        map.put("performance", TerrainPerformanceMonitor.get().asMap());
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
        map.put("gpuDrivenFrames", this.gpuDrivenFrames.sum());
        map.put("gpuDrivenFrameRefusals", this.gpuDrivenFrameRefusals.sum());
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
    
}