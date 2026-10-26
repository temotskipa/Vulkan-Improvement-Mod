package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.GpuFence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Minecraft buffers written once a frame, reused once the GPU is done with the frame that used them,
 * the way Minecraft's staged vertex buffer pools its own: a buffer is reused for a size up to four
 * times smaller, and one nothing asked for in a frame is given back. Used from the render thread
 * only.
 */
final class ModelBufferPool implements AutoCloseable {
    private static final long SIZE_STEP = 256L * 1024L;
    private static final long MAX_REUSE_FACTOR = 4L;
    
    private final Supplier<String> label;
    private final int usage;
    private final List<GpuBuffer> available = new ArrayList<>();
    private final List<GpuBuffer> usedThisFrame = new ArrayList<>();
    private final List<ModelBufferRecycle> pendingRecycle = new ArrayList<>();
    
    ModelBufferPool(Supplier<String> label, int usage) {
        this.label = label;
        this.usage = usage;
    }
    
    GpuBuffer acquire(long minBytes) {
        long size = Math.ceilDiv(Math.max(minBytes, 1L), SIZE_STEP) * SIZE_STEP;
        GpuBuffer buffer = takeBestAvailable(size, size * MAX_REUSE_FACTOR);
        if (buffer == null) {
            buffer = RenderSystem.getDevice().createBuffer(this.label, this.usage, size);
        }
        this.usedThisFrame.add(buffer);
        return buffer;
    }
    
    private @Nullable GpuBuffer takeBestAvailable(long minSize, long maxSize) {
        int best = -1;
        long bestSize = maxSize + 1L;
        for (int i = 0; i < this.available.size(); i++) {
            long size = this.available.get(i).size();
            if (size >= minSize && size < bestSize) {
                best = i;
                bestSize = size;
            }
        }
        return best < 0 ? null : this.available.remove(best);
    }
    
    /**
     * Fences what the frame used, gives back what it did not, and takes back what the GPU has
     * finished with. Called outside any render pass.
     */
    void endFrame() {
        if (!this.usedThisFrame.isEmpty()) {
            GpuFence fence = RenderSystem.getDevice().createCommandEncoder().createFence();
            this.pendingRecycle.add(new ModelBufferRecycle(List.copyOf(this.usedThisFrame), fence));
            this.usedThisFrame.clear();
        }
        this.available.forEach(GpuBuffer::close);
        this.available.clear();
        this.pendingRecycle.removeIf(pending -> {
            if (!pending.completed()) {
                return false;
            }
            pending.release();
            this.available.addAll(pending.buffers());
            return true;
        });
    }
    
    @Override
    public void close() {
        this.available.forEach(GpuBuffer::close);
        this.usedThisFrame.forEach(GpuBuffer::close);
        for (ModelBufferRecycle pending : this.pendingRecycle) {
            pending.buffers().forEach(GpuBuffer::close);
            pending.release();
        }
        this.available.clear();
        this.usedThisFrame.clear();
        this.pendingRecycle.clear();
    }
}
