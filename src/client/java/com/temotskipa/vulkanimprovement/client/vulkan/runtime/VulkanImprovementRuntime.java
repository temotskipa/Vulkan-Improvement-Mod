package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;

public final class VulkanImprovementRuntime {
	private static volatile VulkanRuntimeBackend backend = new VulkanRuntimeBackend(null, false);
	
	private VulkanImprovementRuntime() {
	}
	
	public static boolean isModRendererActive() {
		if (!isModAvailable()) {
			return false;
		}
		RendererLifecycleState state = RendererCoreServices.get().currentLifecycleState();
		return state == RendererLifecycleState.READY || state == RendererLifecycleState.FAILED;
	}
	
	/**
	 * Whether Minecraft renders through Vulkan. Asked once per section layer and frame, so the
	 * answer is kept for as long as the device is the same.
	 */
	public static boolean isVulkanBackendActive() {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return false;
		}
		VulkanRuntimeBackend known = backend;
		if (known.device() != device) {
			known = new VulkanRuntimeBackend(device, "Vulkan".equalsIgnoreCase(device.getDeviceInfo().backendName()));
			backend = known;
		}
		return known.vulkan();
	}
	
	/**
	 * Whether the mod's paths can run: Minecraft draws with Vulkan on a device that enables
	 * everything the mod needs.
	 */
	public static boolean isModAvailable() {
		return isVulkanBackendActive() && VulkanImprovementSupport.enabled();
	}

	public static boolean shouldShowVideoOptions(Minecraft minecraft) {
		if (minecraft == null) {
			return false;
		}
		PreferredGraphicsApi preferred = minecraft.options.preferredGraphicsBackend().get();
		if (isVulkanBackendActive()) {
			return true;
		}
		if (preferred == PreferredGraphicsApi.OPENGL) {
			return false;
		}
		return preferred == PreferredGraphicsApi.VULKAN;
	}
}