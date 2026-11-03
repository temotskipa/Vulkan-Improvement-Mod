package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drops Minecraft's distinction of menus outside a world, which it holds to 60 frames a second
 * whatever Max Framerate says: the title screen and the other menus follow the setting as a world
 * does.
 */
@Mixin(FramerateLimitTracker.class)
public final class FramerateLimitTrackerMixin {
	@ModifyReturnValue(method = "getThrottleReason", at = @At("RETURN"))
	private FramerateThrottleReason vim$noMenuThrottle(FramerateThrottleReason reason) {
		return reason == FramerateThrottleReason.OUT_OF_LEVEL_MENU ? FramerateThrottleReason.NONE : reason;
	}
}
