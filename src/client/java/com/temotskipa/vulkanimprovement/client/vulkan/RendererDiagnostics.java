package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RendererDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static Map<String, Object> bugReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("modVersion", FabricLoader.getInstance().getModContainer("vulkanimprovement")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"));
        report.put("minecraftVersion", FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"));
        report.put("fabricLoaderVersion", FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"));
        report.put("debugConfig", TerrainRendererDebugConfig.describe());
        report.put("capabilities", VulkanImprovementCapabilities.asMap());
        report.put("terrainRenderer", MeshTerrainRenderer.get().asMap());
        report.put("preparedDispatchQueues", TerrainRenderContext.preparedDispatchQueueDepths());
        return report;
    }

    public static void logBugReport() {
        LOGGER.info("[Vulkan Improvement] bug-report {}", RendererJson.toJson(bugReport()));
    }
}