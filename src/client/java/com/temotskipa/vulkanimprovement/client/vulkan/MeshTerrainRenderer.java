package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;

import java.util.ArrayList;
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
    private final LongAdder meshReplacementPreparedGpuCommandRefusals = new LongAdder();
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
    private volatile RendererLifecycleState lifecycleState = RendererLifecycleState.UNCONFIGURED;
    private volatile String lifecycleReason = "not configured";
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
        this.terrainGroupCalls.increment();
        TerrainRenderContext.setCurrentTerrainGroup(chunkSectionsToRender, group);
        DescriptorHeapTerrainResources.get().uploadDirtyTerrainData();
        prepareTerrainCommands(group);
    }

    public void recordSectionCapture(int vertexBytes, int indexBytes, int meshlets) {
        recordSectionCapture(vertexBytes, indexBytes, meshlets, 0L);
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
            return true;
        }

        TerrainMeshTaskDispatch dispatch = TerrainRenderContext.preparedLayerDispatch(layerOrdinal);
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
        if (!TerrainRenderContext.markDispatchRequired(layerOrdinal)) {
            this.meshReplacementDuplicateCancellations.increment();
            return true;
        }

        List<SectionMeshletStore.MeshletRange> ranges = new ArrayList<>(context.draws().size());
        int drawCount = 0;
        for (RenderPass.Draw<?> draw : context.draws()) {
            if (draw.indexCount() <= 0) {
                continue;
            }
            drawCount++;
            SectionMeshletStore.MeshletRange range = SectionMeshletStore.meshletRangeForDraw(draw, layerOrdinal);
            if (range == null || range.count() <= 0) {
                this.meshReplacementRefusals.increment();
                return false;
            }
            ranges.add(range);
        }
        if (drawCount == 0 || ranges.isEmpty()) {
            this.meshReplacementRefusals.increment();
            return false;
        }

        DescriptorHeapTerrainResources.VisibleMeshletUpload visibleMeshlets = terrainResources.writeVisibleMeshletList(ranges);
        if (!visibleMeshlets.ready()) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuVisibleList(visibleMeshlets, layerOrdinal);
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
        if (!this.lifecycleState.drawable() || context.vanillaPipeline() == null || !terrainResources.hasGpuTerrainData() || !MeshShaderTerrainProgram.get().ready() || terrainResources.meshletsUploaded() <= 0) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        return true;
    }

    private void prepareTerrainCommands(ChunkSectionLayerGroup group) {
        if (group == null || !TerrainRendererDebugConfig.replaceVanillaTerrain() || !TerrainRendererDebugConfig.drawAllCapturedTerrainLayers()) {
            return;
        }

        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        if (!this.lifecycleState.drawable() || !terrainResources.hasGpuTerrainData() || !MeshShaderTerrainProgram.get().ready() || terrainResources.meshletsUploaded() <= 0) {
            this.meshReplacementPreparedGpuCommandRefusals.increment();
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
                TerrainRenderContext.setPreparedLayerDispatch(layerOrdinal, preparedDispatch);
                this.meshReplacementPreparedGpuCommands.increment();
            } else {
                this.meshReplacementPreparedGpuCommandRefusals.increment();
            }
        }
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
        setLifecycleState(RendererLifecycleState.CONFIGURING, "configuring Vulkan renderer services");
        RendererDomainRegistry.get().reset();
        try {
            DescriptorHeapTerrainResources.get().configure(device, capabilities);
            MeshShaderTerrainProgram.get().configure(device);
            PresentPacingController.get().configure(capabilities);
            FragmentShadingRateController.get().configure(capabilities);
            RendererDomainRegistry.get().set(RendererDomain.TERRAIN, RendererDomainRegistry.DomainState.meshTerrain("terrain mesh shader path configured"));
            setLifecycleState(RendererLifecycleState.READY, "renderer services configured");
        } catch (RuntimeException | Error ex) {
            setLifecycleState(RendererLifecycleState.FAILED, ex.getClass().getSimpleName() + " during renderer configuration");
            throw ex;
        }
    }

    public void markDeviceLost(String reason) {
        setLifecycleState(RendererLifecycleState.DEVICE_LOST, reason == null || reason.isBlank() ? "Vulkan device lost" : reason);
    }

    public void shutdown() {
        setLifecycleState(RendererLifecycleState.SHUTTING_DOWN, "deferred renderer shutdown requested");
        MeshShaderTerrainProgram.get().shutdown();
        DescriptorHeapTerrainResources.get().shutdown();
        RendererDomainRegistry.get().reset();
        setLifecycleState(RendererLifecycleState.UNCONFIGURED, "renderer shutdown complete");
    }

    public void shutdownNow() {
        setLifecycleState(RendererLifecycleState.SHUTTING_DOWN, "immediate renderer shutdown requested");
        MeshShaderTerrainProgram.get().shutdown();
        DescriptorHeapTerrainResources.get().shutdownNow();
        RendererDomainRegistry.get().reset();
        setLifecycleState(RendererLifecycleState.UNCONFIGURED, "renderer immediate shutdown complete");
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configured", this.lifecycleState.drawable());
        map.put("lifecycleState", this.lifecycleState.name());
        map.put("lifecycleReason", this.lifecycleReason);
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
        map.put("meshReplacementPreparedGpuCommandRefusals", this.meshReplacementPreparedGpuCommandRefusals.sum());
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

    private void setLifecycleState(RendererLifecycleState lifecycleState, String reason) {
        this.lifecycleState = lifecycleState;
        this.lifecycleReason = reason;
    }
}
