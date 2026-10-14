package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerrainMeshShaderPerformanceInvariantCheck {
    private static final Path MESH_SHADER = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain/mesh_terrain.mesh.glsl");
    
    private TerrainMeshShaderPerformanceInvariantCheck() {
    }
    
    
    static void main(String[] args) throws IOException {
        String shader = Files.readString(MESH_SHADER);
        checkMeshletEmissionIsParallel(shader);
        checkMeshletGranularityReducesDispatchCount(shader);
        checkPagesAreSpreadOverSmallTasks();
    }

    /**
     * A page is drawn by one task per 32 of its meshlets. One task for a whole page handed the mesh
     * stage up to a thousand meshlets through a payload of several kilobytes, which cost the GPU
     * more than vanilla's draws of the same terrain.
     */
    private static void checkPagesAreSpreadOverSmallTasks() {
        require(TerrainGpuLayout.MESHLETS_PER_TASK == TerrainGpuLayout.TASK_WORKGROUP_SIZE, "a task must test its meshlets one per invocation");
        require(SectionMeshletStore.tasks(1) == 1, "a page with one meshlet must be drawn by one task");
        require(SectionMeshletStore.tasks(TerrainGpuLayout.MESHLETS_PER_TASK) == 1, "a page with a task's worth of meshlets must be drawn by one task");
        require(SectionMeshletStore.tasks(TerrainGpuLayout.MESHLETS_PER_TASK + 1) == 2, "a page with one meshlet more than a task's worth must be drawn by two tasks");
        int lastTask = SectionMeshletStore.tasks(TerrainGpuLayout.MAX_PAGE_MESHLETS) - 1;
        require(lastTask < 1 << Integer.SIZE - TerrainGpuLayout.TASK_RECORD_PAGE_BITS, "a task record must be able to name the last task of the largest page");
        require(SectionMeshletStore.tasks(TerrainGpuLayout.MAX_PAGE_MESHLETS + 1) - 1 >= 1 << Integer.SIZE - TerrainGpuLayout.TASK_RECORD_PAGE_BITS, "the largest page must be as large as task records allow");
    }
    
    private static void checkMeshletEmissionIsParallel(String shader) {
        require(shader.contains("layout(local_size_x = 32) in;"), "mesh shader must use a multi-invocation workgroup matched to the preferred mesh workgroup size");
        require(shader.contains("uint lane = gl_LocalInvocationID.x;"), "mesh shader must use local invocation id for parallel emission");
        require(shader.contains("if (lane == 0u)"), "mesh shader must restrict single-writer operations to lane zero");
        require(shader.contains("vertex += gl_WorkGroupSize.x"), "mesh shader must distribute vertex emission across workgroup lanes");
        require(shader.contains("primitive += gl_WorkGroupSize.x"), "mesh shader must distribute primitive emission across workgroup lanes");
        require(!shader.contains("layout(local_size_x = 1) in;"), "mesh shader must not serialize each meshlet through one invocation");
    }
    
    private static void checkMeshletGranularityReducesDispatchCount(String shader) {
        require(TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET == 128, "terrain meshlets must batch 128 non-indexed vertices to reduce task-dispatch overhead");
        require(TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET == 32, "indexed terrain meshlets must batch 32 triangles to reduce task-dispatch overhead");
        require(TerrainGpuLayout.MAX_MESH_OUTPUT_VERTICES == 128, "mesh shader output vertex limit must match the terrain meshlet vertex target");
        require(TerrainGpuLayout.MAX_MESH_OUTPUT_PRIMITIVES == 64, "mesh shader output primitive limit must support 32 terrain quads per meshlet");
        require(shader.contains("layout(triangles, max_vertices = 128, max_primitives = 64) out;"), "mesh shader layout must match the 128-vertex terrain meshlet contract");
        require(shader.contains("clamp(header.indexCount, 0, 128)"), "indexed terrain mesh shader emission must clamp to the output vertex limit");
        require(shader.contains("clamp(header.vertexCount, 0, 128)"), "non-indexed terrain mesh shader emission must clamp to the output vertex limit");
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}