package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.presentation.PresentPacingController;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererCoreServices;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererDomainObserver;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererDomainRegistry;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.RendererLifecycleState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class MeshTerrainRenderer {
    private static final MeshTerrainRenderer INSTANCE = new MeshTerrainRenderer();

    private final LongAdder terrainGroupCalls = new LongAdder();
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

    public void observeTerrainGroup(ChunkSectionsToRender chunkSectionsToRender, ChunkSectionLayerGroup group) {
        if (!TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        this.terrainGroupCalls.increment();
        TerrainRenderContext.setCurrentTerrainGroup(chunkSectionsToRender, group);
        DescriptorHeapTerrainResources.get().uploadDirtyTerrainData();
        prepareTerrainCommands(chunkSectionsToRender, group);
    }

    public void finalizeTerrainGroupObservation() {
        int unconsumedPreparedDispatches = TerrainRenderContext.unconsumedPreparedDispatchCount();
        if (unconsumedPreparedDispatches > 0) {
            this.meshReplacementPreparedDispatchQueueLeaks.add(unconsumedPreparedDispatches);
        }
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

    public boolean tryDrawMeshTerrain(TerrainDrawContext context) {
        if (!canAttemptMeshTerrain(context)) {
            return false;
        }
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        if (context.useVisibleDrawList()) {
            return tryDrawVisibleMeshTerrain(context, terrainResources);
        }

        return tryDrawLayerMeshTerrain(context, terrainResources);
    }

    private boolean tryDrawLayerMeshTerrain(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources) {
        int totalMeshletCount = terrainResources.meshletsUploaded();

        int layerOrdinal = context.layerOrdinal();
        int meshletOffset = layerOrdinal < 0 ? 0 : terrainResources.meshletOffsetForLayer(layerOrdinal);
        int meshletCount = layerOrdinal < 0 ? totalMeshletCount : terrainResources.meshletCountForLayer(layerOrdinal);
        if (meshletCount <= 0) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        if (!TerrainRenderContext.markDispatchRequired(layerOrdinal)) {
            this.meshReplacementDuplicateCancellations.increment();
            return false;
        }

        TerrainMeshTaskDispatch dispatch = TerrainRenderContext.pollPreparedLayerDispatch(layerOrdinal);
        if (dispatch == null || !dispatch.ready()) {
            dispatch = TerrainMeshTaskDispatch.fullLayer(meshletOffset, layerOrdinal, meshletCount);
            DescriptorHeapTerrainResources.TerrainWorkQueueUpload workQueue = terrainResources.writeLayerWorkQueue(meshletOffset, layerOrdinal, Math.min(meshletCount, TerrainMeshTaskDispatch.MAX_DIRECT_TASKS));
            if (workQueue.ready()) {
                dispatch = TerrainMeshTaskDispatch.cpuWorkQueue(workQueue, layerOrdinal, meshletCount);
            }
        }
        if (drawMeshTerrain(context, dispatch, terrainResources)) {
            return true;
        }

        this.meshReplacementRefusals.increment();
        return false;
    }

    private boolean tryDrawVisibleMeshTerrain(TerrainDrawContext context, DescriptorHeapTerrainResources terrainResources) {
        int layerOrdinal = context.layerOrdinal();
        if (layerOrdinal < 0) {
            this.meshReplacementRefusals.increment();
            return false;
        }

        List<SectionMeshletStore.MeshletRange> ranges = visibleMeshletRanges(context.draws(), layerOrdinal);
        if (ranges == null || ranges.isEmpty()) {
            this.meshReplacementRefusals.increment();
            return false;
        }

        TerrainMeshTaskDispatch dispatch = buildVisibleMeshDispatch(terrainResources, ranges, layerOrdinal);
        if (dispatch == null) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        if (drawMeshTerrain(context, dispatch, terrainResources)) {
            return true;
        }

        this.meshReplacementRefusals.increment();
        return false;
    }

    private boolean canAttemptMeshTerrain(TerrainDrawContext context) {
        if (!TerrainRendererDebugConfig.replaceVanillaTerrain() || !context.terrainPass()) {
            return false;
        }
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        if (!RendererCoreServices.get().currentLifecycleState().drawable() || context.vanillaPipeline() == null || !terrainResources.hasGpuTerrainData() || !MeshShaderTerrainProgram.get().ready() || terrainResources.meshletsUploaded() <= 0) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        return true;
    }

    private List<SectionMeshletStore.MeshletRange> visibleMeshletRanges(Collection<? extends RenderPass.Draw<?>> draws, int layerOrdinal) {
        List<SectionMeshletStore.MeshletRange> ranges = new ArrayList<>(draws.size());
        int drawCount = 0;
        for (RenderPass.Draw<?> draw : draws) {
            if (draw.indexCount() <= 0) {
                continue;
            }
            drawCount++;
            SectionMeshletStore.MeshletRange range = SectionMeshletStore.meshletRangeForDraw(draw, layerOrdinal);
            if (range == null || range.count() <= 0) {
                return null;
            }
            ranges.add(range);
        }
        return drawCount == 0 ? List.of() : ranges;
    }

    private void prepareTerrainCommands(ChunkSectionsToRender chunkSectionsToRender, ChunkSectionLayerGroup group) {
        if (group == null || !TerrainRendererDebugConfig.replaceVanillaTerrain()) {
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
            prepareVisibleTerrainCommands(chunkSectionsToRender, group, terrainResources);
            return;
        }

        for (ChunkSectionLayer layer : group.layers()) {
            int layerOrdinal = layer.ordinal();
            int meshletOffset = terrainResources.meshletOffsetForLayer(layerOrdinal);
            int meshletCount = terrainResources.meshletCountForLayer(layerOrdinal);
            if (meshletCount <= 0) {
                continue;
            }

            DescriptorHeapTerrainResources.TerrainWorkQueueUpload workQueue = terrainResources.writeLayerWorkQueue(meshletOffset, layerOrdinal, Math.min(meshletCount, TerrainMeshTaskDispatch.MAX_DIRECT_TASKS));
            TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuWorkQueue(workQueue, layerOrdinal, meshletCount);
            DescriptorHeapTerrainResources.TerrainMeshTaskCommandUpload commandUpload = terrainResources.reserveMeshTaskCommand(dispatch.taskCount());
            TerrainMeshTaskDispatch preparedDispatch = dispatch.withIndirectCommand(commandUpload);
            if (preparedDispatch.ready() && preparedDispatch.usesWorkQueue() && preparedDispatch.usesIndirectCommand() && MeshShaderTerrainProgram.get().prepareWorkQueueIndirectCommand(preparedDispatch)) {
                TerrainRenderContext.enqueuePreparedLayerDispatch(layerOrdinal, preparedDispatch);
                this.meshReplacementPreparedGpuCommands.increment();
                this.meshReplacementPreparedFullLayerGpuCommands.increment();
            } else {
                this.meshReplacementPreparedGpuCommandRefusals.increment();
            }
        }
    }

    private void prepareVisibleTerrainCommands(ChunkSectionsToRender chunkSectionsToRender, ChunkSectionLayerGroup group, DescriptorHeapTerrainResources terrainResources) {
        if (chunkSectionsToRender == null) {
            this.meshReplacementPreparedGpuCommandRefusals.increment();
            return;
        }

        for (ChunkSectionLayer layer : group.layers()) {
            int layerOrdinal = layer.ordinal();
            Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup = chunkSectionsToRender.drawGroupsPerLayer().get(layer);
            if (drawGroup == null || drawGroup.isEmpty()) {
                continue;
            }

            for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                if (draws.isEmpty()) {
                    continue;
                }
                List<RenderPass.Draw<GpuBufferSlice[]>> orderedDraws = layer == ChunkSectionLayer.TRANSLUCENT ? draws.reversed() : draws;
                List<SectionMeshletStore.MeshletRange> ranges = visibleMeshletRanges(orderedDraws, layerOrdinal);
                if (ranges == null) {
                    this.meshReplacementPreparedGpuCommandRefusals.increment();
                    continue;
                }
                if (ranges.isEmpty()) {
                    continue;
                }

                DescriptorHeapTerrainResources.TerrainWorkQueueUpload workQueue = terrainResources.writeVisibleWorkQueue(ranges, layerOrdinal);
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
        VulkanRenderPipeline vanillaPipeline = context.vanillaPipeline();
        if (vanillaPipeline == null) {
            return false;
        }
        TerrainMeshTaskDispatch drawDispatch = dispatch;
        if (!drawDispatch.usesIndirectCommand()) {
            DescriptorHeapTerrainResources.TerrainMeshTaskCommandUpload commandUpload = terrainResources.writeMeshTaskCommand(dispatch.taskCount());
            drawDispatch = dispatch.withIndirectCommand(commandUpload);
        }
        if (MeshShaderTerrainProgram.get().drawTerrain(context.commandBuffer(), vanillaPipeline, context.hasDepth(), drawDispatch, terrainResources.meshletHeaderAddress(), terrainResources.meshletVertexPayloadAddress(), terrainResources.meshletIndexPayloadAddress())) {
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
}
