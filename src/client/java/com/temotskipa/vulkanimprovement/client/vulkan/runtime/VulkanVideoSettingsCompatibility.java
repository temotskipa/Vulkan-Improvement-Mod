package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import net.minecraft.client.Minecraft;

public final class VulkanVideoSettingsCompatibility {
	private VulkanVideoSettingsCompatibility() {
	}
	
	/**
	 * The video settings a capture has to be read with.
	 */
	public static String describe() {
		Minecraft minecraft = Minecraft.getInstance();
		var options = minecraft.options;
		return "graphicsPreset=" + options.graphicsPreset().get() + ", renderDistance=" + options.getEffectiveRenderDistance() + ", simulationDistance=" + options.simulationDistance().get() + ", improvedTransparency=" + options.improvedTransparency().get() + ", shaderTransparencyActive=" + minecraft.gameRenderer.useImprovedTransparency() + ", textureFiltering=" + options.textureFiltering().get() + ", maxAnisotropy=" + options.maxAnisotropyValue() + ", mipmapLevels=" + options.mipmapLevels().get() + ", chunkSectionFadeInTime=" + options.chunkSectionFadeInTime().get() + ", enableVsync=" + options.enableVsync().get() + ", framerateLimit=" + options.framerateLimit().get() + ", fullscreen=" + options.fullscreen().get() + ", exclusiveFullscreen=" + options.exclusiveFullscreen().get() + ", preferredGraphicsBackend=" + options.preferredGraphicsBackend().get() + ", restartRequired=" + options.isRestartRequiredToApplyVideoSettings() + ", vulkanBackendActive=" + VulkanImprovementRuntime.isVulkanBackendActive() + ", modRendererActive=" + VulkanImprovementRuntime.isModRendererActive() + ", videoOptionsVisible=" + VulkanImprovementRuntime.shouldShowVideoOptions(minecraft);
	}
}