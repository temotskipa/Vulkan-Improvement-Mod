package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

public final class RendererDomainObserver {
    private RendererDomainObserver() {
    }
    
    public static void observeRendererReady() {
        RendererDomainRegistry registry = RendererDomainRegistry.get();
        registry.set(RendererDomain.TERRAIN, RendererDomainRegistry.DomainState.meshTerrainGpu("terrain mesh-shader path with GPU work queues configured"));
        for (RendererDomain domain : RendererDomain.values()) {
            if (domain == RendererDomain.TERRAIN || domain == RendererDomain.DIAGNOSTICS) {
                continue;
            }
            registry.set(domain, RendererDomainRegistry.DomainState.vanilla("observed vanilla Minecraft renderer path"));
        }
        registry.set(RendererDomain.DIAGNOSTICS, RendererDomainRegistry.DomainState.vanilla("log output, video options, and vim.dumpRendererDiagnostics key"));
    }
}