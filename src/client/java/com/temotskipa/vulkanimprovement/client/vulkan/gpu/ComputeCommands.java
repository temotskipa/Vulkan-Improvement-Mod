package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.api.commands.CommandEncoder;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Compute dispatch on Minecraft's command encoder, which has none of its own. Mixins add it the way
 * Minecraft builds its other commands: its frontend encoder checks a dispatch as it checks a copy,
 * and its Vulkan backend records it into the command buffer of the frame, followed by the barrier
 * Minecraft puts after its own transfers, so every command after it sees what it wrote.
 */
public final class ComputeCommands {
    private ComputeCommands() {
    }
    
    /**
     * Records a dispatch of a compute pipeline into the frame, outside any render pass.
     *
     * @param pushConstants the pipeline's push constants, from the buffer's position to its limit
     */
    public static void dispatch(CommandEncoder encoder, Supplier<String> label, ComputePipeline pipeline, ByteBuffer pushConstants, int groupsX, int groupsY, int groupsZ) {
        if (!(encoder instanceof ComputeCommandsFrontend frontend)) {
            throw new UnsupportedOperationException("No compute dispatch on " + encoder.getClass().getName());
        }
        frontend.vim$dispatchCompute(label, pipeline, pushConstants, groupsX, groupsY, groupsZ);
    }
    
    /**
     * Whether a render pass is open on the encoder, when a dispatch would be refused. An encoder
     * without compute dispatch counts as being inside one.
     */
    public static boolean insideRenderPass(CommandEncoder encoder) {
        return !(encoder instanceof ComputeCommandsFrontend frontend) || frontend.vim$isInRenderPass();
    }
}