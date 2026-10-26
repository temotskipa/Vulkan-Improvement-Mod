package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.jspecify.annotations.Nullable;

/**
 * Implemented on Minecraft's Vulkan pipeline by a mixin.
 */
public interface TerrainPipelineStateHolder {
    @Nullable TerrainPipelineState vim$terrainPipelineState();
    
    void vim$rememberTerrainPipelineState(TerrainPipelineState state);
}