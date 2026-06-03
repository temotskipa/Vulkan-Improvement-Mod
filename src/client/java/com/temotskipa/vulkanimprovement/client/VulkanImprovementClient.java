package com.temotskipa.vulkanimprovement.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.temotskipa.vulkanimprovement.client.vulkan.RendererDiagnostics;
import com.temotskipa.vulkanimprovement.client.vulkan.TerrainRendererDebugConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public class VulkanImprovementClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static KeyMapping dumpDiagnosticsKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Vulkan Improvement] Client initialized: {}", TerrainRendererDebugConfig.describe());
        dumpDiagnosticsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vulkanimprovement.dump_diagnostics",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyMapping.Category.DEBUG
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (dumpDiagnosticsKey.consumeClick()) {
                RendererDiagnostics.logBugReport();
            }
        });
    }
}