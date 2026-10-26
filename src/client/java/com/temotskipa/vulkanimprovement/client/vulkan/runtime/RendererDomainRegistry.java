package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RendererDomainRegistry {
    private static final RendererDomainRegistry INSTANCE = new RendererDomainRegistry();
    
    private final EnumMap<RendererDomain, RendererDomainState> domains = new EnumMap<>(RendererDomain.class);
    
    private RendererDomainRegistry() {
        reset();
    }
    
    public static RendererDomainRegistry get() {
        return INSTANCE;
    }
    
    synchronized void reset() {
        this.domains.clear();
        for (RendererDomain domain : RendererDomain.values()) {
            this.domains.put(domain, RendererDomainState.vanilla("not configured"));
        }
    }
    
    synchronized void set(RendererDomain domain, RendererDomainState state) {
        this.domains.put(domain, state);
    }
    
    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (RendererDomain domain : RendererDomain.values()) {
            RendererDomainState state = this.domains.getOrDefault(domain, RendererDomainState.vanilla("not configured"));
            Map<String, Object> diagnostics = new LinkedHashMap<>(state.asMap());
            diagnostics.put("contract", RendererDomainContract.defaultFor(domain).asMap());
            map.put(domain.diagnosticName(), diagnostics);
        }
        return map;
    }
}