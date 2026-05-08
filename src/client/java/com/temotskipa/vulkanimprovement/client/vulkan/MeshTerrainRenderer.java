package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public final class MeshTerrainRenderer {
    private static final MeshTerrainRenderer INSTANCE = new MeshTerrainRenderer();
    private static final int MAX_TASKS_PER_DISPATCH = 65535;
    
    private final LongAdder terrainGroupCalls = new LongAdder();
    private final LongAdder capturedSections = new LongAdder();
    private final LongAdder releasedSections = new LongAdder();
    private final LongAdder capturedVertexBytes = new LongAdder();
    private final LongAdder capturedIndexBytes = new LongAdder();
    private final LongAdder estimatedMeshlets = new LongAdder();
    private final LongAdder meshReplacementDrawCalls = new LongAdder();
    private final LongAdder meshReplacementTasks = new LongAdder();
    private final LongAdder meshReplacementLayerFilteredDrawCalls = new LongAdder();
    private final LongAdder meshReplacementWholeSetDrawCalls = new LongAdder();
    private final LongAdder meshReplacementDuplicateCancellations = new LongAdder();
    private final LongAdder meshReplacementRefusals = new LongAdder();
    private final LongAdder meshReplacementCustomIndexFallbacks = new LongAdder();
    private final LongAdder[] meshReplacementCustomIndexFallbacksByLayer = createLayerCounters();
    private final LongAdder[] meshReplacementDrawCallsByLayer = createLayerCounters();
    private volatile boolean configured;
    
    private MeshTerrainRenderer() {
    }
    
    public static MeshTerrainRenderer get() {
        return INSTANCE;
    }
    
    public void observeTerrainGroup(ChunkSectionLayerGroup group) {
        this.terrainGroupCalls.increment();
        DescriptorHeapTerrainResources.get().uploadDirtyTerrainData();
    }
    
    public void recordSectionCapture(int vertexBytes, int indexBytes, int meshlets) {
        this.capturedSections.increment();
        this.capturedVertexBytes.add(vertexBytes);
        this.capturedIndexBytes.add(indexBytes);
        this.estimatedMeshlets.add(meshlets);
    }
    
    public void recordSectionRelease() {
        this.releasedSections.increment();
    }
    
    public boolean tryDrawMeshTerrain(VkCommandBuffer commandBuffer, @Nullable VulkanRenderPipeline vanillaPipeline, boolean hasDepth) {
        if (!TerrainRendererDebugConfig.REPLACE_VANILLA_TERRAIN || !TerrainRenderContext.isTerrainPass()) {
            return false;
        }
        DescriptorHeapTerrainResources terrainResources = DescriptorHeapTerrainResources.get();
        int totalMeshletCount = terrainResources.meshletsUploaded();
        if (!this.configured || vanillaPipeline == null || !terrainResources.hasGpuTerrainData() || !MeshShaderTerrainProgram.get().ready() || totalMeshletCount <= 0) {
            this.meshReplacementRefusals.increment();
            return false;
        }

        int layerOrdinal = TerrainRenderContext.currentLayerOrdinal();
        int meshletOffset = layerOrdinal < 0 ? 0 : terrainResources.meshletOffsetForLayer(layerOrdinal);
        int meshletCount = layerOrdinal < 0 ? totalMeshletCount : terrainResources.meshletCountForLayer(layerOrdinal);
        if (meshletCount <= 0) {
            this.meshReplacementRefusals.increment();
            return false;
        }
        if (layerOrdinal >= 0 && terrainResources.customIndexMeshletCountForLayer(layerOrdinal) > 0) {
            this.meshReplacementCustomIndexFallbacks.increment();
            if (layerOrdinal < this.meshReplacementCustomIndexFallbacksByLayer.length) {
                this.meshReplacementCustomIndexFallbacksByLayer[layerOrdinal].increment();
            }
            return false;
        }
        if (!TerrainRenderContext.markDispatchRequired(layerOrdinal)) {
            this.meshReplacementDuplicateCancellations.increment();
            return true;
        }

        int taskCount = Math.min(meshletCount, MAX_TASKS_PER_DISPATCH);
        if (MeshShaderTerrainProgram.get().drawTerrain(commandBuffer, vanillaPipeline, hasDepth, meshletOffset, layerOrdinal, taskCount, terrainResources.meshletHeaderAddress(), terrainResources.meshletVertexPayloadAddress(), terrainResources.meshletIndexPayloadAddress())) {
            this.meshReplacementDrawCalls.increment();
            this.meshReplacementTasks.add(taskCount);
            if (layerOrdinal < 0) {
                this.meshReplacementWholeSetDrawCalls.increment();
            } else {
                this.meshReplacementLayerFilteredDrawCalls.increment();
                if (layerOrdinal < this.meshReplacementDrawCallsByLayer.length) {
                    this.meshReplacementDrawCallsByLayer[layerOrdinal].increment();
                }
            }
            return true;
        }
        
        this.meshReplacementRefusals.increment();
        return false;
    }
    
    public void configure(VulkanDevice device, VulkanImprovementCapabilities.Snapshot capabilities) {
        DescriptorHeapTerrainResources.get().configure(device, capabilities);
        MeshShaderTerrainProgram.get().configure(device);
        PresentPacingController.get().configure(capabilities);
        FragmentShadingRateController.get().configure(capabilities);
        this.configured = true;
    }
    
    public void shutdown() {
        MeshShaderTerrainProgram.get().shutdown();
        DescriptorHeapTerrainResources.get().shutdown();
        this.configured = false;
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configured", this.configured);
        map.put("mode", TerrainRendererDebugConfig.rendererMode());
        map.put("terrainGroupCalls", this.terrainGroupCalls.sum());
        map.put("capturedSections", this.capturedSections.sum());
        map.put("releasedSections", this.releasedSections.sum());
        map.put("capturedVertexBytes", this.capturedVertexBytes.sum());
        map.put("capturedIndexBytes", this.capturedIndexBytes.sum());
        map.put("estimatedMeshlets", this.estimatedMeshlets.sum());
        map.put("meshReplacementDrawCalls", this.meshReplacementDrawCalls.sum());
        map.put("meshReplacementTasks", this.meshReplacementTasks.sum());
        map.put("meshReplacementLayerFilteredDrawCalls", this.meshReplacementLayerFilteredDrawCalls.sum());
        map.put("meshReplacementWholeSetDrawCalls", this.meshReplacementWholeSetDrawCalls.sum());
        map.put("meshReplacementDrawCallsByLayer", layerCounterMap(this.meshReplacementDrawCallsByLayer));
        map.put("meshReplacementCustomIndexFallbacks", this.meshReplacementCustomIndexFallbacks.sum());
        map.put("meshReplacementCustomIndexFallbacksByLayer", layerCounterMap(this.meshReplacementCustomIndexFallbacksByLayer));
        map.put("meshReplacementDuplicateCancellations", this.meshReplacementDuplicateCancellations.sum());
        map.put("meshReplacementRefusals", this.meshReplacementRefusals.sum());
        map.put("gpuTerrainDataReady", DescriptorHeapTerrainResources.get().hasGpuTerrainData());
        map.put("meshShaderProgram", MeshShaderTerrainProgram.get().asMap());
        map.put("sectionStore", SectionMeshletStore.asMap());
        map.put("descriptorHeap", DescriptorHeapTerrainResources.get().asMap());
        map.put("presentPacing", PresentPacingController.get().asMap());
        map.put("fragmentShadingRate", FragmentShadingRateController.get().asMap());
        return map;
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
}
