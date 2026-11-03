package com.temotskipa.vulkanimprovement.client.vulkan.device;

import java.util.List;

/**
 * Whether Minecraft's Vulkan device has everything the mod needs. Minecraft enables the mod's
 * feature set as an optional one, when the device supports all of it, so a device without it still
 * draws with Vulkan and the mod stays off. Recorded as Minecraft creates the device, before its
 * allocator and the device object that the mod's paths are configured with.
 */
public final class VulkanImprovementSupport {
	private static volatile boolean enabled;
	private static volatile List<String> missing = List.of();

	private VulkanImprovementSupport() {
	}

	/**
	 * @param enabledOnDevice whether the device being created enables the mod's feature set
	 * @param lacking         what the device lacks of it, empty when it is enabled
	 */
	public static void deviceCreating(boolean enabledOnDevice, List<String> lacking) {
		enabled = enabledOnDevice;
		missing = List.copyOf(lacking);
	}

	/**
	 * Whether the mod's paths can run on Minecraft's Vulkan device.
	 */
	public static boolean enabled() {
		return enabled;
	}

	/**
	 * What the device lacks of the mod's feature set, empty when it has all of it.
	 */
	public static List<String> missing() {
		return missing;
	}

	public static String describe() {
		return enabled ? "enabled" : "missing=" + missing;
	}
}
