package com.temotskipa.vulkanimprovement.client.vulkan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class VulkanImprovementVideoOptions {
    public static final Component HEADER = Component.translatable("options.vulkanimprovement.header");

    private static @Nullable OptionInstance<Boolean> meshTerrainRenderer;
    private static @Nullable OptionInstance<Boolean> meshTranslucentTerrain;
    private static @Nullable OptionInstance<Boolean> meshletFrustumCulling;
    private static @Nullable OptionInstance<Boolean> drawAllCapturedTerrainLayers;
    private static @Nullable OptionInstance<Boolean> vanillaChunkVisibilityFade;
    private static @Nullable OptionInstance<Boolean> terrainFragmentShadingRate;

    private VulkanImprovementVideoOptions() {
    }

    public static OptionInstance<?>[] createOptions() {
        if (!VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())) {
            return new OptionInstance[0];
        }
        ensureOptions();
        syncFromConfig();
        return new OptionInstance[]{
                meshTerrainRenderer,
                meshTranslucentTerrain,
                meshletFrustumCulling,
                drawAllCapturedTerrainLayers,
                vanillaChunkVisibilityFade,
                terrainFragmentShadingRate
        };
    }

    public static void updateWidgetState(@Nullable OptionsList optionsList) {
        if (optionsList == null || !VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())) {
            return;
        }
        ensureOptions();
        boolean meshTerrain = TerrainRendererDebugConfig.replaceVanillaTerrain();
        boolean fragmentShadingRateSupported = FragmentShadingRateController.get().fragmentShadingRateAvailable();
        setWidgetActive(optionsList, meshTerrainRenderer, true);
        setWidgetActive(optionsList, meshTranslucentTerrain, meshTerrain);
        setWidgetActive(optionsList, meshletFrustumCulling, meshTerrain);
        setWidgetActive(optionsList, drawAllCapturedTerrainLayers, meshTerrain);
        setWidgetActive(optionsList, vanillaChunkVisibilityFade, meshTerrain);
        setWidgetActive(optionsList, terrainFragmentShadingRate, meshTerrain && fragmentShadingRateSupported);
    }

    private static void ensureOptions() {
        if (meshTerrainRenderer != null) {
            return;
        }
        meshTerrainRenderer = OptionInstance.createBoolean(
                "options.vulkanimprovement.meshTerrainRenderer",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshTerrainRenderer.tooltip")),
                TerrainRendererDebugConfig.replaceVanillaTerrain(),
                VulkanImprovementVideoOptions::onMeshTerrainChanged
        );
        meshTranslucentTerrain = OptionInstance.createBoolean(
                "options.vulkanimprovement.meshTranslucentTerrain",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshTranslucentTerrain.tooltip")),
                TerrainRendererDebugConfig.meshTranslucentTerrainEnabled(),
                TerrainRendererDebugConfig::setMeshTranslucentTerrainEnabled
        );
        meshletFrustumCulling = OptionInstance.createBoolean(
                "options.vulkanimprovement.meshletFrustumCulling",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshletFrustumCulling.tooltip")),
                TerrainRendererDebugConfig.meshletFrustumCullingEnabled(),
                TerrainRendererDebugConfig::setMeshletFrustumCullingEnabled
        );
        drawAllCapturedTerrainLayers = OptionInstance.createBoolean(
                "options.vulkanimprovement.drawAllCapturedTerrainLayers",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.drawAllCapturedTerrainLayers.tooltip")),
                TerrainRendererDebugConfig.drawAllCapturedTerrainLayers(),
                VulkanImprovementVideoOptions::onDrawAllCapturedTerrainLayersChanged
        );
        vanillaChunkVisibilityFade = OptionInstance.createBoolean(
                "options.vulkanimprovement.vanillaChunkVisibilityFade",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.vanillaChunkVisibilityFade.tooltip")),
                TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled(),
                TerrainRendererDebugConfig::setVanillaChunkVisibilityFadeEnabled
        );
        terrainFragmentShadingRate = OptionInstance.createBoolean(
                "options.vulkanimprovement.terrainFragmentShadingRate",
                OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.terrainFragmentShadingRate.tooltip")),
                TerrainRendererDebugConfig.fragmentShadingRateEnabled(),
                TerrainRendererDebugConfig::setFragmentShadingRateEnabled
        );
    }

    private static void syncFromConfig() {
        meshTerrainRenderer.set(TerrainRendererDebugConfig.replaceVanillaTerrain());
        meshTranslucentTerrain.set(TerrainRendererDebugConfig.meshTranslucentTerrainEnabled());
        meshletFrustumCulling.set(TerrainRendererDebugConfig.meshletFrustumCullingEnabled());
        drawAllCapturedTerrainLayers.set(TerrainRendererDebugConfig.drawAllCapturedTerrainLayers());
        vanillaChunkVisibilityFade.set(TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled());
        terrainFragmentShadingRate.set(TerrainRendererDebugConfig.fragmentShadingRateEnabled());
    }

    private static void onMeshTerrainChanged(boolean enabled) {
        TerrainRendererDebugConfig.setReplaceVanillaTerrain(enabled);
        if (!enabled) {
            TerrainRendererDebugConfig.setDrawAllCapturedTerrainLayers(false);
            if (drawAllCapturedTerrainLayers != null) {
                drawAllCapturedTerrainLayers.set(false);
            }
        }
    }

    private static void onDrawAllCapturedTerrainLayersChanged(boolean enabled) {
        if (enabled && !TerrainRendererDebugConfig.replaceVanillaTerrain()) {
            return;
        }
        TerrainRendererDebugConfig.setDrawAllCapturedTerrainLayers(enabled);
    }

    private static void setWidgetActive(OptionsList optionsList, OptionInstance<Boolean> option, boolean active) {
        AbstractWidget widget = optionsList.findOption(option);
        if (widget != null) {
            widget.active = active;
        }
    }
}