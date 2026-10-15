#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

// Writes the draws of a frame's opaque terrain from the GPU's own page table, so the CPU never
// looks at a page: it only says which sections are in view, one bit per section of the box around
// them. Every page of a section in view gets its tasks, one per MESHLETS_PER_TASK of its meshlets,
// and each layer's mesh-task command counts them. The tasks of a layer are laid out by how far
// their section is from the camera's, nearest first, which lets the GPU reject hidden terrain by
// depth about as early as Minecraft's own order does. That takes three passes: the first counts a
// layer's tasks at each distance, the second works out where each distance begins, and the third
// writes the records there.
layout(local_size_x = 64) in;

const uint MESHLETS_PER_TASK = 32u;
// A task's record names its page in the low bits and which of the page's tasks it is above them.
const uint TASK_RECORD_PAGE_BITS = 26u;
const int MAX_PAGE_MESHLETS = 2048;
const uint LAYERS = 4u;
const uint DISTANCES = 64u;
const uint NO_COMMAND = 0xFFFFFFFFu;
const uint PASS_COUNT = 0u;
const uint PASS_PLACE = 1u;

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
    uint64_t vertexByteOffset;
    uint64_t indexByteOffset;
    int fadeStart;
    int customIndexBuffer;
    int reserved1;
    int reserved2;
};

struct VisibleMeshletRecord {
    uint meshletIndex;
    float visibility;
};

struct MeshTaskCommand {
    uint groupCountX;
    uint groupCountY;
    uint groupCountZ;
};

layout(buffer_reference, scalar, buffer_reference_align = 8) readonly buffer PageTable {
    ResidentPage pages[];
};

// What the CPU writes for the frame: the counters, cleared, then the sections in view.
layout(buffer_reference, scalar, buffer_reference_align = 8) buffer FrameData {
    uint distanceTasks[LAYERS * DISTANCES];
    uint distanceCursors[LAYERS * DISTANCES];
    uint sectionsInView[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) writeonly buffer TaskRecords {
    VisibleMeshletRecord records[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) buffer MeshTaskCommands {
    MeshTaskCommand commands[];
};

layout(push_constant, scalar) uniform SectionPagePushConstants {
    uint64_t pageTable;
    uint64_t records;
    uint64_t meshTaskCommands;
    uint frameRecord;
    uint pass;
    ivec4 boxMin;
    ivec4 boxSize;
    ivec4 cameraSection;
    uvec4 layerOutput;
    uvec4 layerCommand;
} pc;

// boxMin.w: how many slots of the page table are in use. boxSize.w: the time now, and
// cameraSection.w: how long a section takes to fade in, both in milliseconds; 0 for no fade.

void main() {
    FrameData frame = FrameData(pc.records + uint64_t(pc.frameRecord) * 8ul);
    if (pc.pass == PASS_PLACE) {
        // One invocation per distance: its tasks begin where the nearer ones' end.
        uint distance = gl_GlobalInvocationID.x;
        if (distance >= DISTANCES) {
            return;
        }
        for (uint layer = 0u; layer < LAYERS; layer++) {
            uint first = 0u;
            for (uint nearer = 0u; nearer < distance; nearer++) {
                first += frame.distanceTasks[layer * DISTANCES + nearer];
            }
            frame.distanceCursors[layer * DISTANCES + distance] = first;
        }
        return;
    }

    uint slot = gl_GlobalInvocationID.x;
    if (slot >= uint(pc.boxMin.w)) {
        return;
    }
    PageTable table = PageTable(pc.pageTable);
    // A slot no page holds was cleared, so it has no meshlets.
    int meshletCount = table.pages[slot].meshletCount;
    if (meshletCount <= 0 || meshletCount > MAX_PAGE_MESHLETS) {
        return;
    }
    uint layer = uint(table.pages[slot].layer);
    if (layer >= LAYERS || pc.layerCommand[layer] == NO_COMMAND) {
        return;
    }
    ivec3 section = ivec3(table.pages[slot].sectionX, table.pages[slot].sectionY, table.pages[slot].sectionZ);
    ivec3 cell = section - pc.boxMin.xyz;
    if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, pc.boxSize.xyz))) {
        return;
    }
    uint bit = uint((cell.y * pc.boxSize.z + cell.z) * pc.boxSize.x + cell.x);
    if ((frame.sectionsInView[bit >> 5] & (1u << (bit & 31u))) == 0u) {
        return;
    }
    uint tasks = (uint(meshletCount) + MESHLETS_PER_TASK - 1u) / MESHLETS_PER_TASK;
    ivec3 away = abs(section - pc.cameraSection.xyz);
    uint distance = min(uint(max(away.x, max(away.y, away.z))), DISTANCES - 1u);
    uint counter = layer * DISTANCES + distance;
    if (pc.pass == PASS_COUNT) {
        atomicAdd(frame.distanceTasks[counter], tasks);
        atomicAdd(MeshTaskCommands(pc.meshTaskCommands).commands[pc.layerCommand[layer]].groupCountX, tasks);
        return;
    }

    uint first = pc.layerOutput[layer] + atomicAdd(frame.distanceCursors[counter], tasks);
    // As Minecraft fades a section in: by the time since it was first shown.
    float visibility = 1.0;
    if (pc.cameraSection.w > 0) {
        visibility = clamp(float(pc.boxSize.w - table.pages[slot].fadeStart) / float(pc.cameraSection.w), 0.0, 1.0);
    }
    TaskRecords drawn = TaskRecords(pc.records);
    for (uint task = 0u; task < tasks; task++) {
        drawn.records[first + task] = VisibleMeshletRecord(slot | task << TASK_RECORD_PAGE_BITS, visibility);
    }
}
