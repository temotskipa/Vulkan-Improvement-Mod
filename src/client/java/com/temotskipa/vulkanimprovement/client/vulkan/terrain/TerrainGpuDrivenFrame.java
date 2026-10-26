package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

/**
 * Where a GPU-driven frame's data went in the visible list: the list's address, the record the
 * frame's counters begin at, followed by the sections in view, and the record the task records
 * begin at, one layer after another.
 */
public record TerrainGpuDrivenFrame(long address, int frameRecord, int firstTaskRecord, boolean ready) {
    static TerrainGpuDrivenFrame unavailable() {
        return new TerrainGpuDrivenFrame(0L, 0, 0, false);
    }
}