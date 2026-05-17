package com.temotskipa.vulkanimprovement.client.vulkan;

import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

public final class VulkanImprovementVideoOptions {
    public static final Component HEADER = Component.translatable("options.vulkanimprovement.header");
    
    private VulkanImprovementVideoOptions() {
    }
    
    public static OptionInstance<?>[] createOptions() {
        return new OptionInstance[]{OptionInstance.createBoolean("options.vulkanimprovement.meshTerrainRenderer", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshTerrainRenderer.tooltip")), TerrainRendererDebugConfig.replaceVanillaTerrain(), TerrainRendererDebugConfig::setReplaceVanillaTerrain), OptionInstance.createBoolean("options.vulkanimprovement.meshTranslucentTerrain", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshTranslucentTerrain.tooltip")), TerrainRendererDebugConfig.meshTranslucentTerrainEnabled(), TerrainRendererDebugConfig::setMeshTranslucentTerrainEnabled), OptionInstance.createBoolean("options.vulkanimprovement.meshletFrustumCulling", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshletFrustumCulling.tooltip")), TerrainRendererDebugConfig.meshletFrustumCullingEnabled(), TerrainRendererDebugConfig::setMeshletFrustumCullingEnabled), OptionInstance.createBoolean("options.vulkanimprovement.drawAllCapturedTerrainLayers", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.drawAllCapturedTerrainLayers.tooltip")), TerrainRendererDebugConfig.drawAllCapturedTerrainLayers(), TerrainRendererDebugConfig::setDrawAllCapturedTerrainLayers), OptionInstance.createBoolean("options.vulkanimprovement.vanillaChunkVisibilityFade", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.vanillaChunkVisibilityFade.tooltip")), TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled(), TerrainRendererDebugConfig::setVanillaChunkVisibilityFadeEnabled), OptionInstance.createBoolean("options.vulkanimprovement.terrainFragmentShadingRate", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.terrainFragmentShadingRate.tooltip")), TerrainRendererDebugConfig.fragmentShadingRateEnabled(), TerrainRendererDebugConfig::setFragmentShadingRateEnabled)};
    }
}