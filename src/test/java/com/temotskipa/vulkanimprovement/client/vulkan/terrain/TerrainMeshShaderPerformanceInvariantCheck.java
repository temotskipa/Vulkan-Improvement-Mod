package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerrainMeshShaderPerformanceInvariantCheck {
    private static final Path MESH_SHADER = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain/mesh_terrain.mesh.glsl");

    private TerrainMeshShaderPerformanceInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        String shader = Files.readString(MESH_SHADER);
        checkMeshletEmissionIsParallel(shader);
        checkMeshletGranularityReducesDispatchCount(shader);
    }

    private static void checkMeshletEmissionIsParallel(String shader) {
        require(shader.contains("layout(local_size_x = 32) in;"),
                "mesh shader must use a multi-invocation workgroup matched to the preferred mesh workgroup size");
        require(shader.contains("uint lane = gl_LocalInvocationID.x;"),
                "mesh shader must use local invocation id for parallel emission");
        require(shader.contains("if (lane == 0u)"),
                "mesh shader must restrict single-writer operations to lane zero");
        require(shader.contains("vertex += gl_WorkGroupSize.x"),
                "mesh shader must distribute vertex emission across workgroup lanes");
        require(shader.contains("primitive += gl_WorkGroupSize.x"),
                "mesh shader must distribute primitive emission across workgroup lanes");
        require(!shader.contains("layout(local_size_x = 1) in;"),
                "mesh shader must not serialize each meshlet through one invocation");
    }

    private static void checkMeshletGranularityReducesDispatchCount(String shader) {
        require(TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET == 128,
                "terrain meshlets must batch 128 non-indexed vertices to reduce task-dispatch overhead");
        require(TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET == 32,
                "indexed terrain meshlets must batch 32 triangles to reduce task-dispatch overhead");
        require(TerrainGpuLayout.MAX_MESH_OUTPUT_VERTICES == 128,
                "mesh shader output vertex limit must match the terrain meshlet vertex target");
        require(TerrainGpuLayout.MAX_MESH_OUTPUT_PRIMITIVES == 64,
                "mesh shader output primitive limit must support 32 terrain quads per meshlet");
        require(shader.contains("layout(triangles, max_vertices = 128, max_primitives = 64) out;"),
                "mesh shader layout must match the 128-vertex terrain meshlet contract");
        require(shader.contains("clamp(header.indexCount, 0, 128)"),
                "indexed terrain mesh shader emission must clamp to the output vertex limit");
        require(shader.contains("clamp(header.vertexCount, 0, 128)"),
                "non-indexed terrain mesh shader emission must clamp to the output vertex limit");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
