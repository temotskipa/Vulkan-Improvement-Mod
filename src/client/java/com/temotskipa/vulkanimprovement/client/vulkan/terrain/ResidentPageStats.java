package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.Arrays;

record ResidentPageStats(int livePages, int pendingPages, int meshlets, int[] meshletsByLayer, int[] customIndexMeshletsByLayer, long pagesAllocated, long pagesFreed, long allocationRefusals, long slotsUsed, long slotCapacity, long meshletsUsed, long meshletCapacity, int meshletFreeRanges, long vertexBytesUsed, long vertexByteCapacity, long largestFreeVertexRange, int vertexFreeRanges, long indexBytesUsed, long indexByteCapacity, long largestFreeIndexRange, int indexFreeRanges) {
	/**
	 * As the record would print itself, with the contents of the per-layer counts.
	 */
	String describe() {
		return "livePages=" + this.livePages + ", pendingPages=" + this.pendingPages + ", meshlets=" + this.meshlets + ", meshletsByLayer=" + Arrays.toString(this.meshletsByLayer) + ", customIndexMeshletsByLayer=" + Arrays.toString(this.customIndexMeshletsByLayer) + ", pagesAllocated=" + this.pagesAllocated + ", pagesFreed=" + this.pagesFreed + ", allocationRefusals=" + this.allocationRefusals + ", slotsUsed=" + this.slotsUsed + ", slotCapacity=" + this.slotCapacity + ", meshletsUsed=" + this.meshletsUsed + ", meshletCapacity=" + this.meshletCapacity + ", meshletFreeRanges=" + this.meshletFreeRanges + ", vertexBytesUsed=" + this.vertexBytesUsed + ", vertexByteCapacity=" + this.vertexByteCapacity + ", largestFreeVertexRange=" + this.largestFreeVertexRange + ", vertexFreeRanges=" + this.vertexFreeRanges + ", indexBytesUsed=" + this.indexBytesUsed + ", indexByteCapacity=" + this.indexByteCapacity + ", largestFreeIndexRange=" + this.largestFreeIndexRange + ", indexFreeRanges=" + this.indexFreeRanges;
	}
}
