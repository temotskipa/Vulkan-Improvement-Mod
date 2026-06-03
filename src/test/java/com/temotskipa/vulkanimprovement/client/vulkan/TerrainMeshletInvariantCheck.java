package com.temotskipa.vulkanimprovement.client.vulkan;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;

public final class TerrainMeshletInvariantCheck {
    private TerrainMeshletInvariantCheck() {
    }

    public static void main(String[] args) {
        checkNonIndexedMeshletsPartitionVertices();
        checkIndexedMeshletsPartitionTriangles();
        checkLayerMaterialId();
    }

    private static void checkNonIndexedMeshletsPartitionVertices() {
        long sectionNode = SectionPos.asLong(2, 7, -3);
        ChunkSectionLayer layer = ChunkSectionLayer.values()[0];
        int estimatedVertices = TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET * 2 + 5;
        int meshletCount = 3;
        int coveredVertices = 0;

        for (int i = 0; i < meshletCount; i++) {
            TerrainMeshlet meshlet = TerrainMeshlet.from(sectionNode, layer, i, meshletCount, estimatedVertices, 96);
            require(meshlet.sectionNode() == sectionNode, "section identity must survive meshlet partitioning");
            require(meshlet.sectionX() == 2 && meshlet.sectionY() == 7 && meshlet.sectionZ() == -3, "section coordinates must decode from section node");
            require(meshlet.layer() == layer, "meshlet must keep its chunk layer");
            require(meshlet.firstVertex() == coveredVertices, "non-indexed meshlets must be contiguous");
            require(meshlet.vertexCount() > 0, "non-indexed meshlets must not be empty");
            require(meshlet.vertexCount() <= TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET, "non-indexed meshlet exceeds target vertex budget");
            coveredVertices += meshlet.vertexCount();
        }

        require(coveredVertices == estimatedVertices, "non-indexed meshlets must cover every estimated vertex exactly once");
    }

    private static void checkIndexedMeshletsPartitionTriangles() {
        long sectionNode = SectionPos.asLong(-4, 1, 9);
        ChunkSectionLayer layer = ChunkSectionLayer.values()[0];
        int totalTriangles = TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET * 2 + 3;
        int indexCount = totalTriangles * 3;
        int meshletCount = 3;
        int coveredIndices = 0;

        for (int i = 0; i < meshletCount; i++) {
            TerrainMeshlet meshlet = TerrainMeshlet.fromIndexedTriangles(sectionNode, layer, i, meshletCount, indexCount);
            require(meshlet.sectionNode() == sectionNode, "indexed section identity must survive meshlet partitioning");
            require(meshlet.layer() == layer, "indexed meshlet must keep its chunk layer");
            require(meshlet.firstVertex() == 0, "indexed meshlets address payload through index records");
            require(meshlet.vertexCount() == meshlet.indexCount(), "indexed mesh shader currently emits one output vertex per index");
            require(meshlet.firstIndex() == coveredIndices, "indexed meshlets must be contiguous");
            require(meshlet.indexCount() % 3 == 0, "indexed meshlet must preserve triangle alignment");
            require(meshlet.indexCount() <= TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET * 3, "indexed meshlet exceeds triangle budget");
            coveredIndices += meshlet.indexCount();
        }

        require(coveredIndices == indexCount, "indexed meshlets must cover every normalized index exactly once");
    }

    private static void checkLayerMaterialId() {
        long sectionNode = SectionPos.asLong(0, 0, 0);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            TerrainMeshlet nonIndexed = TerrainMeshlet.from(sectionNode, layer, 0, 1, 4, 6);
            TerrainMeshlet indexed = TerrainMeshlet.fromIndexedTriangles(sectionNode, layer, 0, 1, 6);

            require(nonIndexed.materialId() == TerrainMaterialClassifier.materialIdForLayer(layer), "non-indexed meshlets must use their layer material");
            require(indexed.materialId() == TerrainMaterialClassifier.materialIdForLayer(layer), "indexed meshlets must use their layer material");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
