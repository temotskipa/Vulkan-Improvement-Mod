package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.util.LinkedHashMap;
import java.util.Map;

record TerrainVisibleMeshletStats(int uploaded, int dropped, int offset, int capacity, int nextOffset, boolean wrapped, boolean wrapBlocked, String reason) {
    static TerrainVisibleMeshletStats empty() {
        return new TerrainVisibleMeshletStats(0, 0, 0, 0, 0, false, false, "");
    }
    
    static TerrainVisibleMeshletStats fromAllocation(int uploaded, int dropped, VisibleMeshletAllocation allocation) {
        return new TerrainVisibleMeshletStats(uploaded, dropped, allocation.offset(), allocation.capacity(), allocation.nextOffset(), allocation.wrapped(), allocation.wrapBlocked(), allocation.reason());
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uploaded", this.uploaded);
        map.put("dropped", this.dropped);
        map.put("offset", this.offset);
        map.put("capacity", this.capacity);
        map.put("nextOffset", this.nextOffset);
        map.put("wrapped", this.wrapped);
        map.put("wrapBlocked", this.wrapBlocked);
        map.put("reason", this.reason);
        map.put("recordStride", TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
        return map;
    }
}