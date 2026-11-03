package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainSamplingState;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers whether Minecraft samples terrain textures with rotated-grid supersampling this frame,
 * so mesh terrain samples them as Minecraft's terrain does.
 */
@Mixin(GlobalSettingsUniform.class)
public final class GlobalSettingsUniformMixin {
	@Inject(method = "update", at = @At("HEAD"))
	private void vim$rememberTextureSampling(int width, int height, double glintAlpha, long gameTime, float worldPartialTicks, int menuBlurRadius, Vec3 cameraPos, boolean useRgss, CallbackInfo ci) {
		TerrainSamplingState.record(useRgss);
	}
}
