package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanFeatureRequirements;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Offers the mod's extensions and features to Minecraft as an optional feature set. Minecraft
 * enables it on a device that supports all of it and otherwise draws with Vulkan without it, so a
 * GPU the mod cannot use still gets Minecraft's own Vulkan renderer.
 */
@Mixin(VulkanFeatureSets.class)
public final class VulkanFeatureSetsMixin {
	@Unique
	private static final Logger VIM_LOGGER = LogUtils.getLogger();

	@Unique
	private static boolean vim$announced;

	@Inject(method = "optionalFeatureSets", at = @At("RETURN"))
	private static void vim$offerModernVulkan(CallbackInfoReturnable<Set<FeatureSet>> cir) {
		FeatureSet featureSet = VulkanFeatureRequirements.optionalFeatureSet();
		cir.getReturnValue().add(featureSet);
		if (!vim$announced) {
			vim$announced = true;
			VIM_LOGGER.info("[Vulkan Improvement] Offering the optional Vulkan feature set: {}", featureSet);
		}
	}
}
