#version 460
#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

struct TaskPayload {
    uint visibleMeshlet;
    float meshletVisibility;
};

taskPayloadSharedEXT TaskPayload taskPayload;

struct VisibleMeshletRecord {
    uint meshletIndex;
    float visibility;
};

struct TerrainWorkQueueRecord {
    uint meshletIndex;
    uint layerOrdinal;
    uint lodLevel;
    uint flags;
};

// A section layer's place on the GPU. Only where its meshlets are and how many matter here.
struct ResidentPage {
    uint64_t sectionNode;
    int sectionX;
    int sectionY;
    int sectionZ;
    int layer;
    int firstMeshlet;
    int meshletCount;
    int estimatedVertices;
    int estimatedMeshlets;
    int vertexBytes;
    int indexBytes;
    int vertexByteOffset;
    int indexByteOffset;
    int reserved0;
    int customIndexBuffer;
    int reserved1;
    int reserved2;
    uint64_t reserved3;
};

layout(buffer_reference, scalar, buffer_reference_align = 8) readonly buffer PageTable {
    ResidentPage pages[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer VisibleMeshlets {
    VisibleMeshletRecord records[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer TerrainWorkQueue {
    uint producedCount;
    uint consumedCount;
    uint droppedCount;
    uint reserved;
    TerrainWorkQueueRecord records[];
};

layout(push_constant, scalar) uniform TerrainPushConstants {
    uint64_t meshletHeaders;
    uint64_t vertexPayload;
    uint64_t indexPayload;
    uint64_t debugCounters;
    uint64_t visibleMeshlets;
    uint64_t workQueue;
    uint64_t materialTable;
    uint64_t pageTable;
    uint meshletCount;
    uint enableMeshletFrustumCulling;
    uint meshletOffset;
    int layerOrdinal;
    ivec4 cameraBlock;
    vec4 cameraOffset;
    vec4 cameraRight;
    vec4 cameraUp;
    vec4 cameraForward;
    vec4 projection;
} pc;

void main() {
    if (pc.pageTable != uint64_t(0)) {
        // The GPU listed the pages to draw. This task draws one of them: a mesh task per meshlet
        // of the page. The record's index is a page's, not a meshlet's.
        VisibleMeshlets drawn = VisibleMeshlets(pc.visibleMeshlets);
        VisibleMeshletRecord record = drawn.records[pc.meshletOffset + gl_WorkGroupID.x];
        ResidentPage page = PageTable(pc.pageTable).pages[record.meshletIndex];
        taskPayload.visibleMeshlet = uint(page.firstMeshlet);
        taskPayload.meshletVisibility = clamp(record.visibility, 0.0, 1.0);
        EmitMeshTasksEXT(uint(page.meshletCount), 1, 1);
        return;
    }
    uint localMeshlet = min(gl_WorkGroupID.x, pc.meshletCount - 1u);
    if (pc.workQueue != uint64_t(0)) {
        TerrainWorkQueue queue = TerrainWorkQueue(pc.workQueue);
        TerrainWorkQueueRecord record = queue.records[pc.meshletOffset + localMeshlet];
        taskPayload.visibleMeshlet = record.meshletIndex;
        taskPayload.meshletVisibility = 1.0;
    } else if (pc.visibleMeshlets != uint64_t(0)) {
        VisibleMeshlets visible = VisibleMeshlets(pc.visibleMeshlets);
        VisibleMeshletRecord record = visible.records[pc.meshletOffset + localMeshlet];
        taskPayload.visibleMeshlet = record.meshletIndex;
        taskPayload.meshletVisibility = clamp(record.visibility, 0.0, 1.0);
    } else {
        taskPayload.visibleMeshlet = pc.meshletOffset + localMeshlet;
        taskPayload.meshletVisibility = 1.0;
    }
    EmitMeshTasksEXT(1, 1, 1);
}
