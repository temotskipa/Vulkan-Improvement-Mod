package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public record TerrainVisibleMeshletUpload(int offset, int count, long address, int dropped, boolean ready) {
    static TerrainVisibleMeshletUpload unavailable(int dropped) {
        return new TerrainVisibleMeshletUpload(0, 0, 0L, dropped, false);
    }
}