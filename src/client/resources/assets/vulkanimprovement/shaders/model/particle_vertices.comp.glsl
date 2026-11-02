#version 460
#extension GL_EXT_buffer_reference : require

// Writes the vertices Minecraft's QuadParticleRenderState.renderRotatedQuad writes, from the flat
// arrays that class keeps its particles in: one invocation per particle. The counts below are
// checked against Minecraft's format and arrays by the build.

const uint WORKGROUP_SIZE = 64u;
const uint FLOATS_PER_PARTICLE = 12u;
const uint INTS_PER_PARTICLE = 2u;
const uint QUAD_VERTICES = 4u;

// DefaultVertexFormat.PARTICLE: position, texture coordinate, colour, light.
const uint VERTEX_WORDS = 7u;

const float CORNER_X[4] = float[4](1.0, 1.0, -1.0, -1.0);
const float CORNER_Y[4] = float[4](-1.0, 1.0, 1.0, -1.0);

layout(local_size_x = 64) in;

layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer Words {
    uint words[];
};

layout(buffer_reference, std430, buffer_reference_align = 4) writeonly buffer VertexWords {
    uint words[];
};

layout(push_constant, std430) uniform ParticleVertexPass {
    Words floats;
    Words ints;
    VertexWords vertices;
    uint firstVertex;
    uint count;
} pass;

void main() {
    uint particle = gl_GlobalInvocationID.x;
    if (particle >= pass.count) {
        return;
    }
    uint source = particle * FLOATS_PER_PARTICLE;
    float x = uintBitsToFloat(pass.floats.words[source]);
    float y = uintBitsToFloat(pass.floats.words[source + 1u]);
    float z = uintBitsToFloat(pass.floats.words[source + 2u]);
    float qx = uintBitsToFloat(pass.floats.words[source + 3u]);
    float qy = uintBitsToFloat(pass.floats.words[source + 4u]);
    float qz = uintBitsToFloat(pass.floats.words[source + 5u]);
    float qw = uintBitsToFloat(pass.floats.words[source + 6u]);
    float scale = uintBitsToFloat(pass.floats.words[source + 7u]);
    uint u0 = pass.floats.words[source + 8u];
    uint u1 = pass.floats.words[source + 9u];
    uint v0 = pass.floats.words[source + 10u];
    uint v1 = pass.floats.words[source + 11u];
    uint argb = pass.ints.words[particle * INTS_PER_PARTICLE];
    uint light = pass.ints.words[particle * INTS_PER_PARTICLE + 1u];
    // BufferBuilder.setColor: ARGB.toABGR.
    uint abgr = (argb & 0xFF00FF00u) | ((argb >> 16u) & 0xFFu) | ((argb & 0xFFu) << 16u);

    // Quaternionf.transform, as JOML evaluates it without fused multiply-adds: precise keeps the
    // compiler from fusing or reordering. The corners' z is 0, whose products JOML still adds.
    precise float xx = qx * qx;
    precise float yy = qy * qy;
    precise float zz = qz * qz;
    precise float ww = qw * qw;
    precise float xy = qx * qy;
    precise float xz = qx * qz;
    precise float yz = qy * qz;
    precise float xw = qx * qw;
    precise float zw = qz * qw;
    precise float yw = qy * qw;
    precise float k = 1.0 / (xx + yy + zz + ww);
    precise float r00 = (xx - yy - zz + ww) * k;
    precise float r01 = 2.0 * (xy - zw) * k;
    precise float r02 = (2.0 * (xz + yw) * k) * 0.0;
    precise float r10 = 2.0 * (xy + zw) * k;
    precise float r11 = (yy - xx - zz + ww) * k;
    precise float r12 = (2.0 * (yz - xw) * k) * 0.0;
    precise float r20 = 2.0 * (xz - yw) * k;
    precise float r21 = 2.0 * (yz + xw) * k;
    precise float r22 = ((zz - xx - yy + ww) * k) * 0.0;

    for (uint corner = 0u; corner < QUAD_VERTICES; corner++) {
        float nx = CORNER_X[corner];
        float ny = CORNER_Y[corner];
        precise float outX = (r00 * nx + (r01 * ny + r02)) * scale + x;
        precise float outY = (r10 * nx + (r11 * ny + r12)) * scale + y;
        precise float outZ = (r20 * nx + (r21 * ny + r22)) * scale + z;
        uint target = (pass.firstVertex + particle * QUAD_VERTICES + corner) * VERTEX_WORDS;
        pass.vertices.words[target] = floatBitsToUint(outX);
        pass.vertices.words[target + 1u] = floatBitsToUint(outY);
        pass.vertices.words[target + 2u] = floatBitsToUint(outZ);
        pass.vertices.words[target + 3u] = corner < 2u ? u1 : u0;
        pass.vertices.words[target + 4u] = corner == 1u || corner == 2u ? v0 : v1;
        pass.vertices.words[target + 5u] = abgr;
        pass.vertices.words[target + 6u] = light;
    }
}
