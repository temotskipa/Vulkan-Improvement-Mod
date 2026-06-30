package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TerrainMeshletInvariantCheck {
    private TerrainMeshletInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) {
        checkNonIndexedMeshletsPartitionVertices();
        checkIndexedMeshletsPartitionTriangles();
        checkLayerMaterialId();
        checkCaptureValidationAcceptsValidQuadPayload();
        checkCaptureValidationRejectsBadPayloads();
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

    private static void checkCaptureValidationAcceptsValidQuadPayload() {
        ChunkSectionLayer layer = ChunkSectionLayer.values()[0];
        ByteBuffer vertices = blockVertices(4);
        SectionMeshletStore.CaptureValidation nonIndexed = SectionMeshletStore.validateCapture(
                layer,
                vertices.duplicate(),
                ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN),
                vertices.remaining(),
                0,
                6,
                false,
                0
        );
        require(nonIndexed.valid(), "valid non-indexed quad payload must be accepted: " + nonIndexed.reason());

        ByteBuffer indices = normalizedIndices(0, 1, 2, 2, 3, 0);
        SectionMeshletStore.CaptureValidation indexed = SectionMeshletStore.validateCapture(
                layer,
                vertices.duplicate(),
                indices.duplicate().order(ByteOrder.LITTLE_ENDIAN),
                vertices.remaining(),
                Short.BYTES * 6,
                6,
                true,
                TerrainGpuLayout.NORMALIZED_INDEX_STRIDE
        );
        require(indexed.valid(), "valid normalized custom-index payload must be accepted: " + indexed.reason());
    }

    private static void checkCaptureValidationRejectsBadPayloads() {
        ChunkSectionLayer layer = ChunkSectionLayer.values()[0];
        ByteBuffer vertices = blockVertices(4);

        SectionMeshletStore.CaptureValidation misaligned = SectionMeshletStore.validateCapture(
                layer,
                vertices.duplicate(),
                ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN),
                vertices.remaining() - 1,
                0,
                6,
                false,
                0
        );
        require(!misaligned.valid(), "misaligned vertex payload must be rejected");

        SectionMeshletStore.CaptureValidation vertexMismatch = SectionMeshletStore.validateCapture(
                layer,
                blockVertices(3),
                ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN),
                TerrainGpuLayout.BLOCK_VERTEX_STRIDE * 3,
                0,
                6,
                false,
                0
        );
        require(!vertexMismatch.valid(), "non-indexed vertex/index mismatch must be rejected");

        SectionMeshletStore.CaptureValidation badIndex = SectionMeshletStore.validateCapture(
                layer,
                vertices.duplicate(),
                normalizedIndices(0, 1, 7).order(ByteOrder.LITTLE_ENDIAN),
                vertices.remaining(),
                Short.BYTES * 3,
                3,
                true,
                TerrainGpuLayout.NORMALIZED_INDEX_STRIDE
        );
        require(!badIndex.valid(), "out-of-range custom index must be rejected");

        ByteBuffer nonFinite = blockVertices(4);
        nonFinite.putFloat(0, Float.NaN);
        SectionMeshletStore.CaptureValidation badVertex = SectionMeshletStore.validateCapture(
                layer,
                nonFinite.duplicate(),
                ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN),
                nonFinite.remaining(),
                0,
                6,
                false,
                0
        );
        require(!badVertex.valid(), "non-finite vertex payload must be rejected");
    }

    private static ByteBuffer blockVertices(int vertexCount) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * TerrainGpuLayout.BLOCK_VERTEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            buffer.putFloat(vertex & 1);
            buffer.putFloat(vertex >> 1);
            buffer.putFloat(0.0F);
            buffer.putInt(0xFFFFFFFF);
            buffer.putFloat(vertex & 1);
            buffer.putFloat(vertex >> 1);
            buffer.putInt(0x00F000F0);
        }
        buffer.flip();
        return buffer;
    }

    private static ByteBuffer normalizedIndices(int... indices) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(indices.length * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        for (int index : indices) {
            buffer.putInt(index);
        }
        buffer.flip();
        return buffer;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
