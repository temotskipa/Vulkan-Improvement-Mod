package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Implemented on Minecraft's Vulkan command encoder by a mixin.
 */
public interface ComputeCommandsBackend {
    void vim$dispatchCompute(Supplier<String> label, ComputePipeline pipeline, ByteBuffer pushConstants, int groupsX, int groupsY, int groupsZ);
}