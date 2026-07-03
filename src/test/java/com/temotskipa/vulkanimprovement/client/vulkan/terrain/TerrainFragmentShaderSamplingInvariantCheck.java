package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerrainFragmentShaderSamplingInvariantCheck {
    private static final Path FRAGMENT_SHADER = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain/mesh_terrain.frag.glsl");

    private TerrainFragmentShaderSamplingInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        String shader = Files.readString(FRAGMENT_SHADER);
        checkBlockAtlasUsesTexelFetch(shader);
        checkCutoutAlphaUsesVanillaMask(shader);
        checkOpaqueMaskedAlphaOutput(shader);
    }

    private static void checkBlockAtlasUsesTexelFetch(String shader) {
        require(shader.contains("textureSize(sampler2D(blockAtlasTexture, blockAtlasSampler), 0)"),
                "terrain block atlas sampling must inspect the atlas size");
        require(shader.contains("texelFetch(sampler2D(blockAtlasTexture, blockAtlasSampler)"),
                "terrain block atlas sampling must use texelFetch instead of sampler-filtered texture()");
        require(!shader.contains("texture(sampler2D(blockAtlasTexture, blockAtlasSampler)"),
                "terrain block atlas sampling must not use sampler-filtered texture() in the mesh replacement path");
    }

    private static void checkCutoutAlphaUsesVanillaMask(String shader) {
        require(shader.contains("float alphaCutoff = alphaMasked ? 0.5 : 0.05;"),
                "alpha-masked terrain must use a strict cutout threshold");
        require(shader.contains("if (alpha < alphaCutoff)"),
                "fragment discard must use the material-aware alpha cutoff");
    }

    private static void checkOpaqueMaskedAlphaOutput(String shader) {
        require(shader.contains("float materialAlpha = alphaBlended ? alpha : 1.0;"),
                "solid and alpha-masked terrain must render opaque in the replacement path");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
