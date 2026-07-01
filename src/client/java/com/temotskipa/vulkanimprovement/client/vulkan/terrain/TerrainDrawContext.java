package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public record TerrainDrawContext(VkCommandBuffer commandBuffer, @Nullable VulkanRenderPipeline vanillaPipeline,
                                 boolean hasDepth, boolean terrainPass, int layerOrdinal,
                                 Collection<? extends RenderPass.Draw<?>> draws, boolean drawAllCapturedTerrainLayers) {
    private static final Collection<RenderPass.Draw<?>> NO_DRAWS = List.of();

    public TerrainDrawContext {
        Objects.requireNonNull(commandBuffer, "commandBuffer");
        draws = draws == null ? NO_DRAWS : draws;
    }

    public static TerrainDrawContext current(VkCommandBuffer commandBuffer, @Nullable VulkanRenderPipeline vanillaPipeline, boolean hasDepth, Collection<? extends RenderPass.Draw<?>> draws) {
        return new TerrainDrawContext(commandBuffer, vanillaPipeline, hasDepth, TerrainRenderContext.isTerrainPass(), TerrainRenderContext.currentLayerOrdinal(), draws, TerrainRendererDebugConfig.drawAllCapturedTerrainLayers());
    }

    public boolean hasDrawList() {
        return !this.draws.isEmpty();
    }

    public boolean useVisibleDrawList() {
        return !this.drawAllCapturedTerrainLayers && hasDrawList();
    }
}