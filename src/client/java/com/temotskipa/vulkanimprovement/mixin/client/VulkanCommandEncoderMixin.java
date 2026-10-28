package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.vulkan.*;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommands;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommandsBackend;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainCommandEncoderAccess;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Tells the terrain path whether Minecraft has a render pass open, in which case the encoder
 * accepts no other command buffer, and hands it the command buffer Minecraft records the frame
 * into outside a render pass. Records compute dispatches into that command buffer for
 * {@link ComputeCommands}.
 */
@Mixin(VulkanCommandEncoder.class)
public class VulkanCommandEncoderMixin implements TerrainCommandEncoderAccess, ComputeCommandsBackend {
    @Shadow
    @Final
    private VulkanDevice device;
    
    @Shadow
    private @Nullable VulkanRenderPass currentRenderPass;
    
    /**
     * The instance's debug utilities. The instance is Minecraft's, which closes it when the game
     * ends.
     */
    @Unique
    private static VulkanDebug vim$debug(VulkanInstance instance) {
        return instance.debug();
    }
    
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
    
    /**
     * Records the dispatch in a debug group of its label, as Minecraft labels its render passes,
     * and follows it with the barrier Minecraft puts after each of its transfers.
     */
    @Override
    public void vim$dispatchCompute(Supplier<String> label, ComputePipeline pipeline, ByteBuffer pushConstants, int groupsX, int groupsY, int groupsZ) {
        VkCommandBuffer commandBuffer = commandBuffer();
        VulkanDebug debug = vim$debug(this.device.instance());
        debug.beginDebugGroup(commandBuffer, label);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.handle());
            if (pushConstants.hasRemaining()) {
                VK10.vkCmdPushConstants(commandBuffer, pipeline.layout(), VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
            }
            VK10.vkCmdDispatch(commandBuffer, groupsX, groupsY, groupsZ);
            VulkanCommandEncoder.memoryBarrier(commandBuffer, stack);
        }
        debug.endDebugGroup(commandBuffer);
    }
}