package com.temotskipa.vulkanimprovement.client.vulkan;

public final class TerrainRendererDebugConfig {
    public static final boolean DUMP_CAPABILITIES = booleanProperty("vim.dumpCapabilities", true);
    public static final boolean DISABLE_PRESENT_PACING = booleanProperty("vim.disablePresentPacing", false);
    public static final boolean REQUIRE_VULKAN_BACKEND = booleanProperty("vim.requireVulkanBackend", false);
    public static final boolean VALIDATION_DESCRIPTOR_BUFFER_ONLY = booleanProperty("vim.validationDescriptorBufferOnly", false);
    public static final boolean WAIT_IDLE_BEFORE_TERRAIN_UPLOAD = booleanProperty("vim.waitIdleBeforeTerrainUpload", false);
    public static final boolean ALLOW_CPU_VISIBLE_MESHLET_FALLBACK = booleanProperty("vim.allowCpuVisibleMeshletFallback", false);
    public static final int TERRAIN_FRAGMENT_SHADING_RATE = intProperty("vim.terrainFragmentShadingRate", 1, 1, 4);
    public static final int INITIAL_SECTION_CAPACITY = intProperty("vim.initialSectionCapacity", 32768, 1024, 1_048_576);
    public static final int INITIAL_MESHLET_CAPACITY = intProperty("vim.initialMeshletCapacity", 524288, 4096, 8_388_608);
    public static final int INITIAL_VERTEX_PAYLOAD_BYTES = intProperty("vim.initialVertexPayloadBytes", 512 * 1024 * 1024, 1024 * 1024, 1 << 30);
    public static final int INITIAL_INDEX_PAYLOAD_BYTES = intProperty("vim.initialIndexPayloadBytes", 64 * 1024 * 1024, 1024 * 1024, 512 * 1024 * 1024);
    public static final int TERRAIN_MIRROR_STABILIZATION_MILLIS = intProperty("vim.terrainMirrorStabilizationMillis", 750, 0, 10_000);
    private static volatile boolean fragmentShadingRateEnabled = !booleanProperty("vim.disableFragmentShadingRate", false);
    private static volatile boolean replaceVanillaTerrain = booleanProperty("vim.replaceVanillaTerrain", true);
    private static volatile boolean meshTranslucentTerrainEnabled = booleanProperty("vim.enableMeshTranslucentTerrain", false);
    private static volatile boolean meshletFrustumCullingEnabled = booleanProperty("vim.enableMeshletFrustumCulling", false);
    private static volatile boolean strictMeshTerrainReplacement = booleanProperty("vim.strictMeshTerrainReplacement", true);
    private static volatile boolean vanillaChunkVisibilityFadeEnabled = booleanProperty("vim.enableVanillaChunkVisibilityFade", true);
    private static volatile boolean drawAllCapturedTerrainLayers = booleanProperty("vim.drawAllCapturedTerrainLayers", false);

    private TerrainRendererDebugConfig() {
    }

    public static String rendererMode() {
        return replaceVanillaTerrain() ? "mesh-required" : "mesh-capture-bootstrap";
    }

    public static boolean replaceVanillaTerrain() {
        return replaceVanillaTerrain;
    }

    public static void setReplaceVanillaTerrain(boolean enabled) {
        replaceVanillaTerrain = enabled;
        System.setProperty("vim.replaceVanillaTerrain", Boolean.toString(enabled));
    }

    public static boolean meshTranslucentTerrainEnabled() {
        return meshTranslucentTerrainEnabled;
    }

    public static void setMeshTranslucentTerrainEnabled(boolean enabled) {
        meshTranslucentTerrainEnabled = enabled;
        System.setProperty("vim.enableMeshTranslucentTerrain", Boolean.toString(enabled));
    }

    public static boolean meshletFrustumCullingEnabled() {
        return meshletFrustumCullingEnabled;
    }

    public static void setMeshletFrustumCullingEnabled(boolean enabled) {
        meshletFrustumCullingEnabled = enabled;
        System.setProperty("vim.enableMeshletFrustumCulling", Boolean.toString(enabled));
    }

    public static boolean strictMeshTerrainReplacement() {
        return strictMeshTerrainReplacement;
    }

    public static void setStrictMeshTerrainReplacement(boolean enabled) {
        strictMeshTerrainReplacement = enabled;
        System.setProperty("vim.strictMeshTerrainReplacement", Boolean.toString(enabled));
    }

    public static boolean vanillaChunkVisibilityFadeEnabled() {
        return vanillaChunkVisibilityFadeEnabled;
    }

    public static void setVanillaChunkVisibilityFadeEnabled(boolean enabled) {
        vanillaChunkVisibilityFadeEnabled = enabled;
        System.setProperty("vim.enableVanillaChunkVisibilityFade", Boolean.toString(enabled));
    }

    public static boolean drawAllCapturedTerrainLayers() {
        return drawAllCapturedTerrainLayers;
    }

    public static void setDrawAllCapturedTerrainLayers(boolean enabled) {
        drawAllCapturedTerrainLayers = enabled;
        System.setProperty("vim.drawAllCapturedTerrainLayers", Boolean.toString(enabled));
    }

    public static boolean fragmentShadingRateEnabled() {
        return fragmentShadingRateEnabled;
    }

    public static void setFragmentShadingRateEnabled(boolean enabled) {
        fragmentShadingRateEnabled = enabled;
        System.setProperty("vim.disableFragmentShadingRate", Boolean.toString(!enabled));
    }

    public static String describe() {
        return "rendererMode=" + rendererMode()
                + ", requireVulkanBackend=" + REQUIRE_VULKAN_BACKEND
                + ", enableMeshTranslucentTerrain=" + meshTranslucentTerrainEnabled()
                + ", enableMeshletFrustumCulling=" + meshletFrustumCullingEnabled()
                + ", strictMeshTerrainReplacement=" + strictMeshTerrainReplacement()
                + ", enableVanillaChunkVisibilityFade=" + vanillaChunkVisibilityFadeEnabled()
                + ", drawAllCapturedTerrainLayers=" + drawAllCapturedTerrainLayers()
                + ", defaultSolidCutoutLayerMode=" + (replaceVanillaTerrain() ? "mesh" : "capture-bootstrap")
                + ", defaultTranslucentLayerMode=" + (meshTranslucentTerrainEnabled() ? "mesh-experimental-translucent" : "vanilla-translucent-fallback")
                + ", dumpCapabilities=" + DUMP_CAPABILITIES
                + ", disableFragmentShadingRate=" + !fragmentShadingRateEnabled()
                + ", disablePresentPacing=" + DISABLE_PRESENT_PACING
                + ", validationDescriptorBufferOnly=" + VALIDATION_DESCRIPTOR_BUFFER_ONLY
                + ", waitIdleBeforeTerrainUpload=" + WAIT_IDLE_BEFORE_TERRAIN_UPLOAD
                + ", allowCpuVisibleMeshletFallback=" + ALLOW_CPU_VISIBLE_MESHLET_FALLBACK
                + ", terrainFragmentShadingRate=" + TERRAIN_FRAGMENT_SHADING_RATE
                + ", terrainMirrorStabilizationMillis=" + TERRAIN_MIRROR_STABILIZATION_MILLIS
                + ", initialSectionCapacity=" + INITIAL_SECTION_CAPACITY
                + ", initialMeshletCapacity=" + INITIAL_MESHLET_CAPACITY
                + ", initialVertexPayloadBytes=" + INITIAL_VERTEX_PAYLOAD_BYTES
                + ", initialIndexPayloadBytes=" + INITIAL_INDEX_PAYLOAD_BYTES;
    }

    private static boolean booleanProperty(String name, boolean fallback) {
        String value = System.getProperty(name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int intProperty(String name, int fallback, int min, int max) {
        String value = System.getProperty(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Math.clamp(Integer.parseInt(value), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
