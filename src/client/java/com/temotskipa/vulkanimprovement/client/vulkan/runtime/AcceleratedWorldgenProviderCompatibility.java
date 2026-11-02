package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Arrays;
import java.util.Locale;

public final class AcceleratedWorldgenProviderCompatibility {
	private static final String PROVIDER_IDS_PROPERTY = "vim.acceleratedWorldgenProviderIds";
	private static final String RENDERER_AUTHORITY = "cpu-chunk-state";
	private static final String WORLDGEN_AUTHORITY = "minecraft";
	private static final String METADATA_RELATIONSHIP = "none";
	
	private AcceleratedWorldgenProviderCompatibility() {
	}
	
	public static AcceleratedWorldgenProviderState snapshot() {
		String[] providerIds = configuredProviderIds();
		return forDiagnostics(providerPresent(providerIds), TerrainRendererDebugConfig.acceleratedWorldgenProviderIntegrationEnabled(), providerIds.length);
	}
	
	/**
	 * Whether a provider the config names is installed.
	 */
	static boolean providerPresent() {
		return providerPresent(configuredProviderIds());
	}
	
	private static String[] configuredProviderIds() {
		String configuredIds = System.getProperty(PROVIDER_IDS_PROPERTY, "");
		if (configuredIds.isBlank()) {
			return new String[0];
		}
		return configuredIds.lines().flatMap(line -> Arrays.stream(line.split(","))).map(id -> id.trim().toLowerCase(Locale.ROOT)).filter(id -> !id.isBlank()).distinct().toArray(String[]::new);
	}
	
	static AcceleratedWorldgenProviderState forDiagnostics(boolean providerPresent, boolean integrationEnabled, int configuredProviderCount) {
		return new AcceleratedWorldgenProviderState(providerPresent, integrationEnabled, RENDERER_AUTHORITY, WORLDGEN_AUTHORITY, fallbackReason(providerPresent, integrationEnabled, configuredProviderCount), METADATA_RELATIONSHIP, configuredProviderCount);
	}
	
	private static boolean providerPresent(String[] providerIds) {
		for (String providerId : providerIds) {
			if (FabricLoader.getInstance().getModContainer(providerId).isPresent()) {
				return true;
			}
		}
		return false;
	}
	
	private static String fallbackReason(boolean providerPresent, boolean integrationEnabled, int configuredProviderCount) {
		if (configuredProviderCount <= 0) {
			return "no-provider-id-configured";
		}
		if (!providerPresent) {
			return "provider-not-detected";
		}
		if (!integrationEnabled) {
			return "integration-disabled";
		}
		return "provider-owned-worldgen-not-implemented";
	}
}