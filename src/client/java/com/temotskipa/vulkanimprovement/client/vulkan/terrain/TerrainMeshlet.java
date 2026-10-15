package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One meshlet of a section layer: which vertices or indices it draws, the box around them in the
 * section's own coordinates, and which way all of its faces point, if they all point one way.
 */
public record TerrainMeshlet(long sectionNode, int sectionX, int sectionY, int sectionZ, ChunkSectionLayer layer, int firstVertex, int vertexCount, int firstIndex, int indexCount, int materialId, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int facing) {
    // A block model may reach a block past its own; a meshlet whose box is not known is given one
    // that holds anything a section can draw.
    private static final float SECTION_LOW = -16.0F;
    private static final float SECTION_HIGH = 32.0F;
    
    static TerrainMeshlet from(long sectionNode, ChunkSectionLayer layer, int meshletIndex, int meshletCount, int estimatedVertexCount, int indexCount) {
        int firstVertex = meshletIndex * TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET;
        int vertexCount = meshletIndex == meshletCount - 1 ? Math.max(0, estimatedVertexCount - firstVertex) : Math.min(TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET, estimatedVertexCount - firstVertex);
        int firstIndex = indexCount == 0 ? 0 : meshletIndex * Math.max(1, indexCount / meshletCount);
        int count = indexCount == 0 ? 0 : (meshletIndex == meshletCount - 1 ? indexCount - firstIndex : Math.max(1, indexCount / meshletCount));
        return new TerrainMeshlet(sectionNode, SectionPos.x(sectionNode), SectionPos.y(sectionNode), SectionPos.z(sectionNode), layer, firstVertex, vertexCount, firstIndex, count, TerrainMaterialClassifier.materialIdForLayer(layer), SECTION_LOW, SECTION_LOW, SECTION_LOW, SECTION_HIGH, SECTION_HIGH, SECTION_HIGH, TerrainQuadMeshlets.FACING_ANY);
    }
    
    static TerrainMeshlet fromIndexedTriangles(long sectionNode, ChunkSectionLayer layer, int meshletIndex, int meshletCount, int indexCount) {
        int firstTriangle = meshletIndex * TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET;
        int totalTriangles = indexCount / 3;
        int triangleCount = meshletIndex == meshletCount - 1 ? Math.max(0, totalTriangles - firstTriangle) : Math.min(TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET, totalTriangles - firstTriangle);
        return new TerrainMeshlet(sectionNode, SectionPos.x(sectionNode), SectionPos.y(sectionNode), SectionPos.z(sectionNode), layer, 0, triangleCount * 3, firstTriangle * 3, triangleCount * 3, TerrainMaterialClassifier.materialIdForLayer(layer), SECTION_LOW, SECTION_LOW, SECTION_LOW, SECTION_HIGH, SECTION_HIGH, SECTION_HIGH, TerrainQuadMeshlets.FACING_ANY);
    }
    
    /**
     * A run of quads that all face {@code facing}, inside {@code bounds}: minimum x, y, z, then
     * maximum x, y, z.
     */
    static TerrainMeshlet quads(long sectionNode, ChunkSectionLayer layer, int firstVertex, int vertexCount, float[] bounds, int facing) {
        return new TerrainMeshlet(sectionNode, SectionPos.x(sectionNode), SectionPos.y(sectionNode), SectionPos.z(sectionNode), layer, firstVertex, vertexCount, 0, 0, TerrainMaterialClassifier.materialIdForLayer(layer), bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5], facing);
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
        map.put("materialId", this.materialId);
        map.put("bounds", new float[]{this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ});
        map.put("facing", this.facing);
        return map;
    }
}