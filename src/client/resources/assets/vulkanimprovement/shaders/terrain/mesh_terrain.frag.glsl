#version 460

layout(location = 0) in vec4 meshColor;
layout(location = 1) in vec2 meshUv0;
layout(location = 2) in vec2 meshLightUv;
layout(location = 3) flat in uint meshMaterialFlags;
layout(set = 0, binding = 0) uniform texture2D blockAtlasTexture;
layout(set = 0, binding = 1) uniform sampler blockAtlasSampler;
layout(set = 0, binding = 2) uniform texture2D lightmapTexture;
layout(set = 0, binding = 3) uniform sampler lightmapSampler;
layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 atlasSize = textureSize(sampler2D(blockAtlasTexture, blockAtlasSampler), 0);
    ivec2 atlasTexel = clamp(ivec2(floor(meshUv0 * vec2(atlasSize))), ivec2(0), atlasSize - ivec2(1));
    vec4 atlas = texelFetch(sampler2D(blockAtlasTexture, blockAtlasSampler), atlasTexel, 0);
    float alpha = atlas.a * meshColor.a;
    bool alphaMasked = (meshMaterialFlags & 4u) != 0u;
    bool alphaBlended = (meshMaterialFlags & 8u) != 0u;
    float alphaCutoff = alphaMasked ? 0.5 : 0.05;
    if (alpha < alphaCutoff) {
        discard;
    }
    vec3 light = max(texture(sampler2D(lightmapTexture, lightmapSampler), clamp(meshLightUv, vec2(0.0), vec2(0.99))).rgb, vec3(0.35));
    float materialAlpha = alphaBlended ? alpha : 1.0;
    fragColor = vec4(atlas.rgb * light * meshColor.rgb, materialAlpha);
}
