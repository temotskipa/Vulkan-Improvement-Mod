package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.io.IOException;

public final class TerrainMeshShaderPerformanceInvariantCheck {
    private TerrainMeshShaderPerformanceInvariantCheck() {
    }
    
    
    static void main() throws IOException {
        String shader = GlslSource.read("mesh_terrain.mesh.glsl");
        checkMeshletEmissionIsParallel(shader);
        checkMeshletGranularityReducesDispatchCount(shader);
        checkPagesAreSpreadOverSmallTasks(GlslSource.read("mesh_terrain.task.glsl"));
    }
    
    /**
     * A page is drawn by one task per 32 of its meshlets. One task for a whole page handed the mesh
     * stage up to a thousand meshlets through a payload of several kilobytes, which cost the GPU
     * more than vanilla's draws of the same terrain.
     */
    private static void checkPagesAreSpreadOverSmallTasks(String taskShader) {
        int taskWorkgroupSize = GlslSource.layoutValue(taskShader, "local_size_x");
        require(TerrainGpuLayout.TASK_WORKGROUP_SIZE == taskWorkgroupSize, "the task stage's workgroup must be as large as the program dispatches it");
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
        int maxVertices = GlslSource.layoutValue(shader, "max_vertices");
        int maxPrimitives = GlslSource.layoutValue(shader, "max_primitives");
        require(TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET * 3 <= maxVertices, "an indexed meshlet's corners must fit the mesh stage's output");
        // 64 vertices cost the GPU about a twentieth less than 128 on the same terrain.
        require(TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET == maxVertices, "a non-indexed meshlet must fill the mesh stage's output");
        require(maxPrimitives * 2 == maxVertices, "the mesh stage must be able to output two triangles per quad of a meshlet");
        require(shader.contains("clamp(header.indexCount, 0, 64)"), "indexed terrain mesh shader emission must clamp to the output vertex limit");
        require(shader.contains("clamp(header.vertexCount, 0, 64)"), "non-indexed terrain mesh shader emission must clamp to the output vertex limit");
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}