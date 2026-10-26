package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A mesh-task draw's indirect command, VkDrawMeshTasksIndirectCommandEXT.
 */
public final class TerrainMeshTaskCommandRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(TerrainGpuRecords.INT.withName("groupCountX"), TerrainGpuRecords.INT.withName("groupCountY"), TerrainGpuRecords.INT.withName("groupCountZ")).withName("MeshTaskCommand");
    public static final long GROUP_COUNT_X = TerrainGpuRecords.offset(LAYOUT, "groupCountX");
    public static final long GROUP_COUNT_Y = TerrainGpuRecords.offset(LAYOUT, "groupCountY");
    public static final long GROUP_COUNT_Z = TerrainGpuRecords.offset(LAYOUT, "groupCountZ");
    
    private TerrainMeshTaskCommandRecord() {
    }
}