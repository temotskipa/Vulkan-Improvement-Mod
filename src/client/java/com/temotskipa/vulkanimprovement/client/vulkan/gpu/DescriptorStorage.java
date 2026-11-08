package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.api.buffers.GpuBuffer;

/**
 * Minecraft buffers that hold descriptors for the GPU to find by device address: the mod's descriptor
 * heaps and descriptor buffers. {@code GpuDevice.createBuffer} makes one when given one of these
 * usages, which a mixin on Minecraft's Vulkan backend turns into the descriptor usage and device
 * address use, as it does {@link ShaderStorage#USAGE_SHADER_STORAGE}.
 */
public final class DescriptorStorage {
	/**
	 * A {@link GpuBuffer} usage Minecraft leaves unused: a descriptor heap.
	 */
	public static final int USAGE_DESCRIPTOR_HEAP = 1 << 17;
	/**
	 * A {@link GpuBuffer} usage Minecraft leaves unused: a descriptor buffer holding samplers.
	 */
	public static final int USAGE_SAMPLER_DESCRIPTORS = 1 << 18;
	/**
	 * A {@link GpuBuffer} usage Minecraft leaves unused: a descriptor buffer holding resources.
	 */
	public static final int USAGE_RESOURCE_DESCRIPTORS = 1 << 19;
	/**
	 * The usages of the mod's that give a buffer a device address.
	 */
	public static final int ADDRESSED_USAGES = ShaderStorage.USAGE_SHADER_STORAGE | USAGE_DESCRIPTOR_HEAP | USAGE_SAMPLER_DESCRIPTORS | USAGE_RESOURCE_DESCRIPTORS;
	
	private DescriptorStorage() {
	}
}
