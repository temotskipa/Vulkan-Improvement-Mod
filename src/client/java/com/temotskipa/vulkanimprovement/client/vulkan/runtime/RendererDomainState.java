package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

record RendererDomainState(String path, String reason, boolean cpuDrawLists, boolean gpuWorkQueues, boolean gpuMeshGeneration, boolean gpuVisibility) {
	static RendererDomainState vanilla(String reason) {
		return new RendererDomainState("vanilla", reason, true, false, false, false);
	}
	
	static RendererDomainState meshTerrainGpu(String reason) {
		return new RendererDomainState("mesh-shader-terrain", reason, true, true, false, true);
	}
}