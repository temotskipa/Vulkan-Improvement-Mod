#version 460
#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

layout(local_size_x = 32) in;
layout(triangles, max_vertices = 64, max_primitives = 32) out;

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

struct BlockVertex {
    vec3 position;
    uint color;
    vec2 uv0;
    uint uv2;
};

struct MaterialRecord {
    int materialId;
    int flags;
    int blockAtlasWidth;
    int blockAtlasHeight;
    int blockAtlasBaseMip;
    int blockAtlasMipLevels;
    int lightmapWidth;
    int lightmapHeight;
    int normalTextureIndex;
    int specularTextureIndex;
    int emissionTextureIndex;
    int tintFlags;
    int alphaMode;
    int renderLayerOrdinal;
    int materialDomain;
    int reserved0;
};

const int INDEXED_TRIANGLES_FLAG = 1;

// Terrain is lit per vertex, as Minecraft's own terrain is, and the fragment stage is handed the
// lit colour. The lightmap is 16 texels a side and a light level addresses the centre of one.
layout(set = 0, binding = 2) uniform texture2D lightmapTexture;
layout(set = 0, binding = 3) uniform sampler lightmapSampler;
const float LIGHTMAP_HALF_TEXEL = 0.5 / 16.0;
const int MATERIAL_TABLE_CAPACITY = 256;

layout(buffer_reference, scalar, buffer_reference_align = 8) readonly buffer MeshletHeaders {
    MeshletHeader headers[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer TerrainVertices {
    BlockVertex vertices[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer TerrainIndices {
    uint indices[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer MaterialTable {
    MaterialRecord records[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) buffer TerrainDebugCounters {
    uint candidateMeshlets;
    uint culledMeshlets;
    uint emittedMeshlets;
    uint reserved;
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

const uint MESHLETS_PER_TASK = 32u;
const uint CULL_MESHLET_FRUSTUM_IN_MESH_STAGE = 1u;

// The meshlets the task stage handed on, in the order they are to be drawn.
struct TaskPayload {
    float meshletVisibility;
    uint meshletCount;
    uint meshlets[MESHLETS_PER_TASK];
};

taskPayloadSharedEXT TaskPayload taskPayload;
layout(location = 0) out vec4 meshColor[];
layout(location = 1) out vec2 meshUv0[];
layout(location = 3) out flat uint meshMaterialFlags[];
// x: distance from the camera. y: the larger of the horizontal distance and the height difference.
// z: how far the section has faded in.
layout(location = 4) out vec3 meshFog[];

// A section this close to the camera is shown in full even while it is still fading in.
const float SECTION_FADE_FREE_DISTANCE = 16.0;

// Where a point of a section is, seen from the camera. The section's block coordinates and the
// camera's are subtracted as integers, so the result is as exact a million blocks from the world
// origin as it is next to it.
vec3 cameraRelative(ivec3 section, vec3 local) {
    return vec3(section * 16 - pc.cameraBlock.xyz) + (local - pc.cameraOffset.xyz);
}

vec4 projectRelative(vec3 relative) {
    vec4 value = vec4(relative, 1.0);
    return vec4(
            dot(pc.cameraRight, value),
            dot(pc.cameraUp, value),
            dot(pc.cameraForward, value),
            dot(pc.projection, value)
    );
}

bool cullMeshlet(MeshletHeader header) {
    vec3 relative = cameraRelative(ivec3(header.sectionX, header.sectionY, header.sectionZ), vec3(8.0));
    vec4 clip = projectRelative(relative);
    float margin = 14.0;
    return clip.w <= 0.0 || clip.z < -margin || clip.z > clip.w + margin || abs(clip.x) > abs(clip.w) + margin || abs(clip.y) > abs(clip.w) + margin;
}

void main() {
    uint lane = gl_LocalInvocationID.x;
    MeshletHeaders meshlets = MeshletHeaders(pc.meshletHeaders);
    TerrainDebugCounters counters = TerrainDebugCounters(pc.debugCounters);
    MeshletHeader header = meshlets.headers[taskPayload.meshlets[gl_WorkGroupID.x]];
    bool indexedTriangles = (header.flags0 & INDEXED_TRIANGLES_FLAG) != 0 && header.indexBytes > 0;
    // Nothing is counted for every meshlet: an atomic per workgroup on one address made the
    // opaque layers several times slower than vanilla's.
    if ((pc.cullFlags & CULL_MESHLET_FRUSTUM_IN_MESH_STAGE) != 0u && cullMeshlet(header)) {
        if (lane == 0u) {
            atomicAdd(counters.culledMeshlets, 1u);
            SetMeshOutputsEXT(0u, 0u);
        }
        return;
    }

    uint vertexCount = indexedTriangles ? uint(clamp(header.indexCount, 0, 64)) : uint(clamp(header.vertexCount, 0, 64));
    uint primitiveCount = indexedTriangles ? min(vertexCount / 3u, 32u) : min(vertexCount / 4u * 2u, 32u);
    TerrainVertices payload = TerrainVertices(
            pc.vertexPayload
            + uint64_t(header.vertexByteOffset)
    );
    TerrainIndices indices = TerrainIndices(
            pc.indexPayload
            + uint64_t(header.indexByteOffset)
    );
    uint materialIndex = uint(clamp(header.materialId, 0, MATERIAL_TABLE_CAPACITY - 1));
    uint materialFlags = 3u;
    if (pc.materialTable != uint64_t(0)) {
        MaterialTable materials = MaterialTable(pc.materialTable);
        materialFlags = uint(materials.records[materialIndex].flags);
    }

    if (lane == 0u) {
        SetMeshOutputsEXT(vertexCount, primitiveCount);
    }
    for (uint vertex = lane; vertex < vertexCount; vertex += gl_WorkGroupSize.x) {
        uint sourceVertex = indexedTriangles
        ? indices.indices[uint(max(header.firstIndex, 0)) + vertex]
        : uint(max(header.firstVertex, 0)) + vertex;
        BlockVertex blockVertex = payload.vertices[sourceVertex];
        vec3 relative = cameraRelative(ivec3(header.sectionX, header.sectionY, header.sectionZ), blockVertex.position);
        gl_MeshVerticesEXT[vertex].gl_Position = projectRelative(relative);
        vec2 lightUv = vec2(float(blockVertex.uv2 & 65535u), float((blockVertex.uv2 >> 16) & 65535u)) / 256.0;
        vec4 light = textureLod(sampler2D(lightmapTexture, lightmapSampler), clamp(lightUv + LIGHTMAP_HALF_TEXEL, LIGHTMAP_HALF_TEXEL, 1.0 - LIGHTMAP_HALF_TEXEL), 0.0);
        meshColor[vertex] = unpackUnorm4x8(blockVertex.color) * light;
        float cameraDistance = length(relative);
        float fadeApplies = clamp((cameraDistance - SECTION_FADE_FREE_DISTANCE) / SECTION_FADE_FREE_DISTANCE, 0.0, 1.0);
        meshFog[vertex] = vec3(
                cameraDistance,
                max(length(relative.xz), abs(relative.y)),
                mix(1.0, clamp(taskPayload.meshletVisibility, 0.0, 1.0), fadeApplies)
        );
        meshUv0[vertex] = blockVertex.uv0;
        meshMaterialFlags[vertex] = materialFlags;
    }

    if (indexedTriangles) {
        for (uint primitive = lane; primitive < primitiveCount; primitive += gl_WorkGroupSize.x) {
            uint baseVertex = primitive * 3u;
            gl_PrimitiveTriangleIndicesEXT[primitive] = uvec3(baseVertex, baseVertex + 1u, baseVertex + 2u);
        }
    } else {
        uint quadCount = vertexCount / 4u;
        for (uint quad = lane; quad < quadCount; quad += gl_WorkGroupSize.x) {
            uint baseVertex = quad * 4u;
            uint primitive = quad * 2u;
            gl_PrimitiveTriangleIndicesEXT[primitive] = uvec3(baseVertex, baseVertex + 1u, baseVertex + 2u);
            gl_PrimitiveTriangleIndicesEXT[primitive + 1u] = uvec3(baseVertex, baseVertex + 2u, baseVertex + 3u);
        }
    }
}