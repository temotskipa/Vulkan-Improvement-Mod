package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

public final class TerrainGpuLayout {
    private TerrainGpuLayout() {
    }
    
    public static final int SECTION_METADATA_STRIDE = 80;
    public static final int MESHLET_HEADER_STRIDE = 64;
    public static final int VISIBLE_MESHLET_RECORD_STRIDE = 8;
    public static final int VISIBLE_MESHLET_RING_MULTIPLIER = 8;
    public static final int TERRAIN_WORK_QUEUE_COUNTER_BYTES = 16;
    public static final int TERRAIN_WORK_QUEUE_RECORD_STRIDE = 16;
    public static final int TERRAIN_MESH_TASK_COMMAND_STRIDE = 12;
    public static final int TERRAIN_MESH_TASK_COMMAND_CAPACITY = 64;
    /**
     * The visibility pass's push constants: four buffer addresses, eight integers, the camera's
     * block and its offset inside that block, and four rows of the view-projection matrix.
     */
    public static final int TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES = 160;
    /**
     * How many pages one workgroup of the visibility pass looks at.
     */
    public static final int VISIBILITY_WORKGROUP_SIZE = 64;
    public static final int DEBUG_COUNTER_BYTES = 64;
    /**
     * Where the visibility pass's counters start in the debug counters, after the mesh stage's four.
     */
    public static final int VISIBILITY_COUNTER_OFFSET = 16;
    public static final int MATERIAL_RECORD_STRIDE = 64;
    public static final int MATERIAL_TABLE_CAPACITY = 256;
    
    /**
     * The task and mesh stages' push constants: eight buffer addresses, four integers,
     * the camera's block and its offset inside that block, and four rows of the view-projection matrix.
     */
    public static final int TERRAIN_PUSH_CONSTANT_BYTES = 176;
    
    /**
     * The fragment stage's push constants follow the other stages' at the next offset a
     * {@code vec4} may start at: the frame's fog colour, its four distances, and its mode.
     */
    public static final int TERRAIN_FOG_PUSH_CONSTANT_OFFSET = 176;
    public static final int TERRAIN_FOG_PUSH_CONSTANT_BYTES = 48;
    
    /**
     * What every Vulkan 1.4 device accepts, and the mod requires Vulkan 1.4.
     */
    public static final int VULKAN_1_4_MIN_PUSH_CONSTANT_BYTES = 256;
    
    public static final int TARGET_VERTICES_PER_MESHLET = 128;
    public static final int TARGET_TRIANGLES_PER_INDEXED_MESHLET = 32;
    public static final int MAX_MESH_OUTPUT_VERTICES = 128;
    public static final int MAX_MESH_OUTPUT_PRIMITIVES = 64;
    public static final int BLOCK_VERTEX_STRIDE = 28;
    public static final int INDEXED_TRIANGLES_FLAG = 1;
    public static final int NORMALIZED_INDEX_STRIDE = Integer.BYTES;
}