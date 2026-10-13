package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

/**
 * What the mesh terrain path needs from Minecraft's Vulkan render pass. Implemented on it by a mixin.
 */
public interface TerrainRenderPassAccess {
    /**
     * Whether the encoder has a render pass open. An encoder the mixin did not reach is taken to
     * have one, so nothing is recorded where it might not be allowed.
     */
    static boolean insideRenderPass(Object commandEncoder) {
        return !(commandEncoder instanceof Encoder encoder) || encoder.vim$insideRenderPass();
    }
    
    static @Nullable TerrainRenderPassAccess of(Object renderPass) {
        Object backend = renderPass instanceof Frontend frontend ? frontend.vim$backend() : renderPass;
        return backend instanceof TerrainRenderPassAccess access ? access : null;
    }
    
    VkCommandBuffer vim$commandBuffer();
    
    @Nullable VulkanRenderPipeline vim$boundPipeline();
    
    /**
     * Binds Minecraft's pipeline again and makes it rewrite its descriptors before its next draw.
     */
    void vim$restoreVanillaPipeline();
    
    /**
     * Implemented on Minecraft's frontend render pass by a mixin: the way from the pass a renderer
     * is handed to the backend pass that records it.
     */
    interface Frontend {
        Object vim$backend();
    }
    
    /**
     * Implemented on Minecraft's Vulkan command encoder by a mixin.
     */
    interface Encoder {
        boolean vim$insideRenderPass();
    }
}