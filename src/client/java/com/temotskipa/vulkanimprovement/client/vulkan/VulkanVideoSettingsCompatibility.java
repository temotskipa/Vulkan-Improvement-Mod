package com.temotskipa.vulkanimprovement.client.vulkan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VulkanVideoSettingsCompatibility {
    private VulkanVideoSettingsCompatibility() {
    }

    public static boolean useRgssTextureFiltering() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options.textureFiltering().get() == TextureFilteringMethod.RGSS;
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return map;
        }

        map.put("quality", qualitySettings(minecraft));
        map.put("display", displaySettings(minecraft));
        map.put("preferences", preferenceSettings(minecraft));
        map.put("vulkanImprovement", modSettings());
        map.put("terrainCompatibility", terrainCompatibility(minecraft));
        return map;
    }

    private static Map<String, Object> qualitySettings(Minecraft minecraft) {
        Map<String, Object> map = new LinkedHashMap<>();
        var options = minecraft.options;
        TextureFilteringMethod textureFiltering = options.textureFiltering().get();
        map.put("graphicsPreset", enumName(options.graphicsPreset().get()));
        map.put("biomeBlendRadius", options.biomeBlendRadius().get());
        map.put("renderDistance", options.renderDistance().get());
        map.put("effectiveRenderDistance", options.getEffectiveRenderDistance());
        map.put("simulationDistance", options.simulationDistance().get());
        map.put("prioritizeChunkUpdates", enumName(options.prioritizeChunkUpdates().get()));
        map.put("ambientOcclusion", options.ambientOcclusion().get());
        map.put("cloudStatus", enumName(options.cloudStatus().get()));
        map.put("particles", enumName(options.particles().get()));
        map.put("mipmapLevels", options.mipmapLevels().get());
        map.put("entityShadows", options.entityShadows().get());
        map.put("entityDistanceScaling", options.entityDistanceScaling().get());
        map.put("menuBackgroundBlurriness", options.getMenuBackgroundBlurriness());
        map.put("cloudRange", options.cloudRange().get());
        map.put("cutoutLeaves", options.cutoutLeaves().get());
        map.put("improvedTransparency", options.improvedTransparency().get());
        map.put("shaderTransparencyActive", minecraft.gameRenderer.gameRenderState().useShaderTransparency());
        map.put("textureFiltering", textureFiltering.name());
        map.put("useRgss", textureFiltering == TextureFilteringMethod.RGSS);
        map.put("anisotropicSamplerExpected", textureFiltering == TextureFilteringMethod.ANISOTROPIC);
        map.put("maxAnisotropyBit", options.maxAnisotropyBit().get());
        map.put("maxAnisotropyValue", options.maxAnisotropyValue());
        map.put("weatherRadius", options.weatherRadius().get());
        return map;
    }

    private static Map<String, Object> displaySettings(Minecraft minecraft) {
        Map<String, Object> map = new LinkedHashMap<>();
        var options = minecraft.options;
        map.put("framerateLimit", options.framerateLimit().get());
        map.put("enableVsync", options.enableVsync().get());
        map.put("inactivityFpsLimit", enumName(options.inactivityFpsLimit().get()));
        map.put("guiScale", options.guiScale().get());
        map.put("fullscreen", options.fullscreen().get());
        map.put("exclusiveFullscreen", options.exclusiveFullscreen().get());
        map.put("gamma", options.gamma().get());
        map.put("preferredGraphicsBackend", enumName(options.preferredGraphicsBackend().get()));
        map.put("restartRequiredToApplyVideoSettings", options.isRestartRequiredToApplyVideoSettings());
        return map;
    }

    private static Map<String, Object> preferenceSettings(Minecraft minecraft) {
        Map<String, Object> map = new LinkedHashMap<>();
        var options = minecraft.options;
        map.put("showAutosaveIndicator", options.showAutosaveIndicator().get());
        map.put("vignette", options.vignette().get());
        map.put("attackIndicator", enumName(options.attackIndicator().get()));
        map.put("chunkSectionFadeInTime", options.chunkSectionFadeInTime().get());
        return map;
    }

    private static Map<String, Object> modSettings() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("vulkanBackendActive", VulkanImprovementRuntime.isVulkanBackendActive());
        map.put("modRendererActive", VulkanImprovementRuntime.isModRendererActive());
        map.put("videoOptionsVisible", VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance()));
        map.put("replaceVanillaTerrain", TerrainRendererDebugConfig.replaceVanillaTerrain());
        map.put("meshTranslucentTerrainEnabled", TerrainRendererDebugConfig.meshTranslucentTerrainEnabled());
        map.put("meshletFrustumCullingEnabled", TerrainRendererDebugConfig.meshletFrustumCullingEnabled());
        map.put("vanillaChunkVisibilityFadeEnabled", TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled());
        map.put("drawAllCapturedTerrainLayers", TerrainRendererDebugConfig.drawAllCapturedTerrainLayers());
        map.put("fragmentShadingRateEnabled", TerrainRendererDebugConfig.fragmentShadingRateEnabled());
        map.put("requireVulkanBackend", TerrainRendererDebugConfig.REQUIRE_VULKAN_BACKEND);
        map.put("presentPacingEnabled", !TerrainRendererDebugConfig.DISABLE_PRESENT_PACING);
        map.put("waitIdleBeforeTerrainUpload", TerrainRendererDebugConfig.WAIT_IDLE_BEFORE_TERRAIN_UPLOAD);
        map.put("allowCpuVisibleMeshletFallback", TerrainRendererDebugConfig.ALLOW_CPU_VISIBLE_MESHLET_FALLBACK);
        return map;
    }

    private static Map<String, Object> terrainCompatibility(Minecraft minecraft) {
        Map<String, Object> map = new LinkedHashMap<>();
        var options = minecraft.options;
        TextureFilteringMethod textureFiltering = options.textureFiltering().get();
        map.put("sectionCompilationSource", "vanilla");
        map.put("geometryInvalidationSource", "LevelRenderer.invalidateCompiledGeometry");
        map.put("textureReloadSource", "VideoSettingsScreen.removed");
        map.put("terrainTextureSampling", textureFiltering == TextureFilteringMethod.RGSS ? "vanilla-rgss" : "vanilla-nearest");
        map.put("chunkSectionFade", TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled() ? "vanilla" : "mesh-stable");
        map.put("terrainSamplerSource", "vanilla ChunkSectionsToRender sampler");
        map.put("translucentTargetSource", "vanilla ChunkSectionLayerGroup output target");
        map.put("translucentTerrainMode", TerrainRendererDebugConfig.meshTranslucentTerrainEnabled() ? "mesh-experimental" : "vanilla-fallback");
        map.put("nonTerrainPasses", "vanilla");
        map.put("displayPacing", TerrainRendererDebugConfig.DISABLE_PRESENT_PACING ? "vanilla-only" : "vanilla-plus-present-id-wait");
        map.put("graphicsBackendRequirement", TerrainRendererDebugConfig.REQUIRE_VULKAN_BACKEND ? "vulkan-required" : "vanilla-selection");
        return map;
    }

    private static String enumName(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(value);
    }
}
