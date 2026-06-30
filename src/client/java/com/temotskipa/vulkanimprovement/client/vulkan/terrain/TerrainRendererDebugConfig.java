package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public final class TerrainRendererDebugConfig {
    public static final int MIN_SECTION_CAPACITY = 1024;
    public static final int MAX_SECTION_CAPACITY = 1_048_576;
    public static final int MIN_MESHLET_CAPACITY = 4096;
    public static final int MAX_MESHLET_CAPACITY = 8_388_608;
    public static final int MIN_VERTEX_PAYLOAD_MIB = 1;
    public static final int MAX_VERTEX_PAYLOAD_MIB = 1024;
    public static final int MIN_INDEX_PAYLOAD_MIB = 1;
    public static final int MAX_INDEX_PAYLOAD_MIB = 512;
    public static final int MIN_TERRAIN_MIRROR_STABILIZATION_MILLIS = 0;
    public static final int MAX_TERRAIN_MIRROR_STABILIZATION_MILLIS = 10_000;
    private static volatile boolean dumpCapabilities = booleanProperty("vim.dumpCapabilities", true);
    private static volatile boolean disablePresentPacing = booleanProperty("vim.disablePresentPacing", false);
    private static volatile boolean requireVulkanBackend = booleanProperty("vim.requireVulkanBackend", false);
    private static volatile boolean validationDescriptorBufferOnly = booleanProperty("vim.validationDescriptorBufferOnly", false);
    private static volatile boolean waitIdleBeforeTerrainUpload = booleanProperty("vim.waitIdleBeforeTerrainUpload", false);
    private static volatile boolean allowCpuVisibleMeshletFallback = booleanProperty("vim.allowCpuVisibleMeshletFallback", false);
    private static volatile boolean enableGpuGeneratedMeshTaskCommands = booleanProperty("vim.enableGpuGeneratedMeshTaskCommands", false);
    private static volatile int terrainFragmentShadingRate = intProperty("vim.terrainFragmentShadingRate", 1, 1, 4);
    private static volatile int initialSectionCapacity = intProperty("vim.initialSectionCapacity", 32768, MIN_SECTION_CAPACITY, MAX_SECTION_CAPACITY);
    private static volatile int initialMeshletCapacity = intProperty("vim.initialMeshletCapacity", 524288, MIN_MESHLET_CAPACITY, MAX_MESHLET_CAPACITY);
    private static volatile int initialVertexPayloadBytes = intProperty("vim.initialVertexPayloadBytes", 512 * 1024 * 1024, MIN_VERTEX_PAYLOAD_MIB * 1024 * 1024, MAX_VERTEX_PAYLOAD_MIB * 1024 * 1024);
    private static volatile int initialIndexPayloadBytes = intProperty("vim.initialIndexPayloadBytes", 64 * 1024 * 1024, MIN_INDEX_PAYLOAD_MIB * 1024 * 1024, MAX_INDEX_PAYLOAD_MIB * 1024 * 1024);
    private static volatile int terrainMirrorStabilizationMillis = intProperty("vim.terrainMirrorStabilizationMillis", 750, MIN_TERRAIN_MIRROR_STABILIZATION_MILLIS, MAX_TERRAIN_MIRROR_STABILIZATION_MILLIS);
    private static volatile boolean fragmentShadingRateEnabled = !booleanProperty("vim.disableFragmentShadingRate", false);
    private static volatile boolean replaceVanillaTerrain = booleanProperty("vim.replaceVanillaTerrain", false);
    private static volatile boolean meshTranslucentTerrainEnabled = booleanProperty("vim.enableMeshTranslucentTerrain", false);
    private static volatile boolean meshletFrustumCullingEnabled = booleanProperty("vim.enableMeshletFrustumCulling", false);
    private static volatile boolean strictMeshTerrainReplacement = booleanProperty("vim.strictMeshTerrainReplacement", true);
    private static volatile boolean vanillaChunkVisibilityFadeEnabled = booleanProperty("vim.enableVanillaChunkVisibilityFade", true);
    private static volatile boolean drawAllCapturedTerrainLayers = booleanProperty("vim.drawAllCapturedTerrainLayers", false);
    
    private TerrainRendererDebugConfig() {
    }
    
    public static boolean dumpCapabilities() {
        return dumpCapabilities;
    }
    
    public static void setDumpCapabilities(boolean enabled) {
        dumpCapabilities = enabled;
        setBooleanProperty("vim.dumpCapabilities", enabled);
    }
    
    public static boolean disablePresentPacing() {
        return disablePresentPacing;
    }
    
    public static void setDisablePresentPacing(boolean disabled) {
        disablePresentPacing = disabled;
        setBooleanProperty("vim.disablePresentPacing", disabled);
    }
    
    public static boolean requireVulkanBackend() {
        return requireVulkanBackend;
    }
    
    public static void setRequireVulkanBackend(boolean required) {
        requireVulkanBackend = required;
        setBooleanProperty("vim.requireVulkanBackend", required);
    }
    
    public static boolean validationDescriptorBufferOnly() {
        return validationDescriptorBufferOnly;
    }
    
    public static void setValidationDescriptorBufferOnly(boolean enabled) {
        validationDescriptorBufferOnly = enabled;
        setBooleanProperty("vim.validationDescriptorBufferOnly", enabled);
    }
    
    public static boolean waitIdleBeforeTerrainUpload() {
        return waitIdleBeforeTerrainUpload;
    }
    
    public static void setWaitIdleBeforeTerrainUpload(boolean enabled) {
        waitIdleBeforeTerrainUpload = enabled;
        setBooleanProperty("vim.waitIdleBeforeTerrainUpload", enabled);
    }
    
    public static boolean allowCpuVisibleMeshletFallback() {
        return allowCpuVisibleMeshletFallback;
    }
    
    public static void setAllowCpuVisibleMeshletFallback(boolean enabled) {
        allowCpuVisibleMeshletFallback = enabled;
        setBooleanProperty("vim.allowCpuVisibleMeshletFallback", enabled);
    }
    
    public static boolean enableGpuGeneratedMeshTaskCommands() {
        return enableGpuGeneratedMeshTaskCommands;
    }
    
    public static void setEnableGpuGeneratedMeshTaskCommands(boolean enabled) {
        enableGpuGeneratedMeshTaskCommands = enabled;
        setBooleanProperty("vim.enableGpuGeneratedMeshTaskCommands", enabled);
    }
    
    public static int terrainFragmentShadingRate() {
        return terrainFragmentShadingRate;
    }
    
    public static void setTerrainFragmentShadingRate(int rate) {
        terrainFragmentShadingRate = Math.clamp(rate, 1, 4);
        setIntProperty("vim.terrainFragmentShadingRate", terrainFragmentShadingRate);
    }
    
    public static int initialSectionCapacity() {
        return initialSectionCapacity;
    }
    
    public static void setInitialSectionCapacity(int capacity) {
        initialSectionCapacity = Math.clamp(capacity, MIN_SECTION_CAPACITY, MAX_SECTION_CAPACITY);
        setIntProperty("vim.initialSectionCapacity", initialSectionCapacity);
    }
    
    public static int initialMeshletCapacity() {
        return initialMeshletCapacity;
    }
    
    public static void setInitialMeshletCapacity(int capacity) {
        initialMeshletCapacity = Math.clamp(capacity, MIN_MESHLET_CAPACITY, MAX_MESHLET_CAPACITY);
        setIntProperty("vim.initialMeshletCapacity", initialMeshletCapacity);
    }
    
    public static int initialVertexPayloadMib() {
        return initialVertexPayloadBytes / (1024 * 1024);
    }
    
    public static int initialVertexPayloadBytes() {
        return initialVertexPayloadBytes;
    }
    
    public static void setInitialVertexPayloadMib(int mib) {
        int clamped = Math.clamp(mib, MIN_VERTEX_PAYLOAD_MIB, MAX_VERTEX_PAYLOAD_MIB);
        initialVertexPayloadBytes = clamped * 1024 * 1024;
        setIntProperty("vim.initialVertexPayloadBytes", initialVertexPayloadBytes);
    }
    
    public static int initialIndexPayloadMib() {
        return initialIndexPayloadBytes / (1024 * 1024);
    }
    
    public static int initialIndexPayloadBytes() {
        return initialIndexPayloadBytes;
    }
    
    public static void setInitialIndexPayloadMib(int mib) {
        int clamped = Math.clamp(mib, MIN_INDEX_PAYLOAD_MIB, MAX_INDEX_PAYLOAD_MIB);
        initialIndexPayloadBytes = clamped * 1024 * 1024;
        setIntProperty("vim.initialIndexPayloadBytes", initialIndexPayloadBytes);
    }
    
    public static int terrainMirrorStabilizationMillis() {
        return terrainMirrorStabilizationMillis;
    }
    
    public static void setTerrainMirrorStabilizationMillis(int millis) {
        terrainMirrorStabilizationMillis = Math.clamp(millis, MIN_TERRAIN_MIRROR_STABILIZATION_MILLIS, MAX_TERRAIN_MIRROR_STABILIZATION_MILLIS);
        setIntProperty("vim.terrainMirrorStabilizationMillis", terrainMirrorStabilizationMillis);
    }
    
    public static String rendererMode() {
        return replaceVanillaTerrain() ? "mesh-required" : "mesh-capture-bootstrap";
    }
    
    public static boolean replaceVanillaTerrain() {
        return replaceVanillaTerrain;
    }
    
    public static void setReplaceVanillaTerrain(boolean enabled) {
        replaceVanillaTerrain = enabled;
        setBooleanProperty("vim.replaceVanillaTerrain", enabled);
    }
    
    public static boolean meshTranslucentTerrainEnabled() {
        return meshTranslucentTerrainEnabled;
    }
    
    public static void setMeshTranslucentTerrainEnabled(boolean enabled) {
        meshTranslucentTerrainEnabled = enabled;
        setBooleanProperty("vim.enableMeshTranslucentTerrain", enabled);
    }
    
    public static boolean meshletFrustumCullingEnabled() {
        return meshletFrustumCullingEnabled;
    }
    
    public static void setMeshletFrustumCullingEnabled(boolean enabled) {
        meshletFrustumCullingEnabled = enabled;
        setBooleanProperty("vim.enableMeshletFrustumCulling", enabled);
    }
    
    public static boolean strictMeshTerrainReplacement() {
        return strictMeshTerrainReplacement;
    }
    
    public static void setStrictMeshTerrainReplacement(boolean enabled) {
        strictMeshTerrainReplacement = enabled;
        setBooleanProperty("vim.strictMeshTerrainReplacement", enabled);
    }
    
    public static boolean vanillaChunkVisibilityFadeEnabled() {
        return vanillaChunkVisibilityFadeEnabled;
    }
    
    public static void setVanillaChunkVisibilityFadeEnabled(boolean enabled) {
        vanillaChunkVisibilityFadeEnabled = enabled;
        setBooleanProperty("vim.enableVanillaChunkVisibilityFade", enabled);
    }
    
    public static boolean drawAllCapturedTerrainLayers() {
        return drawAllCapturedTerrainLayers;
    }
    
    public static void setDrawAllCapturedTerrainLayers(boolean enabled) {
        drawAllCapturedTerrainLayers = enabled;
        setBooleanProperty("vim.drawAllCapturedTerrainLayers", enabled);
    }
    
    public static boolean fragmentShadingRateEnabled() {
        return fragmentShadingRateEnabled;
    }
    
    public static void setFragmentShadingRateEnabled(boolean enabled) {
        fragmentShadingRateEnabled = enabled;
        setBooleanProperty("vim.disableFragmentShadingRate", !enabled);
    }
    
    public static String describe() {
        return "rendererMode=" + rendererMode() + ", requireVulkanBackend=" + requireVulkanBackend() + ", enableMeshTranslucentTerrain=" + meshTranslucentTerrainEnabled() + ", enableMeshletFrustumCulling=" + meshletFrustumCullingEnabled() + ", strictMeshTerrainReplacement=" + strictMeshTerrainReplacement() + ", enableVanillaChunkVisibilityFade=" + vanillaChunkVisibilityFadeEnabled() + ", drawAllCapturedTerrainLayers=" + drawAllCapturedTerrainLayers() + ", defaultSolidCutoutLayerMode=" + (replaceVanillaTerrain() ? "mesh" : "capture-bootstrap") + ", defaultTranslucentLayerMode=" + (meshTranslucentTerrainEnabled() ? "mesh-experimental-translucent" : "vanilla-translucent-fallback") + ", dumpCapabilities=" + dumpCapabilities() + ", disableFragmentShadingRate=" + !fragmentShadingRateEnabled() + ", disablePresentPacing=" + disablePresentPacing() + ", validationDescriptorBufferOnly=" + validationDescriptorBufferOnly() + ", waitIdleBeforeTerrainUpload=" + waitIdleBeforeTerrainUpload() + ", allowCpuVisibleMeshletFallback=" + allowCpuVisibleMeshletFallback() + ", enableGpuGeneratedMeshTaskCommands=" + enableGpuGeneratedMeshTaskCommands() + ", terrainFragmentShadingRate=" + terrainFragmentShadingRate() + ", terrainMirrorStabilizationMillis=" + terrainMirrorStabilizationMillis() + ", initialSectionCapacity=" + initialSectionCapacity() + ", initialMeshletCapacity=" + initialMeshletCapacity() + ", initialVertexPayloadBytes=" + initialVertexPayloadBytes() + ", initialIndexPayloadBytes=" + initialIndexPayloadBytes();
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
    
    private static void setBooleanProperty(String name, boolean value) {
        System.setProperty(name, Boolean.toString(value));
    }
    
    private static void setIntProperty(String name, int value) {
        System.setProperty(name, Integer.toString(value));
    }
}