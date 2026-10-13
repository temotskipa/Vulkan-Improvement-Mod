package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRenderPassAccess;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Gives the mesh terrain path the command buffer and bound pipeline of Minecraft's render pass,
 * and the way to hand the pass back to Minecraft after drawing with a pipeline of its own.
 */
@Mixin(VulkanRenderPass.class)
public abstract class VulkanRenderPassMixin implements TerrainRenderPassAccess {
    @Shadow
    @Nullable
    protected VulkanRenderPipeline pipeline;

    @Shadow
    private boolean anyDescriptorDirty;

    @Shadow
    private VkCommandBuffer commandBuffer() {
        throw new AssertionError();
    }

    @Override
    public VkCommandBuffer vim$commandBuffer() {
        return this.commandBuffer();
    }

    @Override
    public @Nullable VulkanRenderPipeline vim$boundPipeline() {
        return this.pipeline;
    }

    @Override
    public void vim$restoreVanillaPipeline() {
        if (this.pipeline == null) {
            return;
        }
        VK12.vkCmdBindPipeline(this.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.vkPipeline());
        this.anyDescriptorDirty = true;
    }
}
