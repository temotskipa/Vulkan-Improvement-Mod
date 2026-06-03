package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class RendererDomainRegistry {
    private static final RendererDomainRegistry INSTANCE = new RendererDomainRegistry();

    private final EnumMap<RendererDomain, DomainState> domains = new EnumMap<>(RendererDomain.class);

    private RendererDomainRegistry() {
        reset();
    }

    static RendererDomainRegistry get() {
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

    synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (RendererDomain domain : RendererDomain.values()) {
            DomainState state = this.domains.getOrDefault(domain, DomainState.vanilla("not configured"));
            map.put(domain.diagnosticName(), state.asMap());
        }
        return map;
    }

    record DomainState(String path, String reason, boolean cpuDrawLists, boolean gpuWorkQueues,
                       boolean gpuMeshGeneration, boolean gpuVisibility) {
        static DomainState vanilla(String reason) {
            return new DomainState("vanilla", reason, true, false, false, false);
        }

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
