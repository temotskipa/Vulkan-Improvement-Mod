package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Which other mods change the Minecraft classes a replacement stands in for, read from the mixin
 * configurations every mod registers. A replacement steps aside for a class another mod changes,
 * since it cannot know what the change does.
 */
final class OtherModMixins {
    static final String OWN_CONFIG = "vulkanimprovement.client.mixins.json";
    
    private OtherModMixins() {
    }
    
    /**
     * The mixin configurations, other than the mod's own, that target any of the given classes,
     * each with the mod it comes from.
     */
    static List<String> targeting() {
        List<String> configs = new ArrayList<>();
        for (Config config : Mixins.getConfigs()) {
            IMixinConfig mixinConfig = config.getConfig();
            if (OWN_CONFIG.equals(mixinConfig.getName())) {
                continue;
            }
            for (String target : mixinConfig.getTargets()) {
                if (ModelGpuVertices.REPLACED_CLASSES.contains(target.replace('/', '.'))) {
                    configs.add(mixinConfig.getName() + " (" + mixinConfig.getCleanSourceId() + ") targets " + target.replace('/', '.'));
                }
            }
        }
        return configs;
    }
}