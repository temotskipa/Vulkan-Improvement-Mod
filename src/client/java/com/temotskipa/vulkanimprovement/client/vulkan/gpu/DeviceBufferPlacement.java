package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.api.buffers.GpuBuffer;

/**
 * Where a buffer's memory lives and how the CPU reaches it, which {@link DeviceBuffer} asks of
 * Minecraft through the mapping usages of {@link GpuBuffer}.
 */
public enum DeviceBufferPlacement {
	/**
	 * Device memory the CPU cannot map; written by copies only. No mapping usage.
	 */
	DEVICE,
	/**
	 * Device memory the CPU maps and writes in order, for what the GPU reads or updates every
	 * frame. Where the device has no such memory, host memory the GPU reaches over the bus.
	 * Any thread may reach it, as diagnostics do. {@link GpuBuffer#USAGE_MAP_WRITE}, which
	 * Minecraft places in device memory the CPU writes in order.
	 */
	DEVICE_MAPPED,
	/**
	 * Host memory the CPU reads and writes at random, for staging and for what the CPU reads
	 * back. Only the thread that made the buffer reaches it, and gives it back, which lets its
	 * memory's lifetime end at no cost: ending one any thread may reach waits on every thread,
	 * some 30 to 50 microseconds with as many threads as the game runs.
	 * {@link GpuBuffer#USAGE_MAP_READ} and {@link GpuBuffer#USAGE_MAP_WRITE}, which Minecraft
	 * places in cached host memory.
	 */
	HOST
}
