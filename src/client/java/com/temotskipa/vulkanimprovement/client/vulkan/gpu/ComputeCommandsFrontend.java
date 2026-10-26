package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Implemented on Minecraft's frontend command encoder by a mixin.
 */
public interface ComputeCommandsFrontend {
    void vim$dispatchCompute(Supplier<String> label, ComputePipeline pipeline, ByteBuffer pushConstants, int groupsX, int groupsY, int groupsZ);
    
    boolean vim$isInRenderPass();
}