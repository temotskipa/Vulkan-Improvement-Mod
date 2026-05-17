package com.temotskipa.vulkanimprovement.client.vulkan;

final class TerrainGpuLayout {
    static final int SECTION_METADATA_STRIDE = 80;
    static final int MESHLET_HEADER_STRIDE = 64;
    static final int VISIBLE_MESHLET_RECORD_STRIDE = 8;
    static final int VISIBLE_MESHLET_RING_MULTIPLIER = 8;
    static final int TERRAIN_WORK_QUEUE_COUNTER_BYTES = 16;
    static final int TERRAIN_WORK_QUEUE_RECORD_STRIDE = 16;
    static final int TERRAIN_MESH_TASK_COMMAND_STRIDE = 12;
    static final int TERRAIN_MESH_TASK_COMMAND_CAPACITY = 64;
    static final int TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES = 32;
    static final int DEBUG_COUNTER_BYTES = 64;
    static final int MATERIAL_RECORD_STRIDE = 64;
    static final int MATERIAL_TABLE_CAPACITY = 256;
    static final int TERRAIN_PUSH_CONSTANT_BYTES = 152;
    static final int TARGET_VERTICES_PER_MESHLET = 64;
    static final int TARGET_TRIANGLES_PER_INDEXED_MESHLET = 16;
    static final int MAX_MESH_OUTPUT_VERTICES = 64;
    static final int MAX_MESH_OUTPUT_PRIMITIVES = 32;
    static final int BLOCK_VERTEX_STRIDE = 28;
    static final int INDEXED_TRIANGLES_FLAG = 1;
    static final int NORMALIZED_INDEX_STRIDE = Integer.BYTES;

    private TerrainGpuLayout() {
    }
}
