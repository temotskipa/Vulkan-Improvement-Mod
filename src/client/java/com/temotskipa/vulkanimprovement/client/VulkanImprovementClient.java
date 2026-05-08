package com.temotskipa.vulkanimprovement.client;

import com.mojang.logging.LogUtils;
import com.temotskipa.vulkanimprovement.client.vulkan.TerrainRendererDebugConfig;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public class VulkanImprovementClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("[Vulkan Improvement] Client initialized: {}", TerrainRendererDebugConfig.describe());
    }
}
