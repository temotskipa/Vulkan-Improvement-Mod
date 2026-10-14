package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a section layer's quads into meshlets that each face one way. A block face points along an
 * axis, so most of a section's quads fall into six groups, and a group whose faces all point away
 * from the camera is not drawn at all: the task stage drops its meshlets before any vertex of
 * them is read. Quads that do not lie exactly across an axis, such as the crossed planes of plants,
 * form a seventh group that is never dropped for facing.
 *
 * <p>Quads keep their order within a group, so two quads in the same plane, a block face and an
 * overlay drawn on it, are drawn in the order Minecraft gave them. Each meshlet carries the box
 * around its vertices, in the section's own coordinates, for the facing and frustum tests.
 */
final class TerrainQuadMeshlets {
    static final int FACING_EAST = 0;
    static final int FACING_WEST = 1;
    static final int FACING_UP = 2;
    static final int FACING_DOWN = 3;
    static final int FACING_SOUTH = 4;
    static final int FACING_NORTH = 5;
    static final int FACING_ANY = 6;
    private static final int FACINGS = 7;
    private static final int QUAD_BYTES = TerrainGpuLayout.BLOCK_VERTEX_STRIDE * 4;
    private static final int QUADS_PER_MESHLET = TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET / 4;
    // A face counts as pointing along an axis only when its other two directions vanish, not when
    // it is merely close: a slightly tilted face can be seen from behind its box from far enough.
    private static final float OFF_AXIS = 1.0e-5F;

    private TerrainQuadMeshlets() {
    }

    /**
     * @param vertices the layer's vertices, four per quad, as Minecraft built them
     * @return the vertices in facing order and their meshlets, or {@code null} when the vertices
     * are not whole quads
     */
    static @Nullable Built build(long sectionNode, ChunkSectionLayer layer, ByteBuffer vertices) {
        ByteBuffer source = vertices.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int base = source.position();
        int bytes = source.remaining();
        if (bytes % QUAD_BYTES != 0) {
            return null;
        }
        int quadCount = bytes / QUAD_BYTES;
        int[] facing = new int[quadCount];
        int[] counts = new int[FACINGS];
        for (int quad = 0; quad < quadCount; quad++) {
            facing[quad] = facingOf(source, base + quad * QUAD_BYTES);
            counts[facing[quad]]++;
        }
        int[] firstQuad = new int[FACINGS];
        for (int group = 1; group < FACINGS; group++) {
            firstQuad[group] = firstQuad[group - 1] + counts[group - 1];
        }
        ByteBuffer sorted = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] cursor = firstQuad.clone();
        for (int quad = 0; quad < quadCount; quad++) {
            sorted.put(cursor[facing[quad]]++ * QUAD_BYTES, source, base + quad * QUAD_BYTES, QUAD_BYTES);
        }

        List<TerrainMeshlet> meshlets = new ArrayList<>();
        for (int group = 0; group < FACINGS; group++) {
            int end = firstQuad[group] + counts[group];
            for (int first = firstQuad[group]; first < end; first += QUADS_PER_MESHLET) {
                int quads = Math.min(QUADS_PER_MESHLET, end - first);
                float[] bounds = bounds(sorted, first * QUAD_BYTES, quads * 4);
                meshlets.add(TerrainMeshlet.quads(sectionNode, layer, first * 4, quads * 4, bounds, group));
            }
        }
        return new Built(sorted, List.copyOf(meshlets));
    }

    /**
     * Which way a quad faces: the normal of its first triangle, which for Minecraft's quads points
     * out of the face they are seen from, or of its second when the first has no area.
     */
    static int facingOf(ByteBuffer vertices, int quadOffset) {
        float[] normal = normal(vertices, quadOffset, 0, 1, 2);
        if (normal == null) {
            normal = normal(vertices, quadOffset, 0, 2, 3);
        }
        if (normal == null) {
            return FACING_ANY;
        }
        float x = normal[0];
        float y = normal[1];
        float z = normal[2];
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        float limit = length * OFF_AXIS;
        if (Math.abs(y) <= limit && Math.abs(z) <= limit) {
            return x > 0.0F ? FACING_EAST : FACING_WEST;
        }
        if (Math.abs(x) <= limit && Math.abs(z) <= limit) {
            return y > 0.0F ? FACING_UP : FACING_DOWN;
        }
        if (Math.abs(x) <= limit && Math.abs(y) <= limit) {
            return z > 0.0F ? FACING_SOUTH : FACING_NORTH;
        }
        return FACING_ANY;
    }

    private static float @Nullable [] normal(ByteBuffer vertices, int quadOffset, int a, int b, int c) {
        int stride = TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
        float ax = vertices.getFloat(quadOffset + a * stride);
        float ay = vertices.getFloat(quadOffset + a * stride + Float.BYTES);
        float az = vertices.getFloat(quadOffset + a * stride + 2 * Float.BYTES);
        float ux = vertices.getFloat(quadOffset + b * stride) - ax;
        float uy = vertices.getFloat(quadOffset + b * stride + Float.BYTES) - ay;
        float uz = vertices.getFloat(quadOffset + b * stride + 2 * Float.BYTES) - az;
        float vx = vertices.getFloat(quadOffset + c * stride) - ax;
        float vy = vertices.getFloat(quadOffset + c * stride + Float.BYTES) - ay;
        float vz = vertices.getFloat(quadOffset + c * stride + 2 * Float.BYTES) - az;
        float x = uy * vz - uz * vy;
        float y = uz * vx - ux * vz;
        float z = ux * vy - uy * vx;
        float squared = x * x + y * y + z * z;
        return squared > 1.0e-12F && Float.isFinite(squared) ? new float[]{x, y, z} : null;
    }

    /**
     * The box around a run of vertices: minimum x, y, z, then maximum x, y, z.
     */
    private static float[] bounds(ByteBuffer vertices, int offset, int vertexCount) {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int at = offset + vertex * TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
            for (int axis = 0; axis < 3; axis++) {
                float value = vertices.getFloat(at + axis * Float.BYTES);
                bounds[axis] = Math.min(bounds[axis], value);
                bounds[axis + 3] = Math.max(bounds[axis + 3], value);
            }
        }
        return bounds;
    }

    record Built(ByteBuffer vertices, List<TerrainMeshlet> meshlets) {
    }
}
