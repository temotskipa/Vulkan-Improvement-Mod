package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.List;

record SectionLayerCapture(int vertexBytes, int indexBytes, int estimatedVertices, int estimatedMeshlets, boolean customIndexBuffer, int indexElementBytes, List<TerrainMeshlet> meshlets, SectionMeshLayerBuffers buffers) {
}