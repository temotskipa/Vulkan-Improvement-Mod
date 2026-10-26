package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.util.LinkedHashMap;
import java.util.Map;

record TerrainMeshTaskCommandStats(int uploaded, int dropped, int offset, int capacity, int nextOffset, boolean wrapped, boolean wrapBlocked, String reason, boolean gpuGenerated) {
    static TerrainMeshTaskCommandStats empty() {
        return new TerrainMeshTaskCommandStats(0, 0, 0, 0, 0, false, false, "", false);
    }
    
    static TerrainMeshTaskCommandStats unavailable(int dropped, int capacity, int nextOffset, String reason, boolean wrapBlocked, boolean gpuGenerated) {
        return new TerrainMeshTaskCommandStats(0, dropped, 0, capacity, nextOffset, false, wrapBlocked, reason, gpuGenerated);
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
        map.put("gpuGenerated", this.gpuGenerated);
        map.put("commandStride", TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE);
        return map;
    }
}