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
    vec4 atlas = texture(sampler2D(blockAtlasTexture, blockAtlasSampler), meshUv0);
    float alpha = atlas.a * meshColor.a;
    if (alpha < 0.05) {
        discard;
    }
    vec3 light = max(texture(sampler2D(lightmapTexture, lightmapSampler), clamp(meshLightUv, vec2(0.0), vec2(0.99))).rgb, vec3(0.35));
    bool alphaMasked = (meshMaterialFlags & 4u) != 0u;
    bool alphaBlended = (meshMaterialFlags & 8u) != 0u;
    float materialAlpha = (alphaMasked || alphaBlended) ? alpha : 1.0;
    fragColor = vec4(atlas.rgb * light * meshColor.rgb, materialAlpha);
}
