#version 460
#extension GL_EXT_buffer_reference : require

// Writes the vertices Minecraft's ModelPart.Cube.compile writes, for every cube of a frame's
// models: one invocation per face. The records are ModelGpuRecords; the word counts below are
// checked against them by the build.

const uint WORKGROUP_SIZE = 64u;
const uint MAX_CUBE_POLYGONS = 6u;
const uint POLYGON_VERTICES = 4u;

const uint POLYGON_WORDS = 24u;
const uint CORNER_WORDS = 5u;
const uint POLYGON_DIRECTION = 20u;

const uint PART_WORDS = 24u;
const uint PART_NORMALS = 12u;
const uint PART_COLOR = 18u;
const uint PART_OVERLAY = 19u;
const uint PART_LIGHT = 20u;

const uint JOB_WORDS = 4u;
const uint DRAW_BITS = 24u;

// DefaultVertexFormat.ENTITY: position, color, texture coordinate, overlay, light, normal.
const uint VERTEX_WORDS = 9u;

layout(local_size_x = 64) in;

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer Words {
    uint words[];
};

layout(buffer_reference, std430, buffer_reference_align = 4) writeonly buffer VertexWords {
    uint words[];
};

layout(push_constant, std430) uniform ModelVertexPass {
    Words polygons;
    Words parts;
    Words jobs;
    Words drawBases;
    VertexWords vertices;
    uint firstJob;
    uint jobCount;
} pass;

void main() {
    uint invocation = gl_GlobalInvocationID.x;
    uint jobInDispatch = invocation / MAX_CUBE_POLYGONS;
    if (jobInDispatch >= pass.jobCount) {
        return;
    }
    uint polygonInCube = invocation - jobInDispatch * MAX_CUBE_POLYGONS;
    uint job = (pass.firstJob + jobInDispatch) * JOB_WORDS;
    uint drawAndPolygons = pass.jobs.words[job + 2u];
    if (polygonInCube >= (drawAndPolygons >> DRAW_BITS)) {
        return;
    }
    uint part = pass.jobs.words[job] * PART_WORDS;
    uint polygon = (pass.jobs.words[job + 1u] + polygonInCube) * POLYGON_WORDS;
    uint draw = drawAndPolygons & ((1u << DRAW_BITS) - 1u);
    uint firstVertex = pass.drawBases.words[draw] + pass.jobs.words[job + 3u] + polygonInCube * POLYGON_VERTICES;

    float m00 = uintBitsToFloat(pass.parts.words[part + 0u]);
    float m01 = uintBitsToFloat(pass.parts.words[part + 1u]);
    float m02 = uintBitsToFloat(pass.parts.words[part + 2u]);
    float m10 = uintBitsToFloat(pass.parts.words[part + 3u]);
    float m11 = uintBitsToFloat(pass.parts.words[part + 4u]);
    float m12 = uintBitsToFloat(pass.parts.words[part + 5u]);
    float m20 = uintBitsToFloat(pass.parts.words[part + 6u]);
    float m21 = uintBitsToFloat(pass.parts.words[part + 7u]);
    float m22 = uintBitsToFloat(pass.parts.words[part + 8u]);
    float m30 = uintBitsToFloat(pass.parts.words[part + 9u]);
    float m31 = uintBitsToFloat(pass.parts.words[part + 10u]);
    float m32 = uintBitsToFloat(pass.parts.words[part + 11u]);
    uint normal = pass.parts.words[part + PART_NORMALS + pass.polygons.words[polygon + POLYGON_DIRECTION]];
    uint color = pass.parts.words[part + PART_COLOR];
    uint overlay = pass.parts.words[part + PART_OVERLAY];
    uint light = pass.parts.words[part + PART_LIGHT];

    for (uint corner = 0u; corner < POLYGON_VERTICES; corner++) {
        uint source = polygon + corner * CORNER_WORDS;
        float x = uintBitsToFloat(pass.polygons.words[source]);
        float y = uintBitsToFloat(pass.polygons.words[source + 1u]);
        float z = uintBitsToFloat(pass.polygons.words[source + 2u]);
        // Matrix4f.transformPosition, as JOML evaluates it without fused multiply-adds: precise
        // keeps the compiler from fusing or reordering, so the result is Minecraft's to the bit.
        precise float outX = m00 * x + (m10 * y + (m20 * z + m30));
        precise float outY = m01 * x + (m11 * y + (m21 * z + m31));
        precise float outZ = m02 * x + (m12 * y + (m22 * z + m32));
        uint target = (firstVertex + corner) * VERTEX_WORDS;
        pass.vertices.words[target] = floatBitsToUint(outX);
        pass.vertices.words[target + 1u] = floatBitsToUint(outY);
        pass.vertices.words[target + 2u] = floatBitsToUint(outZ);
        pass.vertices.words[target + 3u] = color;
        pass.vertices.words[target + 4u] = pass.polygons.words[source + 3u];
        pass.vertices.words[target + 5u] = pass.polygons.words[source + 4u];
        pass.vertices.words[target + 6u] = overlay;
        pass.vertices.words[target + 7u] = light;
        pass.vertices.words[target + 8u] = normal;
    }
}
