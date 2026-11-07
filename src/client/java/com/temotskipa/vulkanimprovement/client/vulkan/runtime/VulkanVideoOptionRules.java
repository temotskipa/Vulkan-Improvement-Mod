package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

/**
 * Which Vulkan Improvement video options can do anything, given the options they depend on and what
 * the device and the install have. One that cannot is greyed out, as Minecraft greys out its
 * anisotropy slider: the path that reads it does not run as things stand.
 */
record VulkanVideoOptionRules(boolean meshTerrainSupported, boolean meshTerrainOn, boolean captureBootstrapOn, boolean cancelVanillaTerrainOn, boolean fullCapturedTerrainOn, boolean gpuTaskCommandsOn, boolean gpuDrivenTerrainOn, boolean improvedTransparencyOn, boolean shadingRateSupported, boolean shadingRateOn, boolean presentPacingSupported, boolean vsyncOn, boolean gpuTimerSupported, boolean worldgenProviderPresent) {
	boolean meshTerrain() {
		return this.meshTerrainSupported;
	}

	/**
	 * Mesh terrain captures terrain anyway.
	 */
	boolean captureBootstrap() {
		return this.meshTerrainSupported && !this.meshTerrainOn;
	}

	/**
	 * What only captured terrain reads: its uploads, its buffers, its metadata.
	 */
	boolean capturedTerrain() {
		return this.meshTerrainSupported && (this.meshTerrainOn || this.captureBootstrapOn);
	}

	/**
	 * What every terrain draw of the mesh path reads.
	 */
	boolean drawnMeshTerrain() {
		return this.meshTerrainSupported && this.meshTerrainOn;
	}

	/**
	 * What chooses how terrain in view is found. Full captured terrain draws everything instead.
	 */
	boolean terrainInView() {
		return drawnMeshTerrain() && !this.fullCapturedTerrainOn;
	}

	boolean gpuDrivenTerrain() {
		return terrainInView() && this.cancelVanillaTerrainOn;
	}

	/**
	 * Occlusion culling splits GPU-driven terrain in two around where Minecraft closes its opaque
	 * pass, which it does apart from everything else only with improved transparency.
	 */
	boolean occlusionCulling() {
		return gpuDrivenTerrain() && this.gpuDrivenTerrainOn && this.improvedTransparencyOn;
	}

	/**
	 * Entities are tested where Minecraft closes its opaque pass too, whoever draws the terrain.
	 */
	boolean entityOcclusionCulling() {
		return this.meshTerrainSupported && this.improvedTransparencyOn;
	}

	/**
	 * The mesh stage's culling, for meshlets drawn from a list. Pages have the task stage's.
	 */
	boolean meshletCulling() {
		return drawnMeshTerrain() && (this.fullCapturedTerrainOn || !this.gpuTaskCommandsOn);
	}

	boolean taskMeshletCulling() {
		return terrainInView() && (this.gpuTaskCommandsOn || this.cancelVanillaTerrainOn && this.gpuDrivenTerrainOn);
	}

	boolean taskPageCulling() {
		return terrainInView() && this.gpuTaskCommandsOn;
	}

	boolean shadingRate() {
		return drawnMeshTerrain() && this.shadingRateSupported;
	}

	boolean shadingRateSize() {
		return shadingRate() && this.shadingRateOn;
	}

	/**
	 * Pacing waits for presents the swapchain shows in order, which it does with VSync on.
	 */
	boolean presentPacing() {
		return this.presentPacingSupported && this.vsyncOn;
	}

	boolean gpuTimer() {
		return this.gpuTimerSupported;
	}

	boolean worldgenProvider() {
		return this.worldgenProviderPresent;
	}
}
