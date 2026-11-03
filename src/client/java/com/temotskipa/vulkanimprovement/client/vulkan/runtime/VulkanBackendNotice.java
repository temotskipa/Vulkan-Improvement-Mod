package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.renderpearl.api.device.BackendCreationException;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Tells the player when the mod's features are off: a toast once Minecraft has loaded, and a line
 * under the title of Video Settings. They are off while Minecraft runs on OpenGL or is set to, and
 * on a GPU that lacks what the mod needs of Vulkan, where Minecraft draws with Vulkan without it.
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
		else if (!VulkanImprovementSupport.enabled()) {
			SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, Component.translatable("vulkanimprovement.notice.title"), Component.translatable("vulkanimprovement.notice.reason.unsupportedGpu", listed(VulkanImprovementSupport.missing())));
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
		if (!VulkanImprovementSupport.enabled()) {
			return Component.translatable("vulkanimprovement.notice.unsupportedGpu").withColor(WARNING_COLOR);
		}
		return null;
	}

	/**
	 * Why Minecraft runs on OpenGL. Minecraft keeps the failure of a backend it tried, so with none
	 * kept it never tried Vulkan. The mod no longer turns a device down, so a failure is Minecraft's.
	 */
	private static Component reason(Minecraft minecraft) {
		BackendCreationException failure = ((BackendFailureHolder) minecraft).vim$backendCreationException();
		if (failure == null) {
			boolean chosen = minecraft.options.preferredGraphicsBackend().get() == PreferredGraphicsApi.OPENGL;
			return Component.translatable(chosen ? "vulkanimprovement.notice.reason.chosen" : "vulkanimprovement.notice.reason.launchOption");
		}
		List<String> missing = failure.getMissingCapabilities();
		if (missing.isEmpty()) {
			return Component.translatable("vulkanimprovement.notice.reason.failed", failure.getMessage());
		}
		return Component.translatable("vulkanimprovement.notice.reason.failedMissing", failure.getMessage(), listed(missing));
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
