package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public record TerrainDrawResult(boolean drewMeshTerrain, boolean cancelVanillaDraw) {
    private static final TerrainDrawResult PASS_THROUGH = new TerrainDrawResult(false, false);
    private static final TerrainDrawResult OVERLAY_ONLY = new TerrainDrawResult(true, false);
    private static final TerrainDrawResult FALLBACK_AFTER_PARTIAL_DRAW = new TerrainDrawResult(true, false);
    
    static TerrainDrawResult passThrough() {
        return PASS_THROUGH;
    }
    
    static TerrainDrawResult overlayOnly() {
        return OVERLAY_ONLY;
    }
    
    static TerrainDrawResult fallbackAfterPartialDraw() {
        return FALLBACK_AFTER_PARTIAL_DRAW;
    }
}