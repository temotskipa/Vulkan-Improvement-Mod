package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ParticleGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

public final class RendererDomainObserver {
	private RendererDomainObserver() {
	}

	public static void observeRendererReady() {
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		observeTerrainMode();
		for (RendererDomain domain : RendererDomain.values()) {
			if (domain == RendererDomain.TERRAIN || domain == RendererDomain.DIAGNOSTICS) {
				continue;
			}
			registry.set(domain, RendererDomainState.vanilla("observed vanilla Minecraft renderer path"));
		}
		registry.set(RendererDomain.DIAGNOSTICS, RendererDomainState.vanilla("log output, video options, and vim.dumpRendererDiagnostics key"));
		observeGpuVertexPaths();
	}

	/**
	 * Records which path draws terrain in the current mode. The mesh path is configured in every
	 * mode; it is the selected path only while it draws.
	 */
	public static void observeTerrainMode() {
		RendererDomainState state;
		if (!TerrainRendererDebugConfig.replaceVanillaTerrain()) {
			state = RendererDomainState.vanilla(TerrainRendererDebugConfig.terrainCaptureBootstrapEnabled() ? "vanilla terrain; meshlet capture runs beside it" : "vanilla terrain; mesh path configured and not selected");
		}
		else if (TerrainRendererDebugConfig.strictMeshTerrainReplacement()) {
			state = RendererDomainState.meshTerrainGpu("mesh terrain replaces vanilla solid and cutout terrain; translucent terrain stays vanilla");
		}
		else {
			state = RendererDomainState.meshTerrainGpu("mesh terrain drawn over vanilla terrain, which stays visible");
		}
		RendererDomainRegistry.get().set(RendererDomain.TERRAIN, state);
	}

	/**
	 * Records whether the GPU writes the vertices of models, items and particles. Each path decides
	 * as a world draws and can be switched at any time, so this is observed again for each report.
	 */
	public static void observeGpuVertexPaths() {
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		ModelGpuVertices models = ModelGpuVertices.get();
		RendererDomainState modelState = RendererDomainState.gpuVerticesOr(models.offReason(), models.drawsWithMeshShaders() ? "the GPU writes model vertices; mesh shaders draw those whose pipeline they replace, Minecraft the rest" : "the GPU writes model vertices; Minecraft draws them");
		registry.set(RendererDomain.ENTITIES, modelState);
		registry.set(RendererDomain.BLOCK_ENTITIES, modelState);
		registry.set(RendererDomain.ITEMS, RendererDomainState.gpuVerticesOr(models.itemOffReason(), "the GPU writes item model vertices; Minecraft draws them"));
		registry.set(RendererDomain.PARTICLES, RendererDomainState.gpuVerticesOr(ParticleGpuVertices.get().offReason(), "the GPU writes quad particle vertices; Minecraft draws them"));
	}
}
