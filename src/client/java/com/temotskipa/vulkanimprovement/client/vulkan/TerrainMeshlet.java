package com.temotskipa.vulkanimprovement.client.vulkan;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;

import java.util.LinkedHashMap;
import java.util.Map;

public record TerrainMeshlet(
        long sectionNode,
        int sectionX,
        int sectionY,
        int sectionZ,
        ChunkSectionLayer layer,
        int firstVertex,
        int vertexCount,
        int firstIndex,
        int indexCount
) {
    static TerrainMeshlet from(long sectionNode, ChunkSectionLayer layer, int meshletIndex, int meshletCount, int estimatedVertexCount, int indexCount) {
        int firstVertex = meshletIndex * SectionMeshletStore.TARGET_VERTICES_PER_MESHLET;
        int vertexCount = meshletIndex == meshletCount - 1
                ? Math.max(0, estimatedVertexCount - firstVertex)
                : Math.min(SectionMeshletStore.TARGET_VERTICES_PER_MESHLET, estimatedVertexCount - firstVertex);
        int firstIndex = indexCount == 0 ? 0 : meshletIndex * Math.max(1, indexCount / meshletCount);
        int count = indexCount == 0 ? 0 : (meshletIndex == meshletCount - 1 ? indexCount - firstIndex : Math.max(1, indexCount / meshletCount));
        return new TerrainMeshlet(
                sectionNode,
                SectionPos.x(sectionNode),
                SectionPos.y(sectionNode),
                SectionPos.z(sectionNode),
                layer,
                firstVertex,
                vertexCount,
                firstIndex,
                count
        );
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sectionNode", this.sectionNode);
        map.put("sectionX", this.sectionX);
        map.put("sectionY", this.sectionY);
        map.put("sectionZ", this.sectionZ);
        map.put("layer", this.layer.label());
        map.put("firstVertex", this.firstVertex);
        map.put("vertexCount", this.vertexCount);
        map.put("firstIndex", this.firstIndex);
        map.put("indexCount", this.indexCount);
        return map;
    }
}
