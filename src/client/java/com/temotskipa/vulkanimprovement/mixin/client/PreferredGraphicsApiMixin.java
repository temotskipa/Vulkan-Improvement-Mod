package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Has Minecraft try Vulkan first when the preferred graphics API is left at Default. Since
 * 26.4-snapshot-7 it then tries Vulkan and OpenGL in a random order on each launch, and the mod does
 * nothing on OpenGL. A choice of OpenGL or Vulkan is kept as it is.
 */
@Mixin(PreferredGraphicsApi.class)
public final class PreferredGraphicsApiMixin {
	@ModifyReturnValue(method = "getBackendsToTry", at = @At("RETURN"))
	private GpuBackend[] vim$tryVulkanFirst(GpuBackend[] backends) {
		if ((Object) this != PreferredGraphicsApi.DEFAULT) {
			return backends;
		}
		GpuBackend[] ordered = backends.clone();
		// The sort is stable: Vulkan first, then the rest in Minecraft's order.
		Arrays.sort(ordered, Comparator.comparing((GpuBackend backend) -> !(backend instanceof VulkanBackend)));
		return ordered;
	}
}
