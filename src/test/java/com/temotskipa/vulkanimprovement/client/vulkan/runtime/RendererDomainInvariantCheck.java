package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.util.Map;
import java.util.Set;

public final class RendererDomainInvariantCheck {
    private static final Map<RendererDomain, String> NON_TERRAIN_FALLBACK_REASONS = Map.of(
            RendererDomain.ENTITIES, "vanilla entity renderer not contracted",
            RendererDomain.BLOCK_ENTITIES, "vanilla block entity renderer not contracted",
            RendererDomain.ITEMS, "vanilla item renderer not contracted",
            RendererDomain.PARTICLES, "vanilla particle engine not contracted",
            RendererDomain.SKY, "vanilla sky renderer not contracted",
            RendererDomain.CLOUDS, "vanilla cloud renderer not contracted",
            RendererDomain.WEATHER, "vanilla weather renderer not contracted",
            RendererDomain.TRANSLUCENT_EFFECTS, "vanilla translucent effect ordering not contracted",
            RendererDomain.POST_PROCESSING, "vanilla post-processing chain not contracted"
    );

    private RendererDomainInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) {
        checkPlanRequiredDomainNames();
        checkEveryDomainExposesContractDiagnostics();
        checkPostProcessingContractUsesMinecraft26Owner();
        checkNonTerrainDomainsRequireContractsBeforeReplacement();
        checkResetDefaultsAllDomainsToVanilla();
        checkTerrainMeshPathKeepsOtherDomainsVanilla();
    }

    private static void checkPlanRequiredDomainNames() {
        RendererDomainRegistry registry = RendererDomainRegistry.get();
        registry.reset();
        Map<String, Object> domains = registry.asMap();

        require(domains.containsKey("terrain"), "domain diagnostics must include terrain");
        require(domains.containsKey("entities"), "domain diagnostics must include entities");
        require(domains.containsKey("blockEntities"), "domain diagnostics must include block entities");
        require(domains.containsKey("items"), "domain diagnostics must include items");
        require(domains.containsKey("particles"), "domain diagnostics must include particles");
        require(domains.containsKey("sky"), "domain diagnostics must include sky");
        require(domains.containsKey("clouds"), "domain diagnostics must include clouds");
        require(domains.containsKey("weather"), "domain diagnostics must include weather");
        require(domains.containsKey("postProcessing"), "domain diagnostics must include post-processing");
    }

    private static void checkEveryDomainExposesContractDiagnostics() {
        RendererDomainRegistry registry = RendererDomainRegistry.get();
        registry.reset();
        Map<String, Object> domains = registry.asMap();

        for (RendererDomain domain : RendererDomain.values()) {
            Map<?, ?> contract = contract(domains, domain);
            require(domain.diagnosticName().equals(contract.get("domain")), domain.diagnosticName() + " contract must identify its domain");
            require(contract.get("vanillaOwner") instanceof String owner && !owner.isBlank(), domain.diagnosticName() + " contract must include a vanilla owner");
            require(contract.get("assetContract") instanceof String asset && !asset.isBlank(), domain.diagnosticName() + " contract must include an asset contract");
            require(contract.get("orderingContract") instanceof String ordering && !ordering.isBlank(), domain.diagnosticName() + " contract must include an ordering contract");
            require(contract.get("materialContract") instanceof String material && !material.isBlank(), domain.diagnosticName() + " contract must include a material contract");
            require(contract.get("fallbackReason") instanceof String fallback && !fallback.isBlank(), domain.diagnosticName() + " contract must include a fallback reason");
            Set<String> expectedKeys = Set.of("domain", "vanillaOwner", "assetContract", "orderingContract", "materialContract", "fallbackReason", "replacementAllowed");
            require(expectedKeys.stream().allMatch(contract::containsKey),
                    domain.diagnosticName() + " contract must expose the complete diagnostic field set");
        }
    }

    private static void checkPostProcessingContractUsesMinecraft26Owner() {
        RendererDomainRegistry registry = RendererDomainRegistry.get();
        registry.reset();
        Map<String, Object> domains = registry.asMap();
        Map<?, ?> contract = contract(domains, RendererDomain.POST_PROCESSING);

        require("net.minecraft.client.renderer.PostPass".equals(contract.get("vanillaOwner")),
                "post-processing contract must use the mcdev-confirmed Minecraft 26.2 PostPass owner");
    }

    private static void checkNonTerrainDomainsRequireContractsBeforeReplacement() {
        RendererDomainRegistry registry = RendererDomainRegistry.get();
        registry.reset();
        Map<String, Object> domains = registry.asMap();

        for (Map.Entry<RendererDomain, String> entry : NON_TERRAIN_FALLBACK_REASONS.entrySet()) {
            Map<?, ?> contract = contract(domains, entry.getKey());
            require(Boolean.FALSE.equals(contract.get("replacementAllowed")), entry.getKey().diagnosticName() + " replacement must remain disabled until contracted");
            require(entry.getValue().equals(contract.get("fallbackReason")), entry.getKey().diagnosticName() + " fallback reason must be concrete");
        }
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

    private static Map<?, ?> contract(Map<String, Object> domains, RendererDomain domain) {
        Object value = state(domains, domain).get("contract");
        require(value instanceof Map<?, ?>, domain.diagnosticName() + " diagnostics must include a contract map");
        return (Map<?, ?>) value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
