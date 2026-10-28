package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommandsBackend;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommandsFrontend;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Compute dispatch on Minecraft's frontend command encoder: checked as Minecraft checks its own
 * commands, then handed to the backend, which records it.
 */
@Mixin(FrontendCommandEncoder.class)
public final class FrontendCommandEncoderMixin implements ComputeCommandsFrontend {
    @Shadow
    @Final
    private CommandEncoderBackend backend;
    
    @Shadow
    private boolean isInRenderPass;
    
    @Override
    public void vim$dispatchCompute(Supplier<String> label, ComputePipeline pipeline, ByteBuffer pushConstants, int groupsX, int groupsY, int groupsZ) {
        if (this.isInRenderPass) {
            throw new IllegalStateException("Close the existing render pass before performing additional commands");
        }
        if (pipeline.handle() == 0L || pipeline.layout() == 0L) {
            throw new IllegalStateException("Compute pipeline " + label.get() + " is not created");
        }
        if (pushConstants.remaining() > pipeline.pushConstantBytes()) {
            throw new IllegalArgumentException("Cannot push " + pushConstants.remaining() + " bytes of constants to a pipeline that takes " + pipeline.pushConstantBytes());
        }
        if (groupsX < 0 || groupsY < 0 || groupsZ < 0) {
            throw new IllegalArgumentException("Workgroup counts must not be negative: " + groupsX + "x" + groupsY + "x" + groupsZ);
        }
        if (!(this.backend instanceof ComputeCommandsBackend computeBackend)) {
            throw new UnsupportedOperationException("No compute dispatch on " + this.backend.getClass().getName());
        }
        computeBackend.vim$dispatchCompute(label, pipeline, pushConstants, groupsX, groupsY, groupsZ);
    }
    
    @Override
    public boolean vim$isInRenderPass() {
        return this.isInRenderPass;
    }
}