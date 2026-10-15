package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

public final class TerrainGpuLayout {
    private TerrainGpuLayout() {
    }
    
    public static final int SECTION_METADATA_STRIDE = 80;
    /**
     * A meshlet header: where its vertices or indices are, its section and material, the box
     * around it in the section's coordinates and the way its faces point.
     */
    public static final int MESHLET_HEADER_STRIDE = 96;
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
    /**
     * The GPU-driven pass's push constants: three buffer addresses, two integers, and five rows
     * of four integers.
     */
    public static final int SECTION_PAGES_PUSH_CONSTANT_BYTES = 112;
    /**
     * How many page-table slots one workgroup of the GPU-driven pass looks at.
     */
    public static final int SECTION_PAGES_WORKGROUP_SIZE = 64;
    /**
     * How many layers the GPU-driven pass can draw, and into how many distances from the camera's
     * section it sorts each layer's tasks: the nearest 63, and everything farther.
     */
    public static final int SECTION_PAGES_LAYERS = 4;
    public static final int SECTION_PAGES_DISTANCES = 64;
    /**
     * The counters the GPU-driven pass sorts tasks with, at the start of a frame's data in the
     * visible list, in that list's records: how many tasks each layer has at each distance, and
     * where each distance's records go.
     */
    public static final int SECTION_PAGES_COUNTER_RECORDS = SECTION_PAGES_LAYERS * SECTION_PAGES_DISTANCES * 2 * Integer.BYTES / VISIBLE_MESHLET_RECORD_STRIDE;
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
    
    /**
     * A meshlet's size: sixteen quads, or 21 triangles whose 63 corners fit the mesh stage's
     * output. Meshlets of 128 vertices cost the GPU about a twentieth more on the same terrain.
     */
    public static final int TARGET_VERTICES_PER_MESHLET = 64;
    public static final int TARGET_TRIANGLES_PER_INDEXED_MESHLET = 21;
    public static final int MAX_MESH_OUTPUT_VERTICES = 64;
    public static final int MAX_MESH_OUTPUT_PRIMITIVES = 32;
    public static final int BLOCK_VERTEX_STRIDE = 28;
    public static final int INDEXED_TRIANGLES_FLAG = 1;
    /**
     * How many of a page's meshlets one task looks at and hands on. A page is drawn by one task per
     * this many of its meshlets: what a task hands the mesh stage stays small, and a large page is
     * spread over many workgroups instead of one.
     */
    public static final int MESHLETS_PER_TASK = 32;
    /**
     * A task's record names its page in this many low bits and, above them, which of the page's
     * tasks it is.
     */
    public static final int TASK_RECORD_PAGE_BITS = 26;
    /**
     * The most pages the page table may hold, as many as a task record can name.
     */
    public static final int MAX_PAGES = 1 << TASK_RECORD_PAGE_BITS;
    /**
     * The most meshlets a page may have: as many tasks as the bits above a task record's page can
     * count. A page for one section layer of whole block faces has at most 24,576 quads, 1,536
     * meshlets and a few partial ones.
     */
    public static final int MAX_PAGE_MESHLETS = 2048;
    /**
     * The task stage's workgroup: one invocation tests one of the task's meshlets.
     */
    public static final int TASK_WORKGROUP_SIZE = 32;
    /**
     * Bits of the task and mesh stages' cull flags: the mesh stage drops meshlets out of view (the
     * work-queue path), the task stage drops a page's meshlets seen from behind, or out of view.
     */
    public static final int CULL_MESHLET_FRUSTUM_IN_MESH_STAGE = 1;
    public static final int CULL_BACK_FACING_MESHLETS = 2;
    public static final int CULL_MESHLETS_OUTSIDE_VIEW = 4;
    public static final int NORMALIZED_INDEX_STRIDE = Integer.BYTES;
}