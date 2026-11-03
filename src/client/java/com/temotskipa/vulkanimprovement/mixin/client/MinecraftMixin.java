package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.api.device.BackendCreationException;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.BackendFailureHolder;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Lets the mod read why a graphics backend could not be created, which Minecraft keeps only for its
 * crash report.
 */
@Mixin(Minecraft.class)
public final class MinecraftMixin implements BackendFailureHolder {
	@Shadow
	private @Nullable BackendCreationException backendCreationException;
	
	@Override
	public @Nullable BackendCreationException vim$backendCreationException() {
		return this.backendCreationException;
	}
}