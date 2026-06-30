package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.FragmentShadingRateController;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.IntConsumer;

public final class VulkanImprovementVideoOptions {
    public static final Component HEADER = Component.translatable("options.vulkanimprovement.header");
    private static @Nullable OptionInstance<Boolean> meshTerrainRenderer;
    private static @Nullable OptionInstance<Boolean> meshTranslucentTerrain;
    private static @Nullable OptionInstance<Boolean> meshletFrustumCulling;
    private static @Nullable OptionInstance<Boolean> strictMeshTerrainReplacement;
    private static @Nullable OptionInstance<Boolean> drawAllCapturedTerrainLayers;
    private static @Nullable OptionInstance<Boolean> vanillaChunkVisibilityFade;
    private static @Nullable OptionInstance<Boolean> terrainFragmentShadingRate;
    private static @Nullable OptionInstance<Boolean> dumpCapabilities;
    private static @Nullable OptionInstance<Boolean> presentPacing;
    private static @Nullable OptionInstance<Boolean> requireVulkanBackend;
    private static @Nullable OptionInstance<Boolean> validationDescriptorBufferOnly;
    private static @Nullable OptionInstance<Boolean> waitIdleBeforeTerrainUpload;
    private static @Nullable OptionInstance<Boolean> allowCpuVisibleMeshletFallback;
    private static @Nullable OptionInstance<Boolean> gpuGeneratedMeshTaskCommands;
    private static @Nullable OptionInstance<Integer> terrainFragmentShadingRateSize;
    private static @Nullable OptionInstance<Integer> initialSectionCapacity;
    private static @Nullable OptionInstance<Integer> initialMeshletCapacity;
    private static @Nullable OptionInstance<Integer> initialVertexPayloadMib;
    private static @Nullable OptionInstance<Integer> initialIndexPayloadMib;
    private static @Nullable OptionInstance<Integer> terrainMirrorStabilizationMillis;
    
    private VulkanImprovementVideoOptions() {
    }
    
    public static OptionInstance<?>[] createOptions() {
        if (!VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())) {
            return new OptionInstance[0];
        }
        ensureOptions();
        syncFromConfig();
        return new OptionInstance[]{meshTerrainRenderer, meshTranslucentTerrain, meshletFrustumCulling, strictMeshTerrainReplacement, drawAllCapturedTerrainLayers, vanillaChunkVisibilityFade, terrainFragmentShadingRate, terrainFragmentShadingRateSize, presentPacing, waitIdleBeforeTerrainUpload, allowCpuVisibleMeshletFallback, gpuGeneratedMeshTaskCommands, dumpCapabilities, requireVulkanBackend, validationDescriptorBufferOnly, initialSectionCapacity, initialMeshletCapacity, initialVertexPayloadMib, initialIndexPayloadMib, terrainMirrorStabilizationMillis};
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
        setWidgetActive(optionsList, strictMeshTerrainReplacement, meshTerrain);
        setWidgetActive(optionsList, drawAllCapturedTerrainLayers, meshTerrain);
        setWidgetActive(optionsList, vanillaChunkVisibilityFade, meshTerrain);
        setWidgetActive(optionsList, terrainFragmentShadingRate, meshTerrain && fragmentShadingRateSupported);
        setWidgetActive(optionsList, terrainFragmentShadingRateSize, meshTerrain && fragmentShadingRateSupported && TerrainRendererDebugConfig.fragmentShadingRateEnabled());
        setWidgetActive(optionsList, waitIdleBeforeTerrainUpload, meshTerrain);
        setWidgetActive(optionsList, allowCpuVisibleMeshletFallback, meshTerrain);
        setWidgetActive(optionsList, gpuGeneratedMeshTaskCommands, meshTerrain);
    }
    
    private static void ensureOptions() {
        if (meshTerrainRenderer != null) {
            return;
        }
        meshTerrainRenderer = OptionInstance.createBoolean("options.vulkanimprovement.meshTerrainRenderer", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshTerrainRenderer.tooltip")), TerrainRendererDebugConfig.replaceVanillaTerrain(), VulkanImprovementVideoOptions::onMeshTerrainChanged);
        meshTranslucentTerrain = OptionInstance.createBoolean("options.vulkanimprovement.meshTranslucentTerrain", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshTranslucentTerrain.tooltip")), TerrainRendererDebugConfig.meshTranslucentTerrainEnabled(), TerrainRendererDebugConfig::setMeshTranslucentTerrainEnabled);
        meshletFrustumCulling = OptionInstance.createBoolean("options.vulkanimprovement.meshletFrustumCulling", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.meshletFrustumCulling.tooltip")), TerrainRendererDebugConfig.meshletFrustumCullingEnabled(), TerrainRendererDebugConfig::setMeshletFrustumCullingEnabled);
        strictMeshTerrainReplacement = OptionInstance.createBoolean("options.vulkanimprovement.strictMeshTerrainReplacement", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.strictMeshTerrainReplacement.tooltip")), TerrainRendererDebugConfig.strictMeshTerrainReplacement(), TerrainRendererDebugConfig::setStrictMeshTerrainReplacement);
        drawAllCapturedTerrainLayers = OptionInstance.createBoolean("options.vulkanimprovement.drawAllCapturedTerrainLayers", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.drawAllCapturedTerrainLayers.tooltip")), TerrainRendererDebugConfig.drawAllCapturedTerrainLayers(), VulkanImprovementVideoOptions::onDrawAllCapturedTerrainLayersChanged);
        vanillaChunkVisibilityFade = OptionInstance.createBoolean("options.vulkanimprovement.vanillaChunkVisibilityFade", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.vanillaChunkVisibilityFade.tooltip")), TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled(), TerrainRendererDebugConfig::setVanillaChunkVisibilityFadeEnabled);
        terrainFragmentShadingRate = OptionInstance.createBoolean("options.vulkanimprovement.terrainFragmentShadingRate", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.terrainFragmentShadingRate.tooltip")), TerrainRendererDebugConfig.fragmentShadingRateEnabled(), TerrainRendererDebugConfig::setFragmentShadingRateEnabled);
        terrainFragmentShadingRateSize = intOption("options.vulkanimprovement.terrainFragmentShadingRateSize", 1, 4, TerrainRendererDebugConfig.terrainFragmentShadingRate(), TerrainRendererDebugConfig::setTerrainFragmentShadingRate);
        presentPacing = OptionInstance.createBoolean("options.vulkanimprovement.presentPacing", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.presentPacing.tooltip")), !TerrainRendererDebugConfig.disablePresentPacing(), enabled -> TerrainRendererDebugConfig.setDisablePresentPacing(!enabled));
        waitIdleBeforeTerrainUpload = OptionInstance.createBoolean("options.vulkanimprovement.waitIdleBeforeTerrainUpload", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.waitIdleBeforeTerrainUpload.tooltip")), TerrainRendererDebugConfig.waitIdleBeforeTerrainUpload(), TerrainRendererDebugConfig::setWaitIdleBeforeTerrainUpload);
        allowCpuVisibleMeshletFallback = OptionInstance.createBoolean("options.vulkanimprovement.allowCpuVisibleMeshletFallback", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.allowCpuVisibleMeshletFallback.tooltip")), TerrainRendererDebugConfig.allowCpuVisibleMeshletFallback(), TerrainRendererDebugConfig::setAllowCpuVisibleMeshletFallback);
        gpuGeneratedMeshTaskCommands = OptionInstance.createBoolean("options.vulkanimprovement.gpuGeneratedMeshTaskCommands", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.gpuGeneratedMeshTaskCommands.tooltip")), TerrainRendererDebugConfig.enableGpuGeneratedMeshTaskCommands(), TerrainRendererDebugConfig::setEnableGpuGeneratedMeshTaskCommands);
        dumpCapabilities = OptionInstance.createBoolean("options.vulkanimprovement.dumpCapabilities", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.dumpCapabilities.tooltip")), TerrainRendererDebugConfig.dumpCapabilities(), TerrainRendererDebugConfig::setDumpCapabilities);
        requireVulkanBackend = OptionInstance.createBoolean("options.vulkanimprovement.requireVulkanBackend", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.requireVulkanBackend.tooltip")), TerrainRendererDebugConfig.requireVulkanBackend(), TerrainRendererDebugConfig::setRequireVulkanBackend);
        validationDescriptorBufferOnly = OptionInstance.createBoolean("options.vulkanimprovement.validationDescriptorBufferOnly", OptionInstance.cachedConstantTooltip(Component.translatable("options.vulkanimprovement.validationDescriptorBufferOnly.tooltip")), TerrainRendererDebugConfig.validationDescriptorBufferOnly(), TerrainRendererDebugConfig::setValidationDescriptorBufferOnly);
        initialSectionCapacity = intOption("options.vulkanimprovement.initialSectionCapacity", TerrainRendererDebugConfig.MIN_SECTION_CAPACITY, TerrainRendererDebugConfig.MAX_SECTION_CAPACITY, TerrainRendererDebugConfig.initialSectionCapacity(), TerrainRendererDebugConfig::setInitialSectionCapacity);
        initialMeshletCapacity = intOption("options.vulkanimprovement.initialMeshletCapacity", TerrainRendererDebugConfig.MIN_MESHLET_CAPACITY, TerrainRendererDebugConfig.MAX_MESHLET_CAPACITY, TerrainRendererDebugConfig.initialMeshletCapacity(), TerrainRendererDebugConfig::setInitialMeshletCapacity);
        initialVertexPayloadMib = mibOption("options.vulkanimprovement.initialVertexPayloadMib", TerrainRendererDebugConfig.MIN_VERTEX_PAYLOAD_MIB, TerrainRendererDebugConfig.MAX_VERTEX_PAYLOAD_MIB, TerrainRendererDebugConfig.initialVertexPayloadMib(), TerrainRendererDebugConfig::setInitialVertexPayloadMib);
        initialIndexPayloadMib = mibOption("options.vulkanimprovement.initialIndexPayloadMib", TerrainRendererDebugConfig.MIN_INDEX_PAYLOAD_MIB, TerrainRendererDebugConfig.MAX_INDEX_PAYLOAD_MIB, TerrainRendererDebugConfig.initialIndexPayloadMib(), TerrainRendererDebugConfig::setInitialIndexPayloadMib);
        terrainMirrorStabilizationMillis = intOption("options.vulkanimprovement.terrainMirrorStabilizationMillis", TerrainRendererDebugConfig.MIN_TERRAIN_MIRROR_STABILIZATION_MILLIS, TerrainRendererDebugConfig.MAX_TERRAIN_MIRROR_STABILIZATION_MILLIS, TerrainRendererDebugConfig.terrainMirrorStabilizationMillis(), TerrainRendererDebugConfig::setTerrainMirrorStabilizationMillis);
    }
    
    private static void syncFromConfig() {
        requireOption(meshTerrainRenderer).set(TerrainRendererDebugConfig.replaceVanillaTerrain());
        requireOption(meshTranslucentTerrain).set(TerrainRendererDebugConfig.meshTranslucentTerrainEnabled());
        requireOption(meshletFrustumCulling).set(TerrainRendererDebugConfig.meshletFrustumCullingEnabled());
        requireOption(strictMeshTerrainReplacement).set(TerrainRendererDebugConfig.strictMeshTerrainReplacement());
        requireOption(drawAllCapturedTerrainLayers).set(TerrainRendererDebugConfig.drawAllCapturedTerrainLayers());
        requireOption(vanillaChunkVisibilityFade).set(TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled());
        requireOption(terrainFragmentShadingRate).set(TerrainRendererDebugConfig.fragmentShadingRateEnabled());
        requireOption(terrainFragmentShadingRateSize).set(TerrainRendererDebugConfig.terrainFragmentShadingRate());
        requireOption(presentPacing).set(!TerrainRendererDebugConfig.disablePresentPacing());
        requireOption(waitIdleBeforeTerrainUpload).set(TerrainRendererDebugConfig.waitIdleBeforeTerrainUpload());
        requireOption(allowCpuVisibleMeshletFallback).set(TerrainRendererDebugConfig.allowCpuVisibleMeshletFallback());
        requireOption(gpuGeneratedMeshTaskCommands).set(TerrainRendererDebugConfig.enableGpuGeneratedMeshTaskCommands());
        requireOption(dumpCapabilities).set(TerrainRendererDebugConfig.dumpCapabilities());
        requireOption(requireVulkanBackend).set(TerrainRendererDebugConfig.requireVulkanBackend());
        requireOption(validationDescriptorBufferOnly).set(TerrainRendererDebugConfig.validationDescriptorBufferOnly());
        requireOption(initialSectionCapacity).set(TerrainRendererDebugConfig.initialSectionCapacity());
        requireOption(initialMeshletCapacity).set(TerrainRendererDebugConfig.initialMeshletCapacity());
        requireOption(initialVertexPayloadMib).set(TerrainRendererDebugConfig.initialVertexPayloadMib());
        requireOption(initialIndexPayloadMib).set(TerrainRendererDebugConfig.initialIndexPayloadMib());
        requireOption(terrainMirrorStabilizationMillis).set(TerrainRendererDebugConfig.terrainMirrorStabilizationMillis());
    }
    
    private static <T> OptionInstance<T> requireOption(@Nullable OptionInstance<T> option) {
        return Objects.requireNonNull(option, "Vulkan Improvement video options must be initialized before use");
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
    
    private static OptionInstance<Integer> intOption(String captionId, int minInclusive, int maxInclusive, int initialValue, IntConsumer onValueUpdate) {
        return new OptionInstance<>(captionId, OptionInstance.cachedConstantTooltip(Component.translatable(captionId + ".tooltip")), Options::genericValueLabel, new OptionInstance.IntRange(minInclusive, maxInclusive), initialValue, onValueUpdate::accept);
    }
    
    private static OptionInstance<Integer> mibOption(String captionId, int minInclusive, int maxInclusive, int initialValue, IntConsumer onValueUpdate) {
        return new OptionInstance<>(captionId, OptionInstance.cachedConstantTooltip(Component.translatable(captionId + ".tooltip")), (caption, value) -> Options.genericValueLabel(caption, Component.literal(value + " MiB")), new OptionInstance.IntRange(minInclusive, maxInclusive), initialValue, onValueUpdate::accept);
    }
    
    private static void setWidgetActive(OptionsList optionsList, OptionInstance<?> option, boolean active) {
        AbstractWidget widget = optionsList.findOption(option);
        if (widget != null) {
            widget.active = active;
        }
    }
}