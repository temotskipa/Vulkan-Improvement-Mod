package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RendererDomainInvariantCheck {
	private static final Path DOMAIN_OWNERS = Path.of("docs/references/minecraft-26-4-renderer-domain-owners.md");
	private static final Path RENDERER_DIAGNOSTICS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/RendererDiagnostics.java");
	private static final Map<RendererDomain, String> NON_TERRAIN_FALLBACK_REASONS = Map.of(RendererDomain.ENTITIES, "vanilla entity renderer not contracted", RendererDomain.BLOCK_ENTITIES, "vanilla block entity renderer not contracted", RendererDomain.ITEMS, "vanilla item renderer not contracted", RendererDomain.PARTICLES, "vanilla particle engine not contracted", RendererDomain.SKY, "vanilla sky renderer not contracted", RendererDomain.CLOUDS, "vanilla cloud renderer not contracted", RendererDomain.WEATHER, "vanilla weather renderer not contracted", RendererDomain.TRANSLUCENT_EFFECTS, "vanilla translucent effect ordering not contracted", RendererDomain.POST_PROCESSING, "vanilla post-processing chain not contracted");
	
	private RendererDomainInvariantCheck() {
	}
	
	
	static void main() {
		checkPlanRequiredDomainNames();
		checkEveryDomainExposesContractDiagnostics();
		checkPostProcessingContractUsesMinecraft26Owner();
		checkContractOwnersAreMcdevConfirmed();
		checkBugReportExposesDomainStateAndContract();
		checkNonTerrainDomainsRequireContractsBeforeReplacement();
		checkResetDefaultsAllDomainsToVanilla();
		checkTerrainMeshPathKeepsOtherDomainsVanilla();
		checkTerrainDomainFollowsTheRendererMode();
	}
	
	/**
	 * Diagnostics distinguish the path that draws from the path that is merely configured.
	 */
	private static void checkTerrainDomainFollowsTheRendererMode() {
		boolean replace = TerrainRendererDebugConfig.replaceVanillaTerrain();
		boolean strict = TerrainRendererDebugConfig.strictMeshTerrainReplacement();
		try {
			RendererDomainRegistry registry = RendererDomainRegistry.get();
			registry.reset();
			TerrainRendererDebugConfig.setReplaceVanillaTerrain(false);
			RendererDomainObserver.observeTerrainMode();
			require("vanilla".equals(registry.state(RendererDomain.TERRAIN).path()), "terrain must be reported as vanilla while vanilla terrain is all that draws");
			
			TerrainRendererDebugConfig.setReplaceVanillaTerrain(true);
			TerrainRendererDebugConfig.setStrictMeshTerrainReplacement(false);
			RendererDomainObserver.observeTerrainMode();
			RendererDomainState overlay = registry.state(RendererDomain.TERRAIN);
			require("mesh-shader-terrain".equals(overlay.path()), "terrain must be reported as mesh terrain while the mesh path draws");
			require(overlay.reason().contains("stays visible"), "the overlay mode must say that vanilla terrain is still drawn");
			
			TerrainRendererDebugConfig.setStrictMeshTerrainReplacement(true);
			RendererDomainObserver.observeTerrainMode();
			require(registry.state(RendererDomain.TERRAIN).reason().contains("replaces"), "the strict mode must say that the mesh path replaces vanilla terrain");
			registry.reset();
		} finally {
			TerrainRendererDebugConfig.setReplaceVanillaTerrain(replace);
			TerrainRendererDebugConfig.setStrictMeshTerrainReplacement(strict);
		}
	}
	
	private static void checkPlanRequiredDomainNames() {
		Set<String> domains = Arrays.stream(RendererDomain.values()).map(RendererDomain::diagnosticName).collect(Collectors.toSet());
		
		require(domains.contains("terrain"), "domain diagnostics must include terrain");
		require(domains.contains("entities"), "domain diagnostics must include entities");
		require(domains.contains("blockEntities"), "domain diagnostics must include block entities");
		require(domains.contains("items"), "domain diagnostics must include items");
		require(domains.contains("particles"), "domain diagnostics must include particles");
		require(domains.contains("sky"), "domain diagnostics must include sky");
		require(domains.contains("clouds"), "domain diagnostics must include clouds");
		require(domains.contains("weather"), "domain diagnostics must include weather");
		require(domains.contains("postProcessing"), "domain diagnostics must include post-processing");
	}
	
	private static void checkEveryDomainExposesContractDiagnostics() {
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		registry.reset();
		
		for (RendererDomain domain : RendererDomain.values()) {
			RendererDomainContract contract = RendererDomainContract.defaultFor(domain);
			require(domain == contract.domain(), domain.diagnosticName() + " contract must identify its domain");
			require(!contract.vanillaOwner().isBlank(), domain.diagnosticName() + " contract must include a vanilla owner");
			require(!contract.assetContract().isBlank(), domain.diagnosticName() + " contract must include an asset contract");
			require(!contract.orderingContract().isBlank(), domain.diagnosticName() + " contract must include an ordering contract");
			require(!contract.materialContract().isBlank(), domain.diagnosticName() + " contract must include a material contract");
			require(!contract.fallbackReason().isBlank(), domain.diagnosticName() + " contract must include a fallback reason");
		}
	}
	
	private static void checkPostProcessingContractUsesMinecraft26Owner() {
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		registry.reset();
		RendererDomainContract contract = RendererDomainContract.defaultFor(RendererDomain.POST_PROCESSING);
		
		require("net.minecraft.client.renderer.PostPass".equals(contract.vanillaOwner()), "post-processing contract must use the mcdev-confirmed PostPass owner");
	}
	
	/**
	 * An owner is a Mojang class name, and class names move between versions. Every one has to be
	 * in the record of what mcdev showed for the version the mod targets.
	 */
	private static void checkContractOwnersAreMcdevConfirmed() {
		String owners = read(DOMAIN_OWNERS);
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		registry.reset();
		for (RendererDomain domain : RendererDomain.values()) {
			String name = RendererDomainContract.defaultFor(domain).vanillaOwner();
			if (name.startsWith("net.minecraft.")) {
				require(recordsOwner(owners, domain.diagnosticName(), name), domain.diagnosticName() + " owner must be recorded as mcdev-confirmed in " + DOMAIN_OWNERS + ": " + name);
			}
		}
		require("net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer".equals(RendererDomainContract.defaultFor(RendererDomain.ITEMS).vanillaOwner()), "the item contract must name the class that replaced ItemInHandRenderer");
	}
	
	/**
	 * Whether a row of the owners table gives the domain that owner, however its cells are padded.
	 */
	private static boolean recordsOwner(String owners, String domain, String owner) {
		return Pattern.compile("^\\|\\s*" + Pattern.quote(domain) + "\\s*\\|\\s*" + Pattern.quote("`" + owner + "`") + "\\s*\\|", Pattern.MULTILINE).matcher(owners).find();
	}
	
	/**
	 * A bug report carries the path each domain draws with, and why.
	 */
	private static void checkBugReportExposesDomainStateAndContract() {
		require(read(RENDERER_DIAGNOSTICS).contains("\"renderer: \" + RendererCoreServices.get().describe()"), "bug reports must include renderer services");
		require(read(RENDERER_DIAGNOSTICS).contains("\"domains: \" + RendererDomainRegistry.get().describe()"), "bug reports must include the domain registry");
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		registry.reset();
		for (RendererDomain domain : RendererDomain.values()) {
			require(registry.describe().contains(domain.diagnosticName() + "=" + registry.state(domain).path()), "every domain must report the path it draws with");
		}
	}
	
	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException ex) {
			throw new AssertionError("failed to read " + path, ex);
		}
	}
	
	private static void checkNonTerrainDomainsRequireContractsBeforeReplacement() {
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		registry.reset();
		
		for (Entry<RendererDomain, String> entry : NON_TERRAIN_FALLBACK_REASONS.entrySet()) {
			RendererDomainContract contract = RendererDomainContract.defaultFor(entry.getKey());
			require(!contract.replacementAllowed(), entry.getKey().diagnosticName() + " replacement must remain disabled until contracted");
			require(entry.getValue().equals(contract.fallbackReason()), entry.getKey().diagnosticName() + " fallback reason must be concrete");
		}
	}
	
	private static void checkResetDefaultsAllDomainsToVanilla() {
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		registry.reset();
		
		for (RendererDomain domain : RendererDomain.values()) {
			RendererDomainState state = registry.state(domain);
			require("vanilla".equals(state.path()), domain.diagnosticName() + " must reset to vanilla");
			require(state.cpuDrawLists(), domain.diagnosticName() + " vanilla path must use CPU draw lists");
			require(!state.gpuWorkQueues(), domain.diagnosticName() + " vanilla path must not claim GPU work queues");
			require(!state.gpuMeshGeneration(), domain.diagnosticName() + " vanilla path must not claim GPU mesh generation");
			require(!state.gpuVisibility(), domain.diagnosticName() + " vanilla path must not claim GPU visibility");
		}
	}
	
	private static void checkTerrainMeshPathKeepsOtherDomainsVanilla() {
		RendererDomainRegistry registry = RendererDomainRegistry.get();
		registry.reset();
		registry.set(RendererDomain.TERRAIN, RendererDomainState.meshTerrainGpu("test terrain path"));
		
		RendererDomainState terrain = registry.state(RendererDomain.TERRAIN);
		require("mesh-shader-terrain".equals(terrain.path()), "terrain mesh path must be reported explicitly");
		require("test terrain path".equals(terrain.reason()), "terrain path reason must survive diagnostics");
		require(terrain.cpuDrawLists(), "current terrain path still consumes CPU-built draw lists");
		require(terrain.gpuWorkQueues(), "current terrain path must report GPU work queues");
		require(!terrain.gpuMeshGeneration(), "current terrain path must not claim GPU mesh generation yet");
		require(terrain.gpuVisibility(), "current terrain path must report mesh-shader visibility work");
		
		for (RendererDomain domain : RendererDomain.values()) {
			if (domain == RendererDomain.TERRAIN) {
				continue;
			}
			require("vanilla".equals(registry.state(domain).path()), domain.diagnosticName() + " must remain vanilla when only terrain is configured");
		}
		
		registry.reset();
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}