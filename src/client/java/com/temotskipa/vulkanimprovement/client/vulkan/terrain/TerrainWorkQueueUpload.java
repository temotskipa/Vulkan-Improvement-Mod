package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public record TerrainWorkQueueUpload(int offset, int count, long address, int dropped, boolean ready) {
    static TerrainWorkQueueUpload unavailable(int dropped) {
        return new TerrainWorkQueueUpload(0, 0, 0L, dropped, false);
    }
}