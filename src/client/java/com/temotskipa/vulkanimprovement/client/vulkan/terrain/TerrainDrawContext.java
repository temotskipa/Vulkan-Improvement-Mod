package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Objects;

/**
 * One terrain layer about to be drawn inside Minecraft's terrain render pass.
 */
public record TerrainDrawContext(VkCommandBuffer commandBuffer, @Nullable TerrainPipelineState vanillaPipeline, boolean terrainPass, int layerOrdinal, boolean drawAllCapturedTerrainLayers) {
    public TerrainDrawContext {
        Objects.requireNonNull(commandBuffer, "commandBuffer");
    }
    
    public static TerrainDrawContext forLayer(TerrainRenderPassAccess renderPass, int layerOrdinal) {
        return new TerrainDrawContext(renderPass.vim$commandBuffer(), TerrainPipelineState.of(renderPass.vim$boundPipeline()), TerrainRenderContext.isTerrainPass(), layerOrdinal, TerrainRendererDebugConfig.drawAllCapturedTerrainLayers());
    }
    
    public boolean useVisibleDrawList() {
        return !this.drawAllCapturedTerrainLayers;
    }
}