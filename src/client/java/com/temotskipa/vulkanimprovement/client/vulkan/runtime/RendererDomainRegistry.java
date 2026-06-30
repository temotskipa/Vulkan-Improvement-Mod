package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RendererDomainRegistry {
    private static final RendererDomainRegistry INSTANCE = new RendererDomainRegistry();
    private final EnumMap<RendererDomain, DomainState> domains = new EnumMap<>(RendererDomain.class);
    
    private RendererDomainRegistry() {
        reset();
    }
    
    public static RendererDomainRegistry get() {
        return INSTANCE;
    }
    
    synchronized void reset() {
        this.domains.clear();
        for (RendererDomain domain : RendererDomain.values()) {
            this.domains.put(domain, DomainState.vanilla("not configured"));
        }
    }
    
    synchronized void set(RendererDomain domain, DomainState state) {
        this.domains.put(domain, state);
    }
    
    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (RendererDomain domain : RendererDomain.values()) {
            DomainState state = this.domains.getOrDefault(domain, DomainState.vanilla("not configured"));
            Map<String, Object> diagnostics = new LinkedHashMap<>(state.asMap());
            diagnostics.put("contract", RendererDomainContract.defaultFor(domain).asMap());
            map.put(domain.diagnosticName(), diagnostics);
        }
        return map;
    }
    
    record DomainState(String path, String reason, boolean cpuDrawLists, boolean gpuWorkQueues,
                       boolean gpuMeshGeneration, boolean gpuVisibility) {
        static DomainState vanilla(String reason) {
            return new DomainState("vanilla", reason, true, false, false, false);
        }
        
        @SuppressWarnings("unused")
        static DomainState meshTerrain(String reason) {
            return new DomainState("mesh-shader-terrain", reason, true, false, false, true);
        }
        
        static DomainState meshTerrainGpu(String reason) {
            return new DomainState("mesh-shader-terrain", reason, true, true, false, true);
        }
        
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("path", this.path);
            map.put("reason", this.reason);
            map.put("cpuDrawLists", this.cpuDrawLists);
            map.put("gpuWorkQueues", this.gpuWorkQueues);
            map.put("gpuMeshGeneration", this.gpuMeshGeneration);
            map.put("gpuVisibility", this.gpuVisibility);
            return map;
        }
    }
}