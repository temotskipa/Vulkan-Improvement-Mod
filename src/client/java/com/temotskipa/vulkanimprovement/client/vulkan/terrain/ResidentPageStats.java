package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

record ResidentPageStats(int livePages, int pendingPages, int meshlets, int[] meshletsByLayer, int[] customIndexMeshletsByLayer, long pagesAllocated, long pagesFreed, long allocationRefusals, long slotsUsed, long slotCapacity, long meshletsUsed, long meshletCapacity, int meshletFreeRanges, long vertexBytesUsed, long vertexByteCapacity, long largestFreeVertexRange, int vertexFreeRanges, long indexBytesUsed, long indexByteCapacity, long largestFreeIndexRange, int indexFreeRanges) {
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("livePages", this.livePages);
        map.put("pendingPages", this.pendingPages);
        map.put("meshlets", this.meshlets);
        map.put("pagesAllocated", this.pagesAllocated);
        map.put("pagesFreed", this.pagesFreed);
        map.put("allocationRefusals", this.allocationRefusals);
        map.put("pageSlots", Map.of("used", this.slotsUsed, "capacity", this.slotCapacity));
        map.put("meshletHeaders", Map.of("used", this.meshletsUsed, "capacity", this.meshletCapacity, "freeRanges", this.meshletFreeRanges));
        map.put("vertexPayload", Map.of("usedBytes", this.vertexBytesUsed, "capacityBytes", this.vertexByteCapacity, "largestFreeRangeBytes", this.largestFreeVertexRange, "freeRanges", this.vertexFreeRanges));
        map.put("indexPayload", Map.of("usedBytes", this.indexBytesUsed, "capacityBytes", this.indexByteCapacity, "largestFreeRangeBytes", this.largestFreeIndexRange, "freeRanges", this.indexFreeRanges));
        return map;
    }
}