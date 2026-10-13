package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AcceleratedWorldgenProviderCompatibility {
    private static final String PROVIDER_IDS_PROPERTY = "vim.acceleratedWorldgenProviderIds";
    private static final String RENDERER_AUTHORITY = "cpu-chunk-state";
    private static final String WORLDGEN_AUTHORITY = "minecraft";
    private static final String METADATA_RELATIONSHIP = "none";
    
    private AcceleratedWorldgenProviderCompatibility() {
    }
    
    public static Map<String, Object> snapshot() {
        String[] providerIds = configuredProviderIds();
        return forDiagnostics(providerPresent(providerIds), TerrainRendererDebugConfig.acceleratedWorldgenProviderIntegrationEnabled(), providerIds.length);
    }
    
    static Map<String, Object> forDiagnostics(boolean providerPresent, boolean integrationEnabled, int configuredProviderCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("present", providerPresent);
        map.put("integrationEnabled", integrationEnabled);
        map.put("rendererAuthority", RENDERER_AUTHORITY);
        map.put("worldgenAuthority", WORLDGEN_AUTHORITY);
        map.put("fallbackReason", fallbackReason(providerPresent, integrationEnabled, configuredProviderCount));
        map.put("metadataRelationship", METADATA_RELATIONSHIP);
        map.put("configuredProviderCount", configuredProviderCount);
        return map;
    }
    
    private static boolean providerPresent(String[] providerIds) {
        for (String providerId : providerIds) {
            if (FabricLoader.getInstance().getModContainer(providerId).isPresent()) {
                return true;
            }
        }
        return false;
    }
    
    private static String[] configuredProviderIds() {
        String configuredIds = System.getProperty(PROVIDER_IDS_PROPERTY, "");
        if (configuredIds.isBlank()) {
            return new String[0];
        }
        return configuredIds.lines().flatMap(line -> java.util.Arrays.stream(line.split(","))).map(id -> id.trim().toLowerCase(Locale.ROOT)).filter(id -> !id.isBlank()).distinct().toArray(String[]::new);
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