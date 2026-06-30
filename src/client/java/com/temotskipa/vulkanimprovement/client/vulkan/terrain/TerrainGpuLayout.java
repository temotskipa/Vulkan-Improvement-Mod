package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public interface TerrainGpuLayout {
    int SECTION_METADATA_STRIDE = 80;
    @SuppressWarnings("unused")
    String MESHLET_HEADER_SHADER_MANIFEST = """            struct MeshletHeader {                uint64_t sectionNode;                int sectionX;                int sectionY;                int sectionZ;                int layer;                int firstVertex;                int vertexCount;                int firstIndex;                int indexCount;                uint vertexByteOffset;                uint indexByteOffset;                int vertexBytes;                int indexBytes;                int flags0;                int materialId;            };            """;
    int MESHLET_HEADER_STRIDE = 64;
    int VISIBLE_MESHLET_RECORD_STRIDE = 8;
    int VISIBLE_MESHLET_RING_MULTIPLIER = 8;
    int TERRAIN_WORK_QUEUE_COUNTER_BYTES = 16;
    int TERRAIN_WORK_QUEUE_RECORD_STRIDE = 16;
    int TERRAIN_MESH_TASK_COMMAND_STRIDE = 12;
    int TERRAIN_MESH_TASK_COMMAND_CAPACITY = 64;
    int TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES = 32;
    int TERRAIN_MESH_TASK_COMMAND_USE_PUSH_TASK_COUNT_FLAG = 1;
    int DEBUG_COUNTER_BYTES = 64;
    int MATERIAL_RECORD_STRIDE = 64;
    int MATERIAL_TABLE_CAPACITY = 256;
    int TERRAIN_PUSH_CONSTANT_BYTES = 152;
    int TARGET_VERTICES_PER_MESHLET = 64;
    int TARGET_TRIANGLES_PER_INDEXED_MESHLET = 16;
    int MAX_MESH_OUTPUT_VERTICES = 64;
    int MAX_MESH_OUTPUT_PRIMITIVES = 32;
    int BLOCK_VERTEX_STRIDE = 28;
    int INDEXED_TRIANGLES_FLAG = 1;
    int NORMALIZED_INDEX_STRIDE = Integer.BYTES;
}