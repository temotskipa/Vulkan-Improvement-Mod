#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require
#extension GL_EXT_samplerless_texture_functions : require

// Builds the depth pyramid over the frame's depth image: level n holds, per texel, the farthest
// depth of the image's pixels whose coordinates shifted right by n are the texel's own. Depth is
// larger nearer the camera, so the farthest is the smallest. Each level is half the size of the
// one below, rounded up, and level 0, the image itself, is not stored: the buffer starts with
// level 1.
//
// One dispatch writes six levels above its source, which is the image or a level already in the
// buffer: a workgroup takes a tile of 64 by 64 of the source's texels and keeps the levels it
// works out for the tile in shared memory, so only the first is read from memory. A texel outside
// the source counts as no depth at all, which the smallest of its neighbours ignores.
layout(local_size_x = 16, local_size_y = 16) in;

const uint TILE = 64u;
const uint LEVELS_PER_DISPATCH = 6u;
const float NO_DEPTH = 3.0e38;

layout(set = 0, binding = 0) uniform texture2D depthImage;

layout(buffer_reference, scalar, buffer_reference_align = 4) buffer DepthPyramid {
    float depths[];
};

layout(push_constant, scalar) uniform DepthPyramidPushConstants {
    uint64_t pyramid;
    uvec2 imageSize;
    uint sourceLevel;
    uint levels;
} pc;

// sourceLevel: 0 for the image, else the stored level the dispatch starts from. levels: how many
// levels the buffer holds above the image.

shared float finer[(TILE / 2u) * (TILE / 2u)];
shared float coarser[(TILE / 4u) * (TILE / 4u)];

uvec2 levelSize(uint level) {
    return (pc.imageSize + (1u << level) - 1u) >> level;
}

// Where a stored level starts in the buffer.
uint levelOffset(uint level) {
    uint offset = 0u;
    for (uint below = 1u; below < level; below++) {
        uvec2 size = levelSize(below);
        offset += size.x * size.y;
    }
    return offset;
}

float sourceDepth(uvec2 texel, uvec2 sourceSize, uint sourceOffset) {
    if (any(greaterThanEqual(texel, sourceSize))) {
        return NO_DEPTH;
    }
    if (pc.sourceLevel == 0u) {
        return texelFetch(depthImage, ivec2(texel), 0).r;
    }
    return DepthPyramid(pc.pyramid).depths[sourceOffset + texel.y * sourceSize.x + texel.x];
}

void store(uint level, uvec2 texel, float depth) {
    uvec2 size = levelSize(level);
    if (level <= pc.levels && all(lessThan(texel, size))) {
        DepthPyramid(pc.pyramid).depths[levelOffset(level) + texel.y * size.x + texel.x] = depth;
    }
}

void main() {
    uvec2 local = gl_LocalInvocationID.xy;
    uvec2 tile = gl_WorkGroupID.xy;
    uvec2 sourceSize = levelSize(pc.sourceLevel);
    uint sourceOffset = pc.sourceLevel == 0u ? 0u : levelOffset(pc.sourceLevel);
    // The first level: each invocation works out two by two of its texels, from four by four of
    // the source's.
    for (uint corner = 0u; corner < 4u; corner++) {
        uvec2 first = local * 2u + uvec2(corner & 1u, corner >> 1);
        uvec2 source = tile * TILE + first * 2u;
        float depth = min(min(sourceDepth(source, sourceSize, sourceOffset), sourceDepth(source + uvec2(1u, 0u), sourceSize, sourceOffset)), min(sourceDepth(source + uvec2(0u, 1u), sourceSize, sourceOffset), sourceDepth(source + uvec2(1u, 1u), sourceSize, sourceOffset)));
        finer[first.y * (TILE / 2u) + first.x] = depth;
        store(pc.sourceLevel + 1u, tile * (TILE / 2u) + first, depth);
    }
    barrier();
    // Each level after it from the one before, in shared memory, the two arrays taking turns.
    uint width = TILE / 4u;
    for (uint step = 2u; step <= LEVELS_PER_DISPATCH; step++) {
        float depth = NO_DEPTH;
        bool working = all(lessThan(local, uvec2(width)));
        if (working) {
            uint below = width * 2u;
            uvec2 child = local * 2u;
            if ((step & 1u) == 0u) {
                depth = min(min(finer[child.y * below + child.x], finer[child.y * below + child.x + 1u]), min(finer[(child.y + 1u) * below + child.x], finer[(child.y + 1u) * below + child.x + 1u]));
            }
            else {
                depth = min(min(coarser[child.y * below + child.x], coarser[child.y * below + child.x + 1u]), min(coarser[(child.y + 1u) * below + child.x], coarser[(child.y + 1u) * below + child.x + 1u]));
            }
        }
        barrier();
        if (working) {
            if ((step & 1u) == 0u) {
                coarser[local.y * width + local.x] = depth;
            }
            else {
                finer[local.y * width + local.x] = depth;
            }
            store(pc.sourceLevel + step, tile * width + local, depth);
        }
        barrier();
        width /= 2u;
    }
}
