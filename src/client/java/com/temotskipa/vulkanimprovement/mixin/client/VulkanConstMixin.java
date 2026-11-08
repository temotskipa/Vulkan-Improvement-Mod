package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DescriptorStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import org.lwjgl.vulkan.EXTDescriptorBuffer;
import org.lwjgl.vulkan.EXTDescriptorHeap;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Teaches Minecraft's Vulkan backend the buffer usages the mod adds: a buffer made with
 * {@link ShaderStorage#USAGE_SHADER_STORAGE} is a storage buffer, and one made with a usage of
 * {@link DescriptorStorage} a descriptor heap or descriptor buffer, each with a device address.
 */
@Mixin(VulkanConst.class)
public final class VulkanConstMixin {
	@ModifyReturnValue(method = "bufferUsageToVk", at = @At("RETURN"))
	private static int vim$addModUsages(int vkUsage, @Local(argsOnly = true, name = "usage") int usage) {
		if ((usage & DescriptorStorage.ADDRESSED_USAGES) == 0) {
			return vkUsage;
		}
		int added = VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
		if ((usage & ShaderStorage.USAGE_SHADER_STORAGE) != 0) {
			added |= VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
		}
		if ((usage & DescriptorStorage.USAGE_DESCRIPTOR_HEAP) != 0) {
			added |= EXTDescriptorHeap.VK_BUFFER_USAGE_DESCRIPTOR_HEAP_BIT_EXT;
		}
		if ((usage & DescriptorStorage.USAGE_SAMPLER_DESCRIPTORS) != 0) {
			added |= EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
		}
		if ((usage & DescriptorStorage.USAGE_RESOURCE_DESCRIPTORS) != 0) {
			added |= EXTDescriptorBuffer.VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT;
		}
		return vkUsage | added;
	}
}
