package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.PreferredGraphicsApi;
import org.slf4j.Logger;

public final class VulkanGraphicsApiPreference {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFERRED_GRAPHICS_BACKEND_KEY = "preferredGraphicsBackend";

    private VulkanGraphicsApiPreference() {
    }

    public static void applyModDefaultUpgrade(Options options, File optionsFile) {
        OptionInstance<PreferredGraphicsApi> preference = options.preferredGraphicsBackend();
        if (preference.get() != PreferredGraphicsApi.DEFAULT) {
            return;
        }
        if (optionsFileDeclaresGraphicsBackend(optionsFile)) {
            return;
        }
        preference.set(PreferredGraphicsApi.VULKAN);
        LOGGER.info(
                "[Vulkan Improvement] First-run graphics API preference upgraded from implicit Default to Vulkan for mesh terrain rendering.");
    }

    private static boolean optionsFileDeclaresGraphicsBackend(File optionsFile) {
        if (!optionsFile.isFile()) {
            return false;
        }
        try (BufferedReader reader = Files.newBufferedReader(optionsFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                if (PREFERRED_GRAPHICS_BACKEND_KEY.equals(line.substring(0, separator))) {
                    return true;
                }
            }
        } catch (IOException exception) {
            LOGGER.warn(
                    "[Vulkan Improvement] Could not inspect {} in options file; leaving graphics API preference unchanged.",
                    PREFERRED_GRAPHICS_BACKEND_KEY,
                    exception);
            return true;
        }
        return false;
    }
}