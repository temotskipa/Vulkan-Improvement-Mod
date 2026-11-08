package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.Destroyable;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * A Minecraft buffer the mod's GPU passes reach through its device address and, where it is mapped,
 * the CPU through a checked segment. It is made by {@code GpuDevice.createBuffer}, or by Minecraft's
 * Vulkan device itself while Minecraft is still making the device, and stays mapped through
 * Minecraft's {@code GpuBuffer.map} from when it is made until it is given back. Minecraft's mappable
 * buffers are host-coherent, so what the CPU writes reaches the GPU, and what the GPU writes the CPU,
 * without a flush.
 */
public final class DeviceBuffer {
	private final String label;
	private final GpuBuffer buffer;
	private final long vkBuffer;
	private final long size;
	private final long deviceAddress;
	// The view Minecraft maps the whole buffer in, kept open until the buffer is given back. Null
	// where the CPU cannot map the buffer.
	private final @Nullable MappedView mapping;
	// How long the CPU may reach the mapped memory: until the buffer is given back.
	private final @Nullable Arena mappedLifetime;
	// The mapped memory. Every access is checked against the buffer's size, and fails once the
	// buffer is given back rather than reach memory that is unmapped.
	private final @Nullable MemorySegment mapped;
	private boolean closed;
	
	/**
	 * @param usage Minecraft's buffer usages, with the mod's from {@link ShaderStorage} and
	 *              {@link DescriptorStorage}; the placement adds the mapping usages
	 */
	public DeviceBuffer(VulkanDevice device, String label, long size, int usage, DeviceBufferPlacement placement) {
		this.label = label;
		this.size = size;
		int placedUsage = usage | switch (placement) {
			case DEVICE -> 0;
			case DEVICE_MAPPED -> GpuBuffer.USAGE_MAP_WRITE;
			case HOST -> GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE;
		};
		GpuDevice frontend = RenderSystem.tryGetDevice();
		this.buffer = frontend != null ? frontend.createBuffer(() -> label, placedUsage, size) : device.createBuffer(() -> label, placedUsage, size);
		this.vkBuffer = ((VulkanGpuBuffer) this.buffer).vkBuffer();
		this.deviceAddress = (usage & DescriptorStorage.ADDRESSED_USAGES) != 0 && this.buffer instanceof ShaderStorageAddressable addressable ? addressable.vim$deviceAddress() : 0L;
		if (placement == DeviceBufferPlacement.DEVICE) {
			this.mapping = null;
			this.mappedLifetime = null;
			this.mapped = null;
		}
		else {
			this.mapping = this.buffer.map(0L, size, placement == DeviceBufferPlacement.HOST, true);
			this.mappedLifetime = placement == DeviceBufferPlacement.HOST ? Arena.ofConfined() : Arena.ofShared();
			this.mapped = mappedSegment(MemoryUtil.memAddress(this.mapping.data()), size, this.mappedLifetime);
		}
	}
	
	/**
	 * The mapped memory as a segment of the buffer's size. Minecraft hands over a byte buffer of the
	 * memory, so this is the one place the mod has to be told how much there is; every access after
	 * it is checked. Making a segment of an address needs native access, which Minecraft starts the
	 * game with.
	 */
	private static MemorySegment mappedSegment(long address, long size, Arena lifetime) {
		return MemorySegment.ofAddress(address).reinterpret(size, lifetime, null);
	}
	
	public long vkBuffer() {
		return this.vkBuffer;
	}
	
	public String label() {
		return this.label;
	}
	
	public boolean closed() {
		return this.closed;
	}
	
	public long size() {
		return this.size;
	}
	
	/**
	 * The buffer's device address, or 0 for one made without a usage that gives it one.
	 */
	public long deviceAddress() {
		return this.deviceAddress;
	}
	
	public boolean hostVisible() {
		return this.mapped != null;
	}
	
	/**
	 * The mapped memory, written and read until the buffer is given back.
	 */
	public MemorySegment mapped() {
		if (this.mapped == null) {
			throw new IllegalStateException(this.label + " is not host visible");
		}
		return this.mapped;
	}
	
	/**
	 * Gives the buffer back. The CPU is done with it from here on, though the GPU may still read it
	 * until Minecraft destroys it, which Minecraft does once the frames that use it are done.
	 */
	public synchronized void close() {
		if (!this.closed) {
			this.closed = true;
			endMappedAccess();
			this.buffer.close();
		}
	}
	
	private void endMappedAccess() {
		if (this.mappedLifetime != null && this.mappedLifetime.scope().isAlive()) {
			this.mappedLifetime.close();
		}
		if (this.mapping != null) {
			this.mapping.close();
		}
	}
	
	/**
	 * Destroys the buffer at once, when the renderer shuts down with the GPU done with it. A buffer
	 * already given back is Minecraft's to destroy.
	 */
	public synchronized void destroyNow() {
		if (!this.closed) {
			this.closed = true;
			endMappedAccess();
			((Destroyable) this.buffer).destroy();
		}
	}
}
