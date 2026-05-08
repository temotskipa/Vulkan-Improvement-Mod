package com.temotskipa.vulkanimprovement.client.vulkan;

public final class TerrainRendererDebugConfig {
    public static final boolean DUMP_CAPABILITIES = booleanProperty("vim.dumpCapabilities", true);
    public static final boolean DISABLE_FRAGMENT_SHADING_RATE = booleanProperty("vim.disableFragmentShadingRate", false);
    public static final boolean DISABLE_PRESENT_PACING = booleanProperty("vim.disablePresentPacing", false);
    public static final boolean REPLACE_VANILLA_TERRAIN = booleanProperty("vim.replaceVanillaTerrain", false);
    public static final int TERRAIN_FRAGMENT_SHADING_RATE = intProperty("vim.terrainFragmentShadingRate", 1, 1, 4);
    public static final int INITIAL_SECTION_CAPACITY = intProperty("vim.initialSectionCapacity", 8192, 1024, 1_048_576);
    public static final int INITIAL_MESHLET_CAPACITY = intProperty("vim.initialMeshletCapacity", 131072, 4096, 8_388_608);
    public static final int INITIAL_VERTEX_PAYLOAD_BYTES = intProperty("vim.initialVertexPayloadBytes", 64 * 1024 * 1024, 1024 * 1024, 1024 * 1024 * 1024);
    public static final int INITIAL_INDEX_PAYLOAD_BYTES = intProperty("vim.initialIndexPayloadBytes", 16 * 1024 * 1024, 1024 * 1024, 512 * 1024 * 1024);
    
    private TerrainRendererDebugConfig() {
    }
    
    public static String rendererMode() {
        return REPLACE_VANILLA_TERRAIN ? "mesh-required" : "mesh-capture-bootstrap";
    }
    
    public static String describe() {
        return "rendererMode=" + rendererMode()
                + ", dumpCapabilities=" + DUMP_CAPABILITIES
                + ", disableFragmentShadingRate=" + DISABLE_FRAGMENT_SHADING_RATE
                + ", disablePresentPacing=" + DISABLE_PRESENT_PACING
                + ", terrainFragmentShadingRate=" + TERRAIN_FRAGMENT_SHADING_RATE
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
            return Math.clamp(max, min, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
