package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.api.buffers.GpuBuffer;

/**
 * Minecraft buffers that shaders read and write through their device address. A buffer is one when
 * {@code GpuDevice.createBuffer} is given {@link #USAGE_SHADER_STORAGE}; a mixin on Minecraft's
 * Vulkan backend turns that usage into storage and device-address use, and gives its buffers their
 * address.
 */
public final class ShaderStorage {
    /**
     * A {@link GpuBuffer} usage Minecraft leaves unused: shaders read and write the buffer through
     * its device address.
     */
    public static final int USAGE_SHADER_STORAGE = 1 << 16;
    
    private ShaderStorage() {
    }
    
    /**
     * The device address of a buffer made with {@link #USAGE_SHADER_STORAGE}.
     */
    public static long address(GpuBuffer buffer) {
        if ((buffer.usage() & USAGE_SHADER_STORAGE) == 0) {
            throw new IllegalArgumentException("Buffer needs USAGE_SHADER_STORAGE to have a device address");
        }
        if (buffer.isClosed()) {
            throw new IllegalStateException("Buffer already closed");
        }
        if (!(buffer instanceof Addressable addressable)) {
            throw new UnsupportedOperationException("Only Vulkan buffers have a device address, not " + buffer.getClass().getName());
        }
        return addressable.vim$deviceAddress();
    }
    
    /**
     * Implemented on Minecraft's Vulkan buffers by a mixin.
     */
    public interface Addressable {
        long vim$deviceAddress();
    }
}