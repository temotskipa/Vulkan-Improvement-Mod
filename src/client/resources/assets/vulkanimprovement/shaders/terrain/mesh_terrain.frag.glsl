#version 460
// The sky and cloud images are read texel by texel, without a sampler.
#extension GL_EXT_samplerless_texture_functions : require

// The vertex colour, already lit by the lightmap in the mesh stage.
layout(location = 0) in vec4 meshColor;
layout(location = 1) in vec2 meshUv0;
layout(location = 3) flat in uint meshMaterialFlags;
// x: distance from the camera. y: the larger of the horizontal distance and the height difference.
// z: how far the section has faded in.
layout(location = 4) in vec3 meshFog;
layout(set = 0, binding = 0) uniform texture2D blockAtlasTexture;
layout(set = 0, binding = 1) uniform sampler blockAtlasSampler;
// What Minecraft has drawn behind the terrain. Read only when the frame uses improved fog.
layout(set = 0, binding = 4) uniform texture2D skyTexture;
layout(set = 0, binding = 5) uniform texture2D cloudsTexture;
layout(set = 0, binding = 6) uniform texture2D cloudsDepthTexture;
layout(location = 0) out vec4 fragColor;

// The frame's fog, as Minecraft set it up for its own terrain shaders. The task and mesh stages
// own the push constants below this block's first offset.
layout(push_constant, std430) uniform TerrainFogConstants {
    layout(offset = 176) vec4 fogColor;
// Where environmental fog starts and ends, then where render-distance fog starts and ends.
    layout(offset = 192) vec4 fogRange;
// x: whether terrain fades into the sky and clouds behind it instead of the fog colour, which
// is what Minecraft's improved transparency setting asks for.
    layout(offset = 208) ivec4 fogMode;
} fog;

// Whether the layer this pipeline draws can drop a fragment for its alpha. Solid terrain cannot.
// A shader that may discard keeps the GPU from testing depth before it runs, so the solid
// pipeline is specialized without the test, as vanilla's solid terrain shader has none.
layout(constant_id = 0) const bool ALPHA_TEST = true;

// Samples the block atlas so that a texel keeps a hard edge while it covers more than a pixel and
// the mip chain takes over once it covers less. Fetching mip 0 directly, as this shader did,
// keeps near terrain sharp but makes distant terrain shimmer and lets cutout foliage keep every
// texel that the smaller mips would have thinned out, which is not how vanilla terrain looks.
vec4 sampleBlockAtlas(vec2 uv) {
    vec2 atlasSize = vec2(textureSize(sampler2D(blockAtlasTexture, blockAtlasSampler), 0));
    vec2 texel = uv * atlasSize;
    vec2 texelsPerPixel = max(fwidth(texel), vec2(0.00001));
    // Move the sample point towards the centre of its texel, leaving one pixel of blend at the
    // texel's edge. When texels are smaller than pixels the point does not move at all.
    vec2 nearestEdge = floor(texel + 0.5);
    vec2 crisp = nearestEdge + clamp((texel - nearestEdge) / texelsPerPixel, -0.5, 0.5);
    return textureGrad(sampler2D(blockAtlasTexture, blockAtlasSampler), crisp / atlasSize, dFdx(uv), dFdy(uv));
}

// How much of a fog that starts and ends at the given distances lies in front of a fragment.
// Minecraft switches a fog off by starting it farther away than anything is drawn.
float fogAmount(float fragmentDistance, float start, float end) {
    return fragmentDistance <= start ? 0.0 : clamp((fragmentDistance - start) / max(end - start, 0.00001), 0.0, 1.0);
}

// What lies behind a fragment: the sky, and the clouds where they are farther away than it is.
vec3 behindTerrain() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec3 sky = texelFetch(skyTexture, pixel, 0).rgb;
    vec4 clouds = texelFetch(cloudsTexture, pixel, 0);
    // Depth is larger nearer the camera, so clouds no deeper than the fragment are behind it.
    float cloudsAreBehind = gl_FragCoord.z >= texelFetch(cloudsDepthTexture, pixel, 0).r ? 1.0 : 0.0;
    // The cloud image holds colour already multiplied by how much of the pixel the clouds cover.
    vec3 cloudColor = clouds.a > 0.0 ? clouds.rgb / clouds.a : clouds.rgb;
    return mix(sky, cloudColor, clouds.a * cloudsAreBehind);
}

void main() {
    vec4 atlas = sampleBlockAtlas(meshUv0);
    vec4 lit = atlas * meshColor;
    bool alphaBlended = (meshMaterialFlags & 8u) != 0u;
    if (ALPHA_TEST) {
        bool alphaMasked = (meshMaterialFlags & 4u) != 0u;
        // The thresholds of Minecraft's cutout and translucent terrain pipelines; solid terrain has none.
        float alphaCutoff = alphaMasked ? 0.5 : (alphaBlended ? 0.1 : 0.0);
        if (lit.a < alphaCutoff) {
            discard;
        }
    }
    float environmentalFog = fogAmount(meshFog.x, fog.fogRange.x, fog.fogRange.y);
    float renderDistanceFog = fogAmount(meshFog.y, fog.fogRange.z, fog.fogRange.w);
    float fogValue = max(environmentalFog, renderDistanceFog);
    float sectionShown = clamp(meshFog.z, 0.0, 1.0);
    // What terrain turns into at the far end of the fog, and what shows through a section that
    // has not faded in yet. Both are the fog colour unless the frame uses improved fog.
    vec3 fogTarget = fog.fogColor.rgb;
    vec3 behind = fog.fogColor.rgb;
    if (fog.fogMode.x != 0) {
        // Render-distance fog gives way to what is behind the terrain all along, environmental
        // fog only over its last tenth.
        float behindShare = max(smoothstep(0.9, 1.0, environmentalFog), renderDistanceFog);
        if (behindShare > 0.0 || sectionShown < 1.0) {
            behind = behindTerrain();
            fogTarget = mix(fog.fogColor.rgb, behind, behindShare);
        }
    }
    vec3 fogged = mix(lit.rgb, fogTarget, fogValue);
    // A section that is still fading in is shown part-way between what is behind it and its own colour.
    vec3 shown = mix(behind, fogged, sectionShown);
    float materialAlpha = alphaBlended ? lit.a : 1.0;
    fragColor = vec4(shown, materialAlpha);
}