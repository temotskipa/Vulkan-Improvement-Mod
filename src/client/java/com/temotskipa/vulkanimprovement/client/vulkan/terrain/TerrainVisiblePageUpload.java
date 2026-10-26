package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

/**
 * @param inputOffset  where the frame's visible pages of a layer start in the visible list
 * @param outputOffset where the GPU writes the pages it keeps
 * @param count        how many pages were listed; no more can be kept
 */
public record TerrainVisiblePageUpload(int inputOffset, int outputOffset, int count, long address, boolean ready) {
    static TerrainVisiblePageUpload unavailable() {
        return new TerrainVisiblePageUpload(0, 0, 0, 0L, false);
    }
}