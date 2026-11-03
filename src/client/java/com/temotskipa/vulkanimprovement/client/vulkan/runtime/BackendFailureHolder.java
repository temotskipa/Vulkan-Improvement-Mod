package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.renderpearl.api.device.BackendCreationException;
import org.jspecify.annotations.Nullable;

/**
 * Implemented on Minecraft by a mixin.
 */
public interface BackendFailureHolder {
	/**
	 * Why a graphics backend Minecraft tried at startup could not be created, or null when the
	 * first one it tried was.
	 */
	@Nullable BackendCreationException vim$backendCreationException();
}
