package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.util.Arrays;

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
	
	/**
	 * The state after a frame's page uploads. A page lives where the allocator put it, so a
	 * layer's meshlets are not one range and the per-layer offsets stay zero.
	 */
	TerrainUploadStats afterPageUpload(int liveSections, ResidentPageStats pages, long cpuNanos) {
		int layerCount = this.meshletCountsByLayer.length;
		return nextUpload(liveSections, pages.livePages(), pages.meshlets(), pages.pendingPages(), pages.vertexBytesUsed(), pages.indexBytesUsed(), new int[layerCount], Arrays.copyOf(pages.meshletsByLayer(), layerCount), Arrays.copyOf(pages.customIndexMeshletsByLayer(), layerCount)).withCpuNanos(cpuNanos);
	}
	
	TerrainUploadStats withCpuNanos(long cpuNanos) {
		long measuredNanos = Math.max(cpuNanos, 0L);
		return new TerrainUploadStats(this.sectionsUploaded, this.layersUploaded, this.meshletsUploaded, this.sectionsDropped, this.vertexBytesUploaded, this.indexBytesUploaded, this.meshletOffsetsByLayer, this.meshletCountsByLayer, this.customIndexMeshletCountsByLayer, this.uploadCount, measuredNanos, this.totalUploadCpuNanos + measuredNanos);
	}
	
	TerrainUploadStats nextUpload(int sections, int layers, int meshlets, int droppedSections, long vertexBytesUploaded, long indexBytesUploaded, int[] meshletOffsetsByLayer, int[] meshletCountsByLayer, int[] customIndexMeshletCountsByLayer) {
		return new TerrainUploadStats(sections, layers, meshlets, droppedSections, vertexBytesUploaded, indexBytesUploaded, meshletOffsetsByLayer, meshletCountsByLayer, customIndexMeshletCountsByLayer, this.uploadCount + 1L, 0L, this.totalUploadCpuNanos);
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
	
	String describe() {
		return "sections=" + this.sectionsUploaded + ", layers=" + this.layersUploaded + ", meshlets=" + this.meshletsUploaded + ", sectionsDropped=" + this.sectionsDropped + ", vertexBytes=" + this.vertexBytesUploaded + ", indexBytes=" + this.indexBytesUploaded + ", meshletOffsetsByLayer=" + Arrays.toString(this.meshletOffsetsByLayer) + ", meshletCountsByLayer=" + Arrays.toString(this.meshletCountsByLayer) + ", customIndexMeshletsByLayer=" + Arrays.toString(this.customIndexMeshletCountsByLayer) + ", uploads=" + this.uploadCount + ", lastUploadCpuMicros=" + nanosToMicros(this.lastUploadCpuNanos) + ", totalUploadCpuMicros=" + nanosToMicros(this.totalUploadCpuNanos);
	}
	
	private static long nanosToMicros(long nanos) {
		return nanos / 1_000L;
	}
}