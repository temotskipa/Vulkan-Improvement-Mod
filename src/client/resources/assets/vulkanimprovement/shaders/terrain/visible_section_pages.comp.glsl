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
//
// A frame that culls by occlusion draws in two phases. The passes above then leave out the pages
// that were hidden in the frame before, and once those drawn have their depth in the depth
// pyramid, a fourth pass tests every page in view against it: it writes the draws of the pages
// that are seen now and were left out, and marks each page for the next frame.
//
// A fifth pass tests boxes the CPU lists, those of entities and block entities, against the same
// pyramid, and writes one word for each that the CPU reads a few frames later.
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
const uint PASS_LATE = 3u;
const uint PASS_BOXES = 4u;

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
    int occluded;
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

// A box the fifth pass tests, relative to the camera.
struct TestBox {
    vec3 low;
    vec3 high;
};

// Only a page's occluded member is written, by the fourth pass; a page copied in has it clear.
layout(buffer_reference, scalar, buffer_reference_align = 8) buffer PageTable {
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

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer DepthPyramid {
    float depths[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer TestBoxes {
    TestBox boxes[];
};

// One word per box the fifth pass tests: 1 for a box that is hidden, 0 for one that is not.
layout(buffer_reference, scalar, buffer_reference_align = 4) writeonly buffer BoxResults {
    uint hidden[];
};

// How many pages the fourth pass has tested, found hidden, and drawn, since the counters were cleared.
layout(buffer_reference, scalar, buffer_reference_align = 4) buffer OcclusionCounters {
    uint testedPages;
    uint hiddenPages;
    uint latePages;
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
    uint64_t depthPyramid;
    uint64_t occlusionCounters;
    uvec4 pyramid;
    ivec4 cameraBlock;
    vec4 cameraOffset;
    vec4 cameraRight;
    vec4 cameraUp;
    vec4 cameraForward;
    vec4 projection;
} pc;

// boxMin.w: how many slots of the page table are in use. boxSize.w: the time now, and
// cameraSection.w: how long a section takes to fade in, both in milliseconds; 0 for no fade.
// pyramid.xy: the size of the depth image the pyramid is built over, pyramid.z: how many levels it
// stores above the image, 0 for none, and pyramid.w: whether the frame culls by occlusion. The
// camera's rows are those of Minecraft's view-projection matrix, applied to positions relative to
// the camera. The fifth pass reads frameRecord boxes at records and writes their words at
// meshTaskCommands; it reads no page.

// The farthest depth of a level's texels from low to high, which are at most one apart each way.
// The pyramid stores level 1 first: level 0 would be the depth image itself.
float farthestDepth(uint level, uvec2 low, uvec2 high) {
    DepthPyramid pyramid = DepthPyramid(pc.depthPyramid);
    uint offset = 0u;
    uvec2 size = (pc.pyramid.xy + 1u) >> 1;
    for (uint below = 1u; below < level; below++) {
        offset += size.x * size.y;
        size = (size + 1u) >> 1;
    }
    uint lowRow = offset + low.y * size.x;
    uint highRow = offset + high.y * size.x;
    return min(min(pyramid.depths[lowRow + low.x], pyramid.depths[lowRow + high.x]), min(pyramid.depths[highRow + low.x], pyramid.depths[highRow + high.x]));
}

// Whether a box, given relative to the camera, is behind what the depth pyramid holds everywhere
// it covers on the screen. Depth is larger nearer the camera, so the box is hidden when its nearest
// point is farther than the farthest depth drawn over its rectangle. A box that reaches behind the
// camera is never hidden, and without a pyramid nothing is. Nor, with wholeOnScreen, is a box that
// reaches past the edge of the screen: a result used in later frames cannot vouch for what this
// frame did not draw, which the camera may have turned towards by then.
bool hiddenByDepth(vec3 low, vec3 high, bool wholeOnScreen) {
    if (pc.pyramid.z == 0u) {
        return false;
    }
    vec2 screenLow = vec2(1.0e30);
    vec2 screenHigh = vec2(-1.0e30);
    float nearest = 0.0;
    for (uint corner = 0u; corner < 8u; corner++) {
        vec4 point = vec4(mix(low, high, vec3(float(corner & 1u), float((corner >> 1) & 1u), float((corner >> 2) & 1u))), 1.0);
        float w = dot(pc.projection, point);
        if (w <= 0.0) {
            return false;
        }
        vec2 xy = vec2(dot(pc.cameraRight, point), dot(pc.cameraUp, point)) / w;
        screenLow = min(screenLow, xy);
        screenHigh = max(screenHigh, xy);
        nearest = max(nearest, dot(pc.cameraForward, point) / w);
    }
    if (wholeOnScreen && (any(lessThan(screenLow, vec2(-1.0))) || any(greaterThan(screenHigh, vec2(1.0))))) {
        return false;
    }
    // The pixels the rectangle touches, as Vulkan maps the view onto the image.
    vec2 size = vec2(pc.pyramid.xy);
    uvec2 lowPixel = uvec2(clamp((screenLow * 0.5 + 0.5) * size, vec2(0.0), size - 1.0));
    uvec2 highPixel = uvec2(clamp((screenHigh * 0.5 + 0.5) * size, vec2(0.0), size - 1.0));
    // A texel of level n covers the pixels whose coordinates shifted right by n are its own. The
    // level read is the first stored one at which the rectangle spans at most two texels each way.
    uint level = 1u;
    while (any(greaterThan((highPixel >> level) - (lowPixel >> level), uvec2(1u)))) {
        if (level >= pc.pyramid.z) {
            return false;
        }
        level++;
    }
    return nearest < farthestDepth(level, lowPixel >> level, highPixel >> level);
}

// As Minecraft fades a section in: by the time since it was first shown.
float fadeVisibility(PageTable table, uint slot) {
    if (pc.cameraSection.w <= 0) {
        return 1.0;
    }
    return clamp(float(pc.boxSize.w - table.pages[slot].fadeStart) / float(pc.cameraSection.w), 0.0, 1.0);
}

void writeTasks(uint first, uint slot, uint tasks, float visibility) {
    TaskRecords drawn = TaskRecords(pc.records);
    for (uint task = 0u; task < tasks; task++) {
        drawn.records[first + task] = VisibleMeshletRecord(slot | task << TASK_RECORD_PAGE_BITS, visibility);
    }
}

// Whether a slot holds a page of a layer the frame draws, of a section in view, and if so the
// page's layer, section and number of tasks.
bool pageInView(PageTable table, FrameData frame, uint slot, out uint layer, out ivec3 section, out uint tasks) {
    layer = 0u;
    section = ivec3(0);
    tasks = 0u;
    if (slot >= uint(pc.boxMin.w)) {
        return false;
    }
    // A slot no page holds was cleared, so it has no meshlets.
    int meshletCount = table.pages[slot].meshletCount;
    if (meshletCount <= 0 || meshletCount > MAX_PAGE_MESHLETS) {
        return false;
    }
    layer = uint(table.pages[slot].layer);
    if (layer >= LAYERS || pc.layerCommand[layer] == NO_COMMAND) {
        return false;
    }
    section = ivec3(table.pages[slot].sectionX, table.pages[slot].sectionY, table.pages[slot].sectionZ);
    ivec3 cell = section - pc.boxMin.xyz;
    if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, pc.boxSize.xyz))) {
        return false;
    }
    uint bit = uint((cell.y * pc.boxSize.z + cell.z) * pc.boxSize.x + cell.x);
    if ((frame.sectionsInView[bit >> 5] & (1u << (bit & 31u))) == 0u) {
        return false;
    }
    tasks = (uint(meshletCount) + MESHLETS_PER_TASK - 1u) / MESHLETS_PER_TASK;
    return true;
}

// The fourth pass for one slot: tests its page against the depth pyramid, marks it for the next
// frame, and writes its tasks if it is seen now and the first phase left it out. Says whether a
// page was tested, found hidden, and drawn.
uvec3 testPage(PageTable table, FrameData frame, uint slot) {
    uint layer;
    ivec3 section;
    uint tasks;
    if (!pageInView(table, frame, slot, layer, section, tasks)) {
        return uvec3(0u);
    }
    bool hiddenBefore = table.pages[slot].occluded != 0;
    // A section's mesh may reach a little past its sixteen blocks, so its box has a margin.
    vec3 sectionLow = vec3(section * 16 - pc.cameraBlock.xyz) - pc.cameraOffset.xyz;
    bool hidden = hiddenByDepth(sectionLow - 2.0, sectionLow + 18.0, false);
    table.pages[slot].occluded = hidden ? 1 : 0;
    // A page that was not hidden before was drawn in the first phase.
    if (hidden || !hiddenBefore) {
        return uvec3(1u, hidden ? 1u : 0u, 0u);
    }
    uint first = pc.layerOutput[layer] + atomicAdd(MeshTaskCommands(pc.meshTaskCommands).commands[pc.layerCommand[layer]].groupCountX, tasks);
    writeTasks(first, slot, tasks, fadeVisibility(table, slot));
    return uvec3(1u, 0u, 1u);
}

// The fourth pass's counts of a workgroup, added to the counters once per workgroup.
shared uint occlusionCounts[3];

void main() {
    if (pc.pass == PASS_BOXES) {
        uint box = gl_GlobalInvocationID.x;
        if (box < pc.frameRecord) {
            TestBox tested = TestBoxes(pc.records).boxes[box];
            BoxResults(pc.meshTaskCommands).hidden[box] = hiddenByDepth(tested.low, tested.high, true) ? 1u : 0u;
        }
        return;
    }
    FrameData frame = FrameData(pc.records + uint64_t(pc.frameRecord) * 8ul);
    PageTable table = PageTable(pc.pageTable);
    if (pc.pass == PASS_LATE) {
        if (gl_LocalInvocationIndex == 0u) {
            occlusionCounts[0] = 0u;
            occlusionCounts[1] = 0u;
            occlusionCounts[2] = 0u;
        }
        barrier();
        uvec3 counted = testPage(table, frame, gl_GlobalInvocationID.x);
        if (counted.x != 0u) {
            atomicAdd(occlusionCounts[0], counted.x);
            atomicAdd(occlusionCounts[1], counted.y);
            atomicAdd(occlusionCounts[2], counted.z);
        }
        barrier();
        if (gl_LocalInvocationIndex == 0u && occlusionCounts[0] != 0u && pc.occlusionCounters != uint64_t(0)) {
            OcclusionCounters counters = OcclusionCounters(pc.occlusionCounters);
            atomicAdd(counters.testedPages, occlusionCounts[0]);
            atomicAdd(counters.hiddenPages, occlusionCounts[1]);
            atomicAdd(counters.latePages, occlusionCounts[2]);
        }
        return;
    }
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
    uint layer;
    ivec3 section;
    uint tasks;
    if (!pageInView(table, frame, slot, layer, section, tasks)) {
        return;
    }
    // A frame that culls by occlusion leaves the pages hidden in the frame before to the fourth pass.
    if (pc.pyramid.w != 0u && table.pages[slot].occluded != 0) {
        return;
    }
    ivec3 away = abs(section - pc.cameraSection.xyz);
    uint distance = min(uint(max(away.x, max(away.y, away.z))), DISTANCES - 1u);
    uint counter = layer * DISTANCES + distance;
    if (pc.pass == PASS_COUNT) {
        atomicAdd(frame.distanceTasks[counter], tasks);
        atomicAdd(MeshTaskCommands(pc.meshTaskCommands).commands[pc.layerCommand[layer]].groupCountX, tasks);
        return;
    }

    uint first = pc.layerOutput[layer] + atomicAdd(frame.distanceCursors[counter], tasks);
    writeTasks(first, slot, tasks, fadeVisibility(table, slot));
}
