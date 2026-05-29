#version 460
#extension GL_EXT_mesh_shader : require
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

layout(local_size_x = 1) in;
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
    uint meshletCount;
    uint debugFlags;
    uint meshletOffset;
    int layerOrdinal;
    vec4 cameraPosition;
    vec4 cameraRight;
    vec4 cameraUp;
    vec4 cameraForward;
    vec4 projection;
} pc;

struct TaskPayload {
    uint visibleMeshlet;
    float meshletVisibility;
};

taskPayloadSharedEXT TaskPayload taskPayload;
layout(location = 0) out vec4 meshColor[];
layout(location = 1) out vec2 meshUv0[];
layout(location = 2) out vec2 meshLightUv[];
layout(location = 3) out flat uint meshMaterialFlags[];

bool cullMeshlet(MeshletHeader header) {
    vec3 center = vec3(
    float(header.sectionX * 16) + 8.0,
    float(header.sectionY * 16) + 8.0,
    float(header.sectionZ * 16) + 8.0
    );
    vec3 relative = center - pc.cameraPosition.xyz;
    float viewX = dot(relative, pc.cameraRight.xyz);
    float viewY = dot(relative, pc.cameraUp.xyz);
    float viewZ = dot(relative, pc.cameraForward.xyz);
    float focal = max(pc.projection.x, 0.01);
    float aspect = max(pc.projection.y, 0.01);
    float nearPlane = max(pc.projection.z, 0.01);
    float farPlane = max(pc.projection.w, nearPlane + 1.0);
    float radius = 14.0;
    float positiveZ = max(viewZ, nearPlane);
    float maxX = positiveZ * aspect / focal + radius;
    float maxY = positiveZ / focal + radius;
    return viewZ < -radius || viewZ > farPlane + radius || abs(viewX) > maxX || abs(viewY) > maxY;
}

void main() {
    MeshletHeaders meshlets = MeshletHeaders(pc.meshletHeaders);
    TerrainDebugCounters counters = TerrainDebugCounters(pc.debugCounters);
    MeshletHeader header = meshlets.headers[taskPayload.visibleMeshlet];
    bool indexedTriangles = (header.flags0 & INDEXED_TRIANGLES_FLAG) != 0 && header.indexBytes > 0;
    atomicAdd(counters.candidateMeshlets, 1u);
    if (cullMeshlet(header)) {
        atomicAdd(counters.culledMeshlets, 1u);
        SetMeshOutputsEXT(0u, 0u);
        return;
    }
    atomicAdd(counters.emittedMeshlets, 1u);

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

    SetMeshOutputsEXT(vertexCount, primitiveCount);
    for (uint vertex = 0u; vertex < vertexCount; vertex++) {
        uint sourceVertex = indexedTriangles
        ? indices.indices[uint(max(header.firstIndex, 0)) + vertex]
        : uint(max(header.firstVertex, 0)) + vertex;
        BlockVertex blockVertex = payload.vertices[sourceVertex];
        vec3 world = vec3(
        float(header.sectionX * 16) + blockVertex.position.x,
        float(header.sectionY * 16) + blockVertex.position.y,
        float(header.sectionZ * 16) + blockVertex.position.z
        );
        vec3 relative = world - pc.cameraPosition.xyz;
        float viewX = dot(relative, pc.cameraRight.xyz);
        float viewY = dot(relative, pc.cameraUp.xyz);
        float viewZ = dot(relative, pc.cameraForward.xyz);
        float focal = max(pc.projection.x, 0.01);
        float aspect = max(pc.projection.y, 0.01);
        gl_MeshVerticesEXT[vertex].gl_Position = vec4(viewX * focal / aspect, viewY * focal, viewZ * 0.2, viewZ);
        vec4 vertexColor = unpackUnorm4x8(blockVertex.color);
        meshColor[vertex] = vec4(vertexColor.rgb, vertexColor.a * clamp(taskPayload.meshletVisibility, 0.0, 1.0));
        meshUv0[vertex] = blockVertex.uv0;
        meshLightUv[vertex] = vec2(
        float(blockVertex.uv2 & 65535u) / 256.0,
        float((blockVertex.uv2 >> 16) & 65535u) / 256.0
        );
        meshMaterialFlags[vertex] = materialFlags;
    }

    if (indexedTriangles) {
        for (uint primitive = 0u; primitive < primitiveCount; primitive++) {
            uint baseVertex = primitive * 3u;
            gl_PrimitiveTriangleIndicesEXT[primitive] = uvec3(baseVertex, baseVertex + 1u, baseVertex + 2u);
        }
    } else {
        for (uint quad = 0u; quad < vertexCount / 4u; quad++) {
            uint baseVertex = quad * 4u;
            uint primitive = quad * 2u;
            gl_PrimitiveTriangleIndicesEXT[primitive] = uvec3(baseVertex, baseVertex + 1u, baseVertex + 2u);
            gl_PrimitiveTriangleIndicesEXT[primitive + 1u] = uvec3(baseVertex, baseVertex + 2u, baseVertex + 3u);
        }
    }
}
