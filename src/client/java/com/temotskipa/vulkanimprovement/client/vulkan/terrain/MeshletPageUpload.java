package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.lang.foreign.MemorySegment;

/**
 * What has to be copied to the GPU for one page: its payload, the headers of its meshlets and
 * its record in the page table.
 */
record MeshletPageUpload(ResidentTerrainPage page, MemorySegment vertices, MemorySegment indices, MemorySegment meshletHeaders, MemorySegment pageRecord) {
    long bytes() {
        return this.vertices.byteSize() + this.indices.byteSize() + this.meshletHeaders.byteSize() + this.pageRecord.byteSize();
    }
}