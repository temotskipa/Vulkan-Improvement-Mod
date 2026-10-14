#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

// Decides which of a layer's pages are drawn this frame and writes the mesh-task command that
// draws them. One invocation takes one task of a page in the frame's visible list, tests its section
// against the view frustum, and appends the page to the layer's draw list. The command's task
// count is the length of that list: it is counted up here and never read back by the CPU.
layout(local_size_x = 64) in;

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

struct VisiblePage {
    uint pageIndex;
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

layout(buffer_reference, scalar, buffer_reference_align = 4) buffer VisiblePages {
    VisiblePage records[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) buffer MeshTaskCommands {
    MeshTaskCommand commands[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) buffer VisibilityCounters {
    uint inputPages;
    uint culledPages;
    uint visiblePages;
    uint reserved;
};

layout(push_constant, scalar) uniform CommandPushConstants {
    uint64_t pageTable;
    uint64_t visiblePages;
    uint64_t meshTaskCommands;
    uint64_t counters;
    uint inputOffset;
    uint outputOffset;
    uint pageCount;
    uint commandIndex;
    uint reserved0;
    uint reserved1;
    uint reserved2;
    uint reserved3;
    ivec4 cameraBlock;
    vec4 cameraOffset;
    vec4 cameraRight;
    vec4 cameraUp;
    vec4 cameraForward;
    vec4 projection;
} pc;

// A record names its page in the low bits and which of the page's tasks it is above them.
const uint TASK_RECORD_PAGE_BITS = 26u;

// A section's mesh may reach a little past its sixteen blocks, so the box that is tested is this
// much larger on every side. Minecraft tests the box without a margin when it collects a frame's
// sections, so this test never drops a section Minecraft would draw from where the camera is.
const float SECTION_MARGIN = 2.0;

// Whether a section is wholly outside the view: every corner of its box lies beyond the same
// side of the frustum, or behind the camera. The camera's block is subtracted as integers first.
bool outsideFrustum(ivec3 section) {
    vec3 low = vec3(section * 16 - pc.cameraBlock.xyz) - pc.cameraOffset.xyz - SECTION_MARGIN;
    float size = 16.0 + 2.0 * SECTION_MARGIN;
    uint beyondEverywhere = 31u;
    for (uint corner = 0u; corner < 8u; corner++) {
        vec4 point = vec4(low + size * vec3(float(corner & 1u), float((corner >> 1) & 1u), float((corner >> 2) & 1u)), 1.0);
        float x = dot(pc.cameraRight, point);
        float y = dot(pc.cameraUp, point);
        float w = dot(pc.projection, point);
        uint beyond = (x < -w ? 1u : 0u) | (x > w ? 2u : 0u) | (y < -w ? 4u : 0u) | (y > w ? 8u : 0u) | (w <= 0.0 ? 16u : 0u);
        beyondEverywhere &= beyond;
    }
    return beyondEverywhere != 0u;
}

void main() {
    uint index = gl_GlobalInvocationID.x;
    if (index >= pc.pageCount) {
        return;
    }
    VisiblePages list = VisiblePages(pc.visiblePages);
    VisibilityCounters counters = VisibilityCounters(pc.counters);
    VisiblePage visible = list.records[pc.inputOffset + index];
    ResidentPage page = PageTable(pc.pageTable).pages[visible.pageIndex & ((1u << TASK_RECORD_PAGE_BITS) - 1u)];
    atomicAdd(counters.inputPages, 1u);
    if (outsideFrustum(ivec3(page.sectionX, page.sectionY, page.sectionZ))) {
        atomicAdd(counters.culledPages, 1u);
        return;
    }
    MeshTaskCommands commands = MeshTaskCommands(pc.meshTaskCommands);
    uint drawn = atomicAdd(commands.commands[pc.commandIndex].groupCountX, 1u);
    list.records[pc.outputOffset + drawn] = visible;
    atomicAdd(counters.visiblePages, 1u);
}
