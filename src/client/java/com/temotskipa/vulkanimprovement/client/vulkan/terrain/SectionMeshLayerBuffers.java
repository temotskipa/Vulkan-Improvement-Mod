package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.lang.foreign.MemorySegment;

record SectionMeshLayerBuffers(MemorySegment vertices, MemorySegment indices) {
    int vertexBytes() {
        return SectionMeshletStore.bytes(this.vertices);
    }
    
    int indexBytes() {
        return SectionMeshletStore.bytes(this.indices);
    }
}