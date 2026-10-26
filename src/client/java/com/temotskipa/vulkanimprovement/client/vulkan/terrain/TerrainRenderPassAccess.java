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
        return !(commandEncoder instanceof TerrainCommandEncoderAccess encoder) || encoder.vim$insideRenderPass();
    }
    
    /**
     * The command buffer Minecraft records the frame into, started if none is open, or
     * {@code null} while a render pass is open. Commands recorded into it run in order with
     * Minecraft's own: executing a command buffer of the mod's instead would end Minecraft's open
     * ones there, each with a barrier on all work before it.
     */
    static @Nullable VkCommandBuffer frameCommandBuffer(Object commandEncoder) {
        return commandEncoder instanceof TerrainCommandEncoderAccess encoder && !encoder.vim$insideRenderPass() ? encoder.vim$frameCommandBuffer() : null;
    }
    
    static @Nullable TerrainRenderPassAccess of(Object renderPass) {
        Object backend = renderPass instanceof TerrainRenderPassFrontend frontend ? frontend.vim$backend() : renderPass;
        return backend instanceof TerrainRenderPassAccess access ? access : null;
    }
    
    VkCommandBuffer vim$commandBuffer();
    
    @Nullable VulkanRenderPipeline vim$boundPipeline();
    
    /**
     * Binds Minecraft's pipeline again and makes it rewrite its descriptors before its next draw.
     */
    void vim$restoreVanillaPipeline();
}