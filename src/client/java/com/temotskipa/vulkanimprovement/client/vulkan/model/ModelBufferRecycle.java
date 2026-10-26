package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuFence;

import java.util.List;

record ModelBufferRecycle(List<GpuBuffer> buffers, GpuFence fence) {
    boolean completed() {
        return this.fence.awaitCompletion(0L);
    }
    
    void release() {
        this.fence.close();
    }
}