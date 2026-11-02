#version 460
#extension GL_EXT_buffer_reference : require

// Writes the vertices of text laid out once: the quads Minecraft's glyphs write under no pose,
// through the pose of each text drawn, one invocation per quad. The word counts below are checked
// against the records by the build.

const uint WORKGROUP_SIZE = 64u;
const uint JOB_QUADS = 16u;
const uint QUAD_VERTICES = 4u;

const uint QUAD_WORDS = 24u;
const uint CORNER_WORDS = 6u;

const uint INSTANCE_WORDS = 13u;
const uint INSTANCE_LIGHT = 12u;

const uint JOB_WORDS = 4u;
const uint DRAW_BITS = 24u;

// DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR: position, texture coordinate, light, colour.
const uint VERTEX_WORDS = 7u;

layout(local_size_x = 64) in;

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer Words {
    uint words[];
};

layout(buffer_reference, std430, buffer_reference_align = 4) writeonly buffer VertexWords {
    uint words[];
};

layout(push_constant, std430) uniform TextVertexPass {
    Words quads;
    Words instances;
    Words jobs;
    Words drawBases;
    VertexWords vertices;
    uint firstJob;
    uint jobCount;
} pass;

void main() {
    uint invocation = gl_GlobalInvocationID.x;
    uint jobInDispatch = invocation / JOB_QUADS;
    if (jobInDispatch >= pass.jobCount) {
        return;
    }
    uint quadInJob = invocation - jobInDispatch * JOB_QUADS;
    uint job = (pass.firstJob + jobInDispatch) * JOB_WORDS;
    uint drawAndQuads = pass.jobs.words[job + 2u];
    if (quadInJob >= (drawAndQuads >> DRAW_BITS)) {
        return;
    }
    uint instance = pass.jobs.words[job] * INSTANCE_WORDS;
    uint quad = (pass.jobs.words[job + 1u] + quadInJob) * QUAD_WORDS;
    uint draw = drawAndQuads & ((1u << DRAW_BITS) - 1u);
    uint firstVertex = pass.drawBases.words[draw] + pass.jobs.words[job + 3u] + quadInJob * QUAD_VERTICES;

    float m00 = uintBitsToFloat(pass.instances.words[instance + 0u]);
    float m01 = uintBitsToFloat(pass.instances.words[instance + 1u]);
    float m02 = uintBitsToFloat(pass.instances.words[instance + 2u]);
    float m10 = uintBitsToFloat(pass.instances.words[instance + 3u]);
    float m11 = uintBitsToFloat(pass.instances.words[instance + 4u]);
    float m12 = uintBitsToFloat(pass.instances.words[instance + 5u]);
    float m20 = uintBitsToFloat(pass.instances.words[instance + 6u]);
    float m21 = uintBitsToFloat(pass.instances.words[instance + 7u]);
    float m22 = uintBitsToFloat(pass.instances.words[instance + 8u]);
    float m30 = uintBitsToFloat(pass.instances.words[instance + 9u]);
    float m31 = uintBitsToFloat(pass.instances.words[instance + 10u]);
    float m32 = uintBitsToFloat(pass.instances.words[instance + 11u]);
    uint light = pass.instances.words[instance + INSTANCE_LIGHT];

    for (uint corner = 0u; corner < QUAD_VERTICES; corner++) {
        uint source = quad + corner * CORNER_WORDS;
        float x = uintBitsToFloat(pass.quads.words[source]);
        float y = uintBitsToFloat(pass.quads.words[source + 1u]);
        float z = uintBitsToFloat(pass.quads.words[source + 2u]);
        // Matrix4f.transformPosition, as JOML evaluates it without fused multiply-adds.
        precise float outX = m00 * x + (m10 * y + (m20 * z + m30));
        precise float outY = m01 * x + (m11 * y + (m21 * z + m31));
        precise float outZ = m02 * x + (m12 * y + (m22 * z + m32));
        uint target = (firstVertex + corner) * VERTEX_WORDS;
        pass.vertices.words[target] = floatBitsToUint(outX);
        pass.vertices.words[target + 1u] = floatBitsToUint(outY);
        pass.vertices.words[target + 2u] = floatBitsToUint(outZ);
        pass.vertices.words[target + 3u] = pass.quads.words[source + 4u];
        pass.vertices.words[target + 4u] = pass.quads.words[source + 5u];
        pass.vertices.words[target + 5u] = light;
        pass.vertices.words[target + 6u] = pass.quads.words[source + 3u];
    }
}
