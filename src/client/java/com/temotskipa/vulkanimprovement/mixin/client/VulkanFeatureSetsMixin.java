package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanFeatureRequirements;
import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * Adds the mod's required extensions and features to the feature sets Minecraft requires of a
 * Vulkan device. Minecraft then checks, reports and enables them with its own.
 */
@Mixin(VulkanFeatureSets.class)
public final class VulkanFeatureSetsMixin {
    @Unique
    private static final Logger VIM_LOGGER = LogUtils.getLogger();

    @Unique
    private static boolean vim$announced;

    @Inject(method = "requiredFeatureSets", at = @At("RETURN"))
    private static void vim$requireModernVulkan(CallbackInfoReturnable<Set<FeatureSet>> cir) {
        List<FeatureSet> featureSets = VulkanFeatureRequirements.requiredFeatureSets();
        cir.getReturnValue().addAll(featureSets);
        if (!vim$announced) {
            vim$announced = true;
            VIM_LOGGER.info("[Vulkan Improvement] Requiring Vulkan feature sets: {}", featureSets);
        }
    }
}
