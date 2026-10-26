package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

/**
 * Where a buffer's memory lives and how the CPU reaches it.
 */
public enum DeviceBufferPlacement {
    /**
     * Device memory the CPU cannot map; written by copies only.
     */
    DEVICE,
    /**
     * Device memory the CPU maps and writes in order, for what the GPU reads or updates every
     * frame. Where the device has no such memory, host memory the GPU reaches over the bus.
     * Any thread may reach it, as diagnostics do.
     */
    DEVICE_MAPPED,
    /**
     * Host memory the CPU reads and writes at random, for staging. Only the thread that made
     * the buffer reaches it, and gives it back, which lets its memory's lifetime end at no cost:
     * ending one any thread may reach waits on every thread, some 30 to 50 microseconds with as
     * many threads as the game runs.
     */
    HOST
}