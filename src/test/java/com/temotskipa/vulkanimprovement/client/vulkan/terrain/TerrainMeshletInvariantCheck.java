package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;

import java.lang.foreign.MemorySegment;

public final class TerrainMeshletInvariantCheck {
    private static final int QUAD_BYTES = 4 * TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
    private static final MemorySegment NO_INDICES = MemorySegment.ofArray(new byte[0]);
    // Corners of faces of the block at the origin, anticlockwise seen from outside.
    private static final float[][] UP_FACE = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
    private static final float[][] EAST_FACE = {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
    private static final float[][] PLANT_FACE = {{0, 0, 0}, {0, 1, 0}, {1, 1, 1}, {1, 0, 1}};
    
    private TerrainMeshletInvariantCheck() {
    }
    
    static void main() {
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
        MemorySegment vertices = MemorySegment.ofArray(new byte[5 * QUAD_BYTES]);
        putQuad(vertices, 0, UP_FACE, 1);
        putQuad(vertices, 1, EAST_FACE, 2);
        putQuad(vertices, 2, PLANT_FACE, 3);
        putQuad(vertices, 3, UP_FACE, 4);
        putQuad(vertices, 4, EAST_FACE, 5);
        
        BuiltQuadMeshlets built = TerrainQuadMeshlets.build(sectionNode, layer, vertices);
        require(built != null, "whole quads must be grouped");
        require(built.vertices().byteSize() == vertices.byteSize(), "grouping must keep every vertex");
        require(built.meshlets().size() == 3, "three facings must give three meshlets");
        require(built.meshlets().get(0).facing() == TerrainQuadMeshlets.FACING_EAST && built.meshlets().get(1).facing() == TerrainQuadMeshlets.FACING_UP && built.meshlets().get(2).facing() == TerrainQuadMeshlets.FACING_ANY, "groups must come in facing order");
        int[] expectedTags = {2, 5, 1, 4, 3};
        for (int quad = 0; quad < expectedTags.length; quad++) {
            require(built.vertices().get(TerrainGpuRecords.INT, (long) quad * QUAD_BYTES + 12) == expectedTags[quad], "quads must keep their order inside a facing group");
        }
        int covered = 0;
        for (TerrainMeshlet meshlet : built.meshlets()) {
            require(meshlet.firstVertex() == covered, "meshlets must cover the grouped vertices in order");
            covered += meshlet.vertexCount();
        }
        require(covered == 20, "meshlets must cover every vertex once");
        TerrainMeshlet east = built.meshlets().getFirst();
        require(east.minX() == 1.0F && east.maxX() == 1.0F && east.minY() == 0.0F && east.maxY() == 1.0F, "a meshlet's box must hold its quads and no more");
        require(TerrainQuadMeshlets.build(sectionNode, layer, blockVertices(3)) == null, "vertices that are not whole quads must not be grouped");
    }

    /**
     * A face points to the side its corners go round anticlockwise when seen from, as Minecraft's
     * do; a face that does not lie across an axis is never dropped for facing.
     */
    private static void checkFacingFollowsWinding() {
        MemorySegment vertices = MemorySegment.ofArray(new byte[4 * QUAD_BYTES]);
        putQuad(vertices, 0, UP_FACE, 0);
        putQuad(vertices, 1, EAST_FACE, 0);
        putQuad(vertices, 2, new float[][]{EAST_FACE[3], EAST_FACE[2], EAST_FACE[1], EAST_FACE[0]}, 0);
        putQuad(vertices, 3, new float[][]{{0, 1, 0}, {0, 1, 0}, {0, 1, 1}, {1, 1, 1}}, 0);
        require(TerrainQuadMeshlets.facingOf(vertices, 0L) == TerrainQuadMeshlets.FACING_UP, "a top face must face up");
        require(TerrainQuadMeshlets.facingOf(vertices, QUAD_BYTES) == TerrainQuadMeshlets.FACING_EAST, "an east face must face east");
        require(TerrainQuadMeshlets.facingOf(vertices, 2L * QUAD_BYTES) == TerrainQuadMeshlets.FACING_WEST, "reversed corners must face the other way");
        require(TerrainQuadMeshlets.facingOf(vertices, 3L * QUAD_BYTES) == TerrainQuadMeshlets.FACING_UP, "a quad whose first triangle has no area must face the way its second does");
        MemorySegment plant = MemorySegment.ofArray(new byte[QUAD_BYTES]);
        putQuad(plant, 0, PLANT_FACE, 0);
        require(TerrainQuadMeshlets.facingOf(plant, 0L) == TerrainQuadMeshlets.FACING_ANY, "a diagonal face must not be dropped for facing");
    }

    private static void checkLargeGroupsSplitIntoMeshlets() {
        int quads = TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET / 4 + 1;
        MemorySegment vertices = MemorySegment.ofArray(new byte[quads * QUAD_BYTES]);
        for (int quad = 0; quad < quads; quad++) {
            putQuad(vertices, quad, UP_FACE, quad);
        }
        BuiltQuadMeshlets built = TerrainQuadMeshlets.build(SectionPos.asLong(0, 0, 0), ChunkSectionLayer.values()[0], vertices);
        require(built != null && built.meshlets().size() == 2, "a facing group larger than a meshlet must be split");
        require(built.meshlets().get(0).vertexCount() == TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET && built.meshlets().get(1).vertexCount() == 4, "a meshlet must hold at most its vertex budget");
    }
    
    /**
     * Writes a quad; its colour holds a tag, so its place after grouping can be told.
     */
    private static void putQuad(MemorySegment vertices, int quad, float[][] corners, int tag) {
        for (int corner = 0; corner < corners.length; corner++) {
            putVertex(vertices, quad * 4 + corner, corners[corner][0], corners[corner][1], corners[corner][2], tag, 0.0F, 0.0F);
        }
    }
    
    /**
     * Writes a block vertex: its position, colour, texture coordinates and light.
     */
    private static void putVertex(MemorySegment vertices, int vertex, float x, float y, float z, int color, float u, float v) {
        long at = (long) vertex * TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
        vertices.set(TerrainGpuRecords.FLOAT, at, x);
        vertices.set(TerrainGpuRecords.FLOAT, at + 4, y);
        vertices.set(TerrainGpuRecords.FLOAT, at + 8, z);
        vertices.set(TerrainGpuRecords.INT, at + 12, color);
        vertices.set(TerrainGpuRecords.FLOAT, at + 16, u);
        vertices.set(TerrainGpuRecords.FLOAT, at + 20, v);
        vertices.set(TerrainGpuRecords.INT, at + 24, 0x00F000F0);
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
        MemorySegment vertices = blockVertices(4);
        SectionCaptureValidation nonIndexed = SectionMeshletStore.validateCapture(layer, vertices, NO_INDICES, bytes(vertices), 0, 6, false, 0);
        require(nonIndexed.valid(), "valid non-indexed quad payload must be accepted: " + nonIndexed.reason());
        
        MemorySegment indices = normalizedIndices(0, 1, 2, 2, 3, 0);
        SectionCaptureValidation indexed = SectionMeshletStore.validateCapture(layer, vertices, indices, bytes(vertices), Short.BYTES * 6, 6, true, TerrainGpuLayout.NORMALIZED_INDEX_STRIDE);
        require(indexed.valid(), "valid normalized custom-index payload must be accepted: " + indexed.reason());
    }
    
    private static void checkCaptureValidationRejectsBadPayloads() {
        ChunkSectionLayer layer = ChunkSectionLayer.values()[0];
        MemorySegment vertices = blockVertices(4);
        
        SectionCaptureValidation misaligned = SectionMeshletStore.validateCapture(layer, vertices, NO_INDICES, bytes(vertices) - 1, 0, 6, false, 0);
        require(!misaligned.valid(), "misaligned vertex payload must be rejected");
        
        SectionCaptureValidation vertexMismatch = SectionMeshletStore.validateCapture(layer, blockVertices(3), NO_INDICES, TerrainGpuLayout.BLOCK_VERTEX_STRIDE * 3, 0, 6, false, 0);
        require(!vertexMismatch.valid(), "non-indexed vertex/index mismatch must be rejected");
        
        SectionCaptureValidation badIndex = SectionMeshletStore.validateCapture(layer, vertices, normalizedIndices(0, 1, 7), bytes(vertices), Short.BYTES * 3, 3, true, TerrainGpuLayout.NORMALIZED_INDEX_STRIDE);
        require(!badIndex.valid(), "out-of-range custom index must be rejected");
        
        MemorySegment nonFinite = blockVertices(4);
        nonFinite.set(TerrainGpuRecords.FLOAT, 0L, Float.NaN);
        SectionCaptureValidation badVertex = SectionMeshletStore.validateCapture(layer, nonFinite, NO_INDICES, bytes(nonFinite), 0, 6, false, 0);
        require(!badVertex.valid(), "non-finite vertex payload must be rejected");
    }
    
    private static MemorySegment blockVertices(int vertexCount) {
        MemorySegment vertices = MemorySegment.ofArray(new byte[vertexCount * TerrainGpuLayout.BLOCK_VERTEX_STRIDE]);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            putVertex(vertices, vertex, vertex & 1, vertex >> 1, 0.0F, 0xFFFFFFFF, vertex & 1, vertex >> 1);
        }
        return vertices;
    }
    
    private static MemorySegment normalizedIndices(int... indices) {
        MemorySegment segment = MemorySegment.ofArray(new byte[indices.length * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE]);
        MemorySegment.copy(indices, 0, segment, TerrainGpuRecords.INT, 0L, indices.length);
        return segment;
    }
    
    private static int bytes(MemorySegment segment) {
        return Math.toIntExact(segment.byteSize());
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}