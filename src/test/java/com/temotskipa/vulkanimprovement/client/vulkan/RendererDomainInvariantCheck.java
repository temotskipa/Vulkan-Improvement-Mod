package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.Map;

public final class RendererDomainInvariantCheck {
    private RendererDomainInvariantCheck() {
    }

    public static void main(String[] args) {
        checkResetDefaultsAllDomainsToVanilla();
        checkTerrainMeshPathKeepsOtherDomainsVanilla();
    }

    private static void checkResetDefaultsAllDomainsToVanilla() {
        RendererDomainRegistry registry = RendererDomainRegistry.get();
        registry.reset();
        Map<String, Object> domains = registry.asMap();

        require(domains.size() == RendererDomain.values().length, "domain diagnostics must include every renderer domain");
        for (RendererDomain domain : RendererDomain.values()) {
            Map<?, ?> state = state(domains, domain);
            require("vanilla".equals(state.get("path")), domain.diagnosticName() + " must reset to vanilla");
            require(Boolean.TRUE.equals(state.get("cpuDrawLists")), domain.diagnosticName() + " vanilla path must use CPU draw lists");
            require(Boolean.FALSE.equals(state.get("gpuWorkQueues")), domain.diagnosticName() + " vanilla path must not claim GPU work queues");
            require(Boolean.FALSE.equals(state.get("gpuMeshGeneration")), domain.diagnosticName() + " vanilla path must not claim GPU mesh generation");
            require(Boolean.FALSE.equals(state.get("gpuVisibility")), domain.diagnosticName() + " vanilla path must not claim GPU visibility");
        }
    }

    private static void checkTerrainMeshPathKeepsOtherDomainsVanilla() {
        RendererDomainRegistry registry = RendererDomainRegistry.get();
        registry.reset();
        registry.set(RendererDomain.TERRAIN, RendererDomainRegistry.DomainState.meshTerrainGpu("test terrain path"));

        Map<String, Object> domains = registry.asMap();
        Map<?, ?> terrain = state(domains, RendererDomain.TERRAIN);
        require("mesh-shader-terrain".equals(terrain.get("path")), "terrain mesh path must be reported explicitly");
        require("test terrain path".equals(terrain.get("reason")), "terrain path reason must survive diagnostics");
        require(Boolean.TRUE.equals(terrain.get("cpuDrawLists")), "current terrain path still consumes CPU-built draw lists");
        require(Boolean.TRUE.equals(terrain.get("gpuWorkQueues")), "current terrain path must report GPU work queues");
        require(Boolean.FALSE.equals(terrain.get("gpuMeshGeneration")), "current terrain path must not claim GPU mesh generation yet");
        require(Boolean.TRUE.equals(terrain.get("gpuVisibility")), "current terrain path must report mesh-shader visibility work");

        for (RendererDomain domain : RendererDomain.values()) {
            if (domain == RendererDomain.TERRAIN) {
                continue;
            }
            require("vanilla".equals(state(domains, domain).get("path")), domain.diagnosticName() + " must remain vanilla when only terrain is configured");
        }

        registry.reset();
    }

    private static Map<?, ?> state(Map<String, Object> domains, RendererDomain domain) {
        Object value = domains.get(domain.diagnosticName());
        require(value instanceof Map<?, ?>, domain.diagnosticName() + " diagnostics must be a map");
        return (Map<?, ?>) value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
