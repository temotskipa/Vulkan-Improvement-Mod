package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRenderPassAccess;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Tells the terrain path whether Minecraft has a render pass open, in which case the encoder
 * accepts no other command buffer, and hands it the command buffer Minecraft records the frame
 * into outside a render pass.
 */
@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderMixin implements TerrainRenderPassAccess.Encoder {
    @Shadow
    private @Nullable VulkanRenderPass currentRenderPass;
    
    @Shadow
    private VkCommandBuffer commandBuffer() {
        throw new AssertionError();
    }
    
    @Override
    public boolean vim$insideRenderPass() {
        return this.currentRenderPass != null;
    }
    
    @Override
    public VkCommandBuffer vim$frameCommandBuffer() {
        return commandBuffer();
    }
}