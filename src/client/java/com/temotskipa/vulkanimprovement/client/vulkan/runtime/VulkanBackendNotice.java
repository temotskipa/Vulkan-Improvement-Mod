package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanFeatureRequirements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells the player when the mod's features are off because Minecraft does not draw with Vulkan: a
 * toast once Minecraft has loaded, and a line under the title of Video Settings while OpenGL runs or
 * is chosen. A GPU that lacks what the mod requires of a Vulkan device leaves Minecraft on OpenGL as
 * well, since Minecraft then finds no device it can use.
 */
public final class VulkanBackendNotice {
	/**
	 * The colour of Minecraft's own restart warning in Video Settings.
	 */
	private static final int WARNING_COLOR = -2142128;
	/**
	 * As long as Minecraft shows its warning about an unsecure server.
	 */
	private static final SystemToastId TOAST = new SystemToastId(10000L);
	private static final int LISTED_CAPABILITIES = 3;
	private static boolean toastShown;
	
	private VulkanBackendNotice() {
	}
	
	/**
	 * Shows the toast on the first tick after Minecraft has loaded, if the mod is off. Called every
	 * tick.
	 */
	public static void showToastOnce(Minecraft minecraft) {
		if (toastShown || minecraft.gui.overlay() != null) {
			return;
		}
		toastShown = true;
		if (!VulkanImprovementRuntime.isVulkanBackendActive()) {
			SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, Component.translatable("vulkanimprovement.notice.title"), reason(minecraft));
		}
	}
	
	/**
	 * The line Video Settings shows under its title, or null while the mod runs and will still run
	 * after a restart.
	 */
	public static @Nullable Component videoSettingsLine(Minecraft minecraft) {
		if (minecraft.options.preferredGraphicsBackend().get() == PreferredGraphicsApi.OPENGL) {
			return Component.translatable("vulkanimprovement.notice.openGlChosen").withColor(WARNING_COLOR);
		}
		if (!VulkanImprovementRuntime.isVulkanBackendActive()) {
			return Component.translatable("vulkanimprovement.notice.openGlRunning").withColor(WARNING_COLOR);
		}
		return null;
	}
	
	/**
	 * Why Minecraft runs on OpenGL. Minecraft keeps the failure of a backend it tried, so with none
	 * kept it never tried Vulkan.
	 */
	private static Component reason(Minecraft minecraft) {
		BackendCreationException failure = ((BackendFailureHolder) minecraft).vim$backendCreationException();
		if (failure == null) {
			boolean chosen = minecraft.options.preferredGraphicsBackend().get() == PreferredGraphicsApi.OPENGL;
			return Component.translatable(chosen ? "vulkanimprovement.notice.reason.chosen" : "vulkanimprovement.notice.reason.launchOption");
		}
		List<String> missing = failure.getMissingCapabilities();
		if (onlyTheModRequires(missing, modCapabilities(), minecraftCapabilities())) {
			return Component.translatable("vulkanimprovement.notice.reason.unsupported", listed(missing));
		}
		if (missing.isEmpty()) {
			return Component.translatable("vulkanimprovement.notice.reason.failed", failure.getMessage());
		}
		return Component.translatable("vulkanimprovement.notice.reason.failedMissing", failure.getMessage(), listed(missing));
	}
	
	/**
	 * Whether the mod alone turned the device down: everything it lacks the mod requires and
	 * Minecraft does not, so Minecraft could draw with Vulkan on it without the mod.
	 */
	static boolean onlyTheModRequires(List<String> missing, Set<String> modCapabilities, Set<String> minecraftCapabilities) {
		return !missing.isEmpty() && missing.stream().allMatch(capability -> modCapabilities.contains(capability) && !minecraftCapabilities.contains(capability));
	}
	
	private static Set<String> modCapabilities() {
		Set<String> capabilities = new HashSet<>(VulkanFeatureRequirements.requiredDeviceExtensions());
		for (VulkanFeature feature : VulkanFeatureRequirements.requiredDeviceFeatures()) {
			capabilities.add(feature.name());
		}
		capabilities.add(VulkanFeatureRequirements.VULKAN_14_CAPABILITY_NAME);
		return capabilities;
	}
	
	/**
	 * What Minecraft's own feature sets require, told apart from the mod's by their names.
	 */
	private static Set<String> minecraftCapabilities() {
		Set<String> capabilities = new HashSet<>();
		for (FeatureSet featureSet : VulkanFeatureSets.requiredFeatureSets()) {
			if (!featureSet.name().startsWith(VulkanFeatureRequirements.FEATURE_SET_PREFIX)) {
				capabilities.addAll(featureSet.extensions());
				for (VulkanFeature feature : featureSet.features()) {
					capabilities.add(feature.name());
				}
			}
		}
		return capabilities;
	}
	
	/**
	 * The first few capabilities, so that the toast stays a few lines tall.
	 */
	private static Component listed(List<String> capabilities) {
		List<String> distinct = capabilities.stream().distinct().toList();
		String first = String.join(", ", distinct.subList(0, Math.min(LISTED_CAPABILITIES, distinct.size())));
		if (distinct.size() <= LISTED_CAPABILITIES) {
			return Component.literal(first);
		}
		return Component.translatable("vulkanimprovement.notice.more", first, distinct.size() - LISTED_CAPABILITIES);
	}
}