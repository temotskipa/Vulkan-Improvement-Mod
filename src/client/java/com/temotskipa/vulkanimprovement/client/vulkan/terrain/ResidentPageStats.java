package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record ResidentPageStats(int livePages, int pendingPages, int meshlets, int[] meshletsByLayer, int[] customIndexMeshletsByLayer, long pagesAllocated, long pagesFreed, long allocationRefusals, long slotsUsed, long slotCapacity, long meshletsUsed, long meshletCapacity, int meshletFreeRanges, long vertexBytesUsed, long vertexByteCapacity, long largestFreeVertexRange, int vertexFreeRanges, long indexBytesUsed, long indexByteCapacity, long largestFreeIndexRange, int indexFreeRanges) {
}