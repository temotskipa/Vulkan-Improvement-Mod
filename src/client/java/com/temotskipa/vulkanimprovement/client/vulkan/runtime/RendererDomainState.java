package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import org.jspecify.annotations.Nullable;

record RendererDomainState(String path, String reason, boolean cpuDrawLists, boolean gpuWorkQueues, boolean gpuMeshGeneration, boolean gpuVisibility) {
	static RendererDomainState vanilla(String reason) {
		return new RendererDomainState("vanilla", reason, true, false, false, false);
	}

	static RendererDomainState meshTerrainGpu(String reason) {
		return new RendererDomainState("mesh-shader-terrain", reason, true, true, false, true);
	}

	/**
	 * Minecraft's renderer submits, orders and draws the domain, and the GPU writes the vertices it
	 * draws.
	 */
	static RendererDomainState gpuVertices(String reason) {
		return new RendererDomainState("gpu-vertices", reason, true, false, true, false);
	}

	/**
	 * The state of a domain whose vertices the GPU writes while its path is on: vanilla, with the
	 * reason, when the path is off.
	 */
	static RendererDomainState gpuVerticesOr(@Nullable String offReason, String reason) {
		return offReason == null ? gpuVertices(reason) : vanilla(offReason);
	}
}
