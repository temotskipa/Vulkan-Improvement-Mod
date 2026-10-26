package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

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
}