package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRenderPassAccess;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Tells the terrain path whether Minecraft has a render pass open, in which case the encoder
 * accepts no other command buffer.
 */
@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderMixin implements TerrainRenderPassAccess.Encoder {
    @Shadow
    private @Nullable VulkanRenderPass currentRenderPass;
    
    @Override
    public boolean vim$insideRenderPass() {
        return this.currentRenderPass != null;
    }
}