package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.lang.foreign.MemorySegment;
import java.util.List;

record BuiltQuadMeshlets(MemorySegment vertices, List<TerrainMeshlet> meshlets) {
}