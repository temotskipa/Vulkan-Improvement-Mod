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
    uint meshletCount;
    uint enableMeshletFrustumCulling;
    uint meshletOffset;
    int layerOrdinal;
    vec4 cameraPosition;
    vec4 cameraRight;
    vec4 cameraUp;
    vec4 cameraForward;
    vec4 projection;
} pc;

void main() {
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
