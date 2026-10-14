#version 460
#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

// A task tests this many of a page's meshlets, one per invocation.
layout(local_size_x = 32) in;

const uint MESHLETS_PER_TASK = 32u;
// A task's record names its page in the low bits and which of the page's tasks it is above them.
const uint TASK_RECORD_PAGE_BITS = 27u;
const uint TASK_RECORD_PAGE_MASK = (1u << TASK_RECORD_PAGE_BITS) - 1u;
const uint CULL_BACK_FACING_MESHLETS = 2u;
const uint CULL_MESHLETS_OUTSIDE_VIEW = 4u;
const int FACING_EAST = 0;
const int FACING_WEST = 1;
const int FACING_UP = 2;
const int FACING_DOWN = 3;
const int FACING_SOUTH = 4;
const int FACING_NORTH = 5;

// The meshlets a task hands to the mesh stage, in the order they are to be drawn.
struct TaskPayload {
    float meshletVisibility;
    uint meshletCount;
    uint meshlets[MESHLETS_PER_TASK];
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

// Of a meshlet, only its box and the way its faces point matter here.
struct MeshletHeader {
    uint64_t sectionNode;
    int sectionX;
    int sectionY;
    int sectionZ;
    int layer;
    int firstVertex;
    int vertexCount;
    int firstIndex;
    int indexCount;
    uint vertexByteOffset;
    uint indexByteOffset;
    int vertexBytes;
    int indexBytes;
    int flags0;
    int materialId;
    vec3 boundsMin;
    vec3 boundsMax;
    int facing;
    int reserved0;
};

layout(buffer_reference, scalar, buffer_reference_align = 8) readonly buffer PageTable {
    ResidentPage pages[];
};

layout(buffer_reference, scalar, buffer_reference_align = 8) readonly buffer MeshletHeaders {
    MeshletHeader headers[];
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
    uint cullFlags;
    uint meshletOffset;
    int layerOrdinal;
    ivec4 cameraBlock;
    vec4 cameraOffset;
    vec4 cameraRight;
    vec4 cameraUp;
    vec4 cameraForward;
    vec4 projection;
} pc;

// Which of the current batch of meshlets are kept, one bit per invocation.
shared uint keptMask;

// Whether a box, given relative to the camera, lies wholly outside the view: every corner beyond
// the same side of the frustum, or behind the camera.
bool outsideView(vec3 low, vec3 high) {
    uint beyondEverywhere = 31u;
    for (uint corner = 0u; corner < 8u; corner++) {
        vec4 point = vec4(mix(low, high, vec3(float(corner & 1u), float((corner >> 1) & 1u), float((corner >> 2) & 1u))), 1.0);
        float x = dot(pc.cameraRight, point);
        float y = dot(pc.cameraUp, point);
        float w = dot(pc.projection, point);
        uint beyond = (x < -w ? 1u : 0u) | (x > w ? 2u : 0u) | (y < -w ? 4u : 0u) | (y > w ? 8u : 0u) | (w <= 0.0 ? 16u : 0u);
        beyondEverywhere &= beyond;
    }
    return beyondEverywhere != 0u;
}

// Whether every face of a meshlet that points one way points away from the camera. A face that
// points east is seen only from east of its plane, and the box's west side is the westernmost of
// those planes; the camera is at the origin. A face edge-on to the camera is kept.
bool facesAway(int facing, vec3 low, vec3 high) {
    switch (facing) {
        case FACING_EAST: return low.x > 0.0;
        case FACING_WEST: return high.x < 0.0;
        case FACING_UP: return low.y > 0.0;
        case FACING_DOWN: return high.y < 0.0;
        case FACING_SOUTH: return low.z > 0.0;
        case FACING_NORTH: return high.z < 0.0;
        default: return false;
    }
}

bool keepMeshlet(MeshletHeader header, ResidentPage page) {
    // The section's block coordinates and the camera's are subtracted as integers first.
    vec3 origin = vec3(ivec3(page.sectionX, page.sectionY, page.sectionZ) * 16 - pc.cameraBlock.xyz) - pc.cameraOffset.xyz;
    vec3 low = origin + header.boundsMin;
    vec3 high = origin + header.boundsMax;
    if ((pc.cullFlags & CULL_BACK_FACING_MESHLETS) != 0u && facesAway(header.facing, low, high)) {
        return false;
    }
    return (pc.cullFlags & CULL_MESHLETS_OUTSIDE_VIEW) == 0u || !outsideView(low - 1.0, high + 1.0);
}

void main() {
    uint lane = gl_LocalInvocationID.x;
    if (pc.pageTable != uint64_t(0)) {
        // The pages to draw were listed, each as one task per MESHLETS_PER_TASK of its meshlets.
        // This task hands on those of its share that may be seen, in their order.
        VisibleMeshlets drawn = VisibleMeshlets(pc.visibleMeshlets);
        VisibleMeshletRecord record = drawn.records[pc.meshletOffset + gl_WorkGroupID.x];
        ResidentPage page = PageTable(pc.pageTable).pages[record.meshletIndex & TASK_RECORD_PAGE_MASK];
        MeshletHeaders headers = MeshletHeaders(pc.meshletHeaders);
        uint first = (record.meshletIndex >> TASK_RECORD_PAGE_BITS) * MESHLETS_PER_TASK;
        uint count = uint(max(page.meshletCount, 0));
        count = count > first ? min(count - first, MESHLETS_PER_TASK) : 0u;
        // A page out of view is dropped whole before any of its meshlets is read. A section's mesh
        // may reach a little past its sixteen blocks, so its box is tested with a margin.
        vec3 sectionLow = vec3(ivec3(page.sectionX, page.sectionY, page.sectionZ) * 16 - pc.cameraBlock.xyz) - pc.cameraOffset.xyz;
        if ((pc.cullFlags & CULL_MESHLETS_OUTSIDE_VIEW) != 0u && outsideView(sectionLow - 2.0, sectionLow + 18.0)) {
            count = 0u;
        }
        uint meshlet = uint(page.firstMeshlet) + first + lane;
        bool keep = lane < count && keepMeshlet(headers.headers[meshlet], page);
        if (lane == 0u) {
            keptMask = 0u;
        }
        barrier();
        if (keep) {
            atomicOr(keptMask, 1u << lane);
        }
        barrier();
        uint mask = keptMask;
        if (keep) {
            taskPayload.meshlets[bitCount(mask & ((1u << lane) - 1u))] = meshlet;
        }
        uint kept = uint(bitCount(mask));
        if (lane == 0u) {
            taskPayload.meshletCount = kept;
            taskPayload.meshletVisibility = clamp(record.visibility, 0.0, 1.0);
        }
        EmitMeshTasksEXT(kept, 1, 1);
        return;
    }
    uint localMeshlet = min(gl_WorkGroupID.x, pc.meshletCount - 1u);
    if (lane == 0u) {
        if (pc.workQueue != uint64_t(0)) {
            TerrainWorkQueue queue = TerrainWorkQueue(pc.workQueue);
            TerrainWorkQueueRecord record = queue.records[pc.meshletOffset + localMeshlet];
            taskPayload.meshlets[0] = record.meshletIndex;
            taskPayload.meshletVisibility = 1.0;
        }
        else if (pc.visibleMeshlets != uint64_t(0)) {
            VisibleMeshlets visible = VisibleMeshlets(pc.visibleMeshlets);
            VisibleMeshletRecord record = visible.records[pc.meshletOffset + localMeshlet];
            taskPayload.meshlets[0] = record.meshletIndex;
            taskPayload.meshletVisibility = clamp(record.visibility, 0.0, 1.0);
        }
        else {
            taskPayload.meshlets[0] = pc.meshletOffset + localMeshlet;
            taskPayload.meshletVisibility = 1.0;
        }
        taskPayload.meshletCount = 1u;
    }
    EmitMeshTasksEXT(1, 1, 1);
}
