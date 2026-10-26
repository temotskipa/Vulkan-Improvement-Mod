package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.util.LinkedHashMap;
import java.util.Map;

record TerrainWorkQueueStats(int uploaded, int dropped, int offset, int capacity, int nextOffset, boolean wrapped, boolean wrapBlocked, String reason) {
    static TerrainWorkQueueStats empty() {
        return new TerrainWorkQueueStats(0, 0, 0, 0, 0, false, false, "");
    }
    
    static TerrainWorkQueueStats unavailable(int dropped, int capacity, int nextOffset, String reason, boolean wrapBlocked) {
        return new TerrainWorkQueueStats(0, dropped, 0, capacity, nextOffset, false, wrapBlocked, reason);
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
        map.put("recordStride", TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE);
        return map;
    }
}