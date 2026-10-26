package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public record TerrainMeshTaskCommandUpload(int commandIndex, int taskCount, long vkBuffer, long offsetBytes, int strideBytes, long address, int dropped, boolean ready, boolean gpuGenerated) {
    static TerrainMeshTaskCommandUpload unavailable(int dropped) {
        return new TerrainMeshTaskCommandUpload(0, 0, 0L, 0L, 0, 0L, dropped, false, false);
    }
}