package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.util.LinkedHashMap;
import java.util.Map;

final class TerrainMeshTaskCommandLayout {
    private TerrainMeshTaskCommandLayout() {
    }
    
    static long commandOffset(int commandIndex) {
        if (commandIndex < 0) {
            throw new IllegalArgumentException("commandIndex must be non-negative");
        }
        return (long) commandIndex * TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE;
    }
    
    static long bytesForCapacity() {
        if (TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY < 0) {
            throw new IllegalArgumentException("commandCapacity must be non-negative");
        }
        return (long) TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY * TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE;
    }
    
    static Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("commandStride", TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE);
        map.put("commandCapacity", TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY);
        map.put("commandPushConstantBytes", TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES);
        map.put("bytes", bytesForCapacity());
        map.put("commandFields", Map.of("groupCountX", 0, "groupCountY", 4, "groupCountZ", 8));
        map.put("pushConstantFields", Map.of("pageTable", 0, "visiblePages", 8, "meshTaskCommands", 16, "counters", 24, "inputOffset", 32, "outputOffset", 36, "pageCount", 40, "commandIndex", 44, "camera", 64));
        map.put("visibilityWorkgroupSize", TerrainGpuLayout.VISIBILITY_WORKGROUP_SIZE);
        return map;
    }
}