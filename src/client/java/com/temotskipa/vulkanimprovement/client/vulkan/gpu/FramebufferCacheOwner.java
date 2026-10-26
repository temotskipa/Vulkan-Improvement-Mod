package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

/**
 * Minecraft's Vulkan command encoder, which owns the framebuffers its render passes use.
 */
public interface FramebufferCacheOwner {
    FramebufferCache vim$framebuffers();
}