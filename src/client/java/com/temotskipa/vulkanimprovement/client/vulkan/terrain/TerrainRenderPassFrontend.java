package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

/**
 * Implemented on Minecraft's frontend render pass by a mixin: the way from the pass a renderer
 * is handed to the backend pass that records it.
 */
public interface TerrainRenderPassFrontend {
    Object vim$backend();
}