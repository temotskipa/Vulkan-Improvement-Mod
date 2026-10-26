package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public record TerrainUploadStats(int sectionsUploaded, int layersUploaded, int meshletsUploaded, int sectionsDropped, long vertexBytesUploaded, long indexBytesUploaded, int[] meshletOffsetsByLayer, int[] meshletCountsByLayer, int[] customIndexMeshletCountsByLayer, long uploadCount, long lastUploadCpuNanos, long totalUploadCpuNanos) {
    public TerrainUploadStats {
        meshletOffsetsByLayer = meshletOffsetsByLayer.clone();
        meshletCountsByLayer = meshletCountsByLayer.clone();
        customIndexMeshletCountsByLayer = customIndexMeshletCountsByLayer.clone();
    }
    
    static TerrainUploadStats empty() {
        return new TerrainUploadStats(0, 0, 0, 0, 0L, 0L, emptyLayerCounters(), emptyLayerCounters(), emptyLayerCounters(), 0L, 0L, 0L);
    }
    
    private static int[] emptyLayerCounters() {
        return new int[ChunkSectionLayer.values().length];
    }
    
    private static long nanosToMicros(long nanos) {
        return nanos / 1_000L;
    }
    
    TerrainUploadStats nextUpload(int sections, int layers, int meshlets, int droppedSections, long vertexBytesUploaded, long indexBytesUploaded, int[] meshletOffsetsByLayer, int[] meshletCountsByLayer, int[] customIndexMeshletCountsByLayer) {
        return new TerrainUploadStats(sections, layers, meshlets, droppedSections, vertexBytesUploaded, indexBytesUploaded, meshletOffsetsByLayer, meshletCountsByLayer, customIndexMeshletCountsByLayer, this.uploadCount + 1L, 0L, this.totalUploadCpuNanos);
    }
    
    TerrainUploadStats withCpuNanos(long cpuNanos) {
        long measuredNanos = Math.max(cpuNanos, 0L);
        return new TerrainUploadStats(this.sectionsUploaded, this.layersUploaded, this.meshletsUploaded, this.sectionsDropped, this.vertexBytesUploaded, this.indexBytesUploaded, this.meshletOffsetsByLayer, this.meshletCountsByLayer, this.customIndexMeshletCountsByLayer, this.uploadCount, measuredNanos, this.totalUploadCpuNanos + measuredNanos);
    }
    
    /**
     * The state after a frame's page uploads. A page lives where the allocator put it, so a
     * layer's meshlets are not one range and the per-layer offsets stay zero.
     */
    TerrainUploadStats afterPageUpload(int liveSections, ResidentPageStats pages, long cpuNanos) {
        int layerCount = this.meshletCountsByLayer.length;
        return nextUpload(liveSections, pages.livePages(), pages.meshlets(), pages.pendingPages(), pages.vertexBytesUsed(), pages.indexBytesUsed(), new int[layerCount], Arrays.copyOf(pages.meshletsByLayer(), layerCount), Arrays.copyOf(pages.customIndexMeshletsByLayer(), layerCount)).withCpuNanos(cpuNanos);
    }
    
    int meshletCountForLayer(int layerOrdinal) {
        if (layerOrdinal < 0 || layerOrdinal >= this.meshletCountsByLayer.length) {
            return this.meshletsUploaded;
        }
        return this.meshletCountsByLayer[layerOrdinal];
    }
    
    int customIndexMeshletCountForLayer(int layerOrdinal) {
        if (layerOrdinal < 0 || layerOrdinal >= this.customIndexMeshletCountsByLayer.length) {
            return 0;
        }
        return this.customIndexMeshletCountsByLayer[layerOrdinal];
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sectionsUploaded", this.sectionsUploaded);
        map.put("layersUploaded", this.layersUploaded);
        map.put("meshletsUploaded", this.meshletsUploaded);
        map.put("sectionsDropped", this.sectionsDropped);
        map.put("vertexBytesUploaded", this.vertexBytesUploaded);
        map.put("indexBytesUploaded", this.indexBytesUploaded);
        map.put("meshletRangesByLayer", meshletRangesByLayer());
        map.put("customIndexMeshletsByLayer", customIndexMeshletsByLayer());
        map.put("uploadCount", this.uploadCount);
        map.put("lastUploadCpuMicros", nanosToMicros(this.lastUploadCpuNanos));
        map.put("totalUploadCpuMicros", nanosToMicros(this.totalUploadCpuNanos));
        map.put("averageUploadCpuMicros", this.uploadCount == 0L ? 0L : nanosToMicros(this.totalUploadCpuNanos / this.uploadCount));
        return map;
    }
    
    private Map<String, Object> meshletRangesByLayer() {
        Map<String, Object> ranges = new LinkedHashMap<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            int ordinal = layer.ordinal();
            ranges.put(layer.label(), Map.of("offset", ordinal < this.meshletOffsetsByLayer.length ? this.meshletOffsetsByLayer[ordinal] : 0, "count", ordinal < this.meshletCountsByLayer.length ? this.meshletCountsByLayer[ordinal] : 0));
        }
        return ranges;
    }
    
    private Map<String, Object> customIndexMeshletsByLayer() {
        Map<String, Object> ranges = new LinkedHashMap<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            int ordinal = layer.ordinal();
            ranges.put(layer.label(), ordinal < this.customIndexMeshletCountsByLayer.length ? this.customIndexMeshletCountsByLayer[ordinal] : 0);
        }
        return ranges;
    }
}