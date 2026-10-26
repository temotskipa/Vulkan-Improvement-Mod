package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * Implemented on Minecraft's Vulkan command encoder by a mixin.
 */
public interface TerrainCommandEncoderAccess {
    boolean vim$insideRenderPass();
    
    VkCommandBuffer vim$frameCommandBuffer();
}