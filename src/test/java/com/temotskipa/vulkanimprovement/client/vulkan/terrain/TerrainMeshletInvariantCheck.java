package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class TerrainMeshletInvariantCheck {
    private TerrainMeshletInvariantCheck() {
    }
    
    
    static void main(String[] args) {
        checkNonIndexedMeshletsPartitionVertices();
        checkIndexedMeshletsPartitionTriangles();
        checkLayerMaterialId();
        checkCaptureValidationAcceptsValidQuadPayload();
        checkCaptureValidationRejectsBadPayloads();
        checkQuadsAreGroupedByFacing();
        checkFacingFollowsWinding();
        checkLargeGroupsSplitIntoMeshlets();
    }

    /**
     * Quads are sorted into one group per facing, keeping their order inside a group, and every
     * meshlet holds quads of one group and the box around them.
     */
    private static void checkQuadsAreGroupedByFacing() {
        long sectionNode = SectionPos.asLong(1, 2, 3);
        ChunkSectionLayer layer = ChunkSectionLayer.values()[0];
        ByteBuffer vertices = ByteBuffer.allocateDirect(5 * 4 * TerrainGpuLayout.BLOCK_VERTEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        putQuad(vertices, UP_FACE, 1);
        putQuad(vertices, EAST_FACE, 2);
        putQuad(vertices, PLANT_FACE, 3);
        putQuad(vertices, UP_FACE, 4);
        putQuad(vertices, EAST_FACE, 5);
        vertices.flip();

        TerrainQuadMeshlets.Built built = TerrainQuadMeshlets.build(sectionNode, layer, vertices);
        require(built != null, "whole quads must be grouped");
        require(built.vertices().remaining() == vertices.remaining(), "grouping must keep every vertex");
        require(built.meshlets().size() == 3, "three facings must give three meshlets");
        require(built.meshlets().get(0).facing() == TerrainQuadMeshlets.FACING_EAST && built.meshlets().get(1).facing() == TerrainQuadMeshlets.FACING_UP && built.meshlets().get(2).facing() == TerrainQuadMeshlets.FACING_ANY, "groups must come in facing order");
        int[] expectedTags = {2, 5, 1, 4, 3};
        for (int quad = 0; quad < expectedTags.length; quad++) {
            require(built.vertices().getInt(quad * 4 * TerrainGpuLayout.BLOCK_VERTEX_STRIDE + 12) == expectedTags[quad], "quads must keep their order inside a facing group");
        }
        int covered = 0;
        for (TerrainMeshlet meshlet : built.meshlets()) {
            require(meshlet.firstVertex() == covered, "meshlets must cover the grouped vertices in order");
            covered += meshlet.vertexCount();
        }
        require(covered == 20, "meshlets must cover every vertex once");
        TerrainMeshlet east = built.meshlets().get(0);
        require(east.minX() == 1.0F && east.maxX() == 1.0F && east.minY() == 0.0F && east.maxY() == 1.0F, "a meshlet's box must hold its quads and no more");
        require(TerrainQuadMeshlets.build(sectionNode, layer, blockVertices(3)) == null, "vertices that are not whole quads must not be grouped");
    }

    /**
     * A face points to the side its corners go round anticlockwise when seen from, as Minecraft's
     * do; a face that does not lie across an axis is never dropped for facing.
     */
    private static void checkFacingFollowsWinding() {
        ByteBuffer vertices = ByteBuffer.allocateDirect(4 * 4 * TerrainGpuLayout.BLOCK_VERTEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        putQuad(vertices, UP_FACE, 0);
        putQuad(vertices, EAST_FACE, 0);
        putQuad(vertices, new float[][]{EAST_FACE[3], EAST_FACE[2], EAST_FACE[1], EAST_FACE[0]}, 0);
        putQuad(vertices, new float[][]{{0, 1, 0}, {0, 1, 0}, {0, 1, 1}, {1, 1, 1}}, 0);
        int quadBytes = 4 * TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
        require(TerrainQuadMeshlets.facingOf(vertices, 0) == TerrainQuadMeshlets.FACING_UP, "a top face must face up");
        require(TerrainQuadMeshlets.facingOf(vertices, quadBytes) == TerrainQuadMeshlets.FACING_EAST, "an east face must face east");
        require(TerrainQuadMeshlets.facingOf(vertices, 2 * quadBytes) == TerrainQuadMeshlets.FACING_WEST, "reversed corners must face the other way");
        require(TerrainQuadMeshlets.facingOf(vertices, 3 * quadBytes) == TerrainQuadMeshlets.FACING_UP, "a quad whose first triangle has no area must face the way its second does");
        ByteBuffer plant = ByteBuffer.allocateDirect(quadBytes).order(ByteOrder.LITTLE_ENDIAN);
        putQuad(plant, PLANT_FACE, 0);
        require(TerrainQuadMeshlets.facingOf(plant, 0) == TerrainQuadMeshlets.FACING_ANY, "a diagonal face must not be dropped for facing");
    }

    private static void checkLargeGroupsSplitIntoMeshlets() {
        int quads = TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET / 4 + 1;
        ByteBuffer vertices = ByteBuffer.allocateDirect(quads * 4 * TerrainGpuLayout.BLOCK_VERTEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        for (int quad = 0; quad < quads; quad++) {
            putQuad(vertices, UP_FACE, quad);
        }
        vertices.flip();
        TerrainQuadMeshlets.Built built = TerrainQuadMeshlets.build(SectionPos.asLong(0, 0, 0), ChunkSectionLayer.values()[0], vertices);
        require(built != null && built.meshlets().size() == 2, "a facing group larger than a meshlet must be split");
        require(built.meshlets().get(0).vertexCount() == TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET && built.meshlets().get(1).vertexCount() == 4, "a meshlet must hold at most its vertex budget");
    }

    // Corners of faces of the block at the origin, anticlockwise seen from outside.
    private static final float[][] UP_FACE = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
    private static final float[][] EAST_FACE = {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
    private static final float[][] PLANT_FACE = {{0, 0, 0}, {0, 1, 0}, {1, 1, 1}, {1, 0, 1}};

    /**
     * Writes a quad; its colour holds a tag, so its place after grouping can be told.
     */
    private static void putQuad(ByteBuffer buffer, float[][] corners, int tag) {
        for (float[] corner : corners) {
            buffer.putFloat(corner[0]);
            buffer.putFloat(corner[1]);
            buffer.putFloat(corner[2]);
            buffer.putInt(tag);
            buffer.putFloat(0.0F);
            buffer.putFloat(0.0F);
            buffer.putInt(0x00F000F0);
        }
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
        SectionMeshletStore.CaptureValidation nonIndexed = SectionMeshletStore.validateCapture(layer, vertices.duplicate(), ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN), vertices.remaining(), 0, 6, false, 0);
        require(nonIndexed.valid(), "valid non-indexed quad payload must be accepted: " + nonIndexed.reason());
        
        ByteBuffer indices = normalizedIndices(0, 1, 2, 2, 3, 0);
        SectionMeshletStore.CaptureValidation indexed = SectionMeshletStore.validateCapture(layer, vertices.duplicate(), indices.duplicate().order(ByteOrder.LITTLE_ENDIAN), vertices.remaining(), Short.BYTES * 6, 6, true, TerrainGpuLayout.NORMALIZED_INDEX_STRIDE);
        require(indexed.valid(), "valid normalized custom-index payload must be accepted: " + indexed.reason());
    }
    
    private static void checkCaptureValidationRejectsBadPayloads() {
        ChunkSectionLayer layer = ChunkSectionLayer.values()[0];
        ByteBuffer vertices = blockVertices(4);
        
        SectionMeshletStore.CaptureValidation misaligned = SectionMeshletStore.validateCapture(layer, vertices.duplicate(), ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN), vertices.remaining() - 1, 0, 6, false, 0);
        require(!misaligned.valid(), "misaligned vertex payload must be rejected");
        
        SectionMeshletStore.CaptureValidation vertexMismatch = SectionMeshletStore.validateCapture(layer, blockVertices(3), ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN), TerrainGpuLayout.BLOCK_VERTEX_STRIDE * 3, 0, 6, false, 0);
        require(!vertexMismatch.valid(), "non-indexed vertex/index mismatch must be rejected");
        
        SectionMeshletStore.CaptureValidation badIndex = SectionMeshletStore.validateCapture(layer, vertices.duplicate(), normalizedIndices(0, 1, 7).order(ByteOrder.LITTLE_ENDIAN), vertices.remaining(), Short.BYTES * 3, 3, true, TerrainGpuLayout.NORMALIZED_INDEX_STRIDE);
        require(!badIndex.valid(), "out-of-range custom index must be rejected");
        
        ByteBuffer nonFinite = blockVertices(4);
        nonFinite.putFloat(0, Float.NaN);
        SectionMeshletStore.CaptureValidation badVertex = SectionMeshletStore.validateCapture(layer, nonFinite.duplicate(), ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN), nonFinite.remaining(), 0, 6, false, 0);
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