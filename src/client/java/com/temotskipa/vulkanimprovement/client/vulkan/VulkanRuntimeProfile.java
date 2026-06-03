package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.LinkedHashMap;
import java.util.Map;

final class VulkanRuntimeProfile {
    private final Map<String, Object> hardRequirements;
    private final Map<String, Object> preferredFeatures;
    private final Map<String, Object> rtReadiness;
    private final Map<String, Object> selectedPaths;
    private final Map<String, Object> disabledReasons;
    
    private VulkanRuntimeProfile(Map<String, Object> hardRequirements, Map<String, Object> preferredFeatures, Map<String, Object> rtReadiness, Map<String, Object> selectedPaths, Map<String, Object> disabledReasons) {
        this.hardRequirements = Map.copyOf(hardRequirements);
        this.preferredFeatures = Map.copyOf(preferredFeatures);
        this.rtReadiness = Map.copyOf(rtReadiness);
        this.selectedPaths = Map.copyOf(selectedPaths);
        this.disabledReasons = Map.copyOf(disabledReasons);
    }
    
    static VulkanRuntimeProfile from(VulkanImprovementCapabilities.Snapshot snapshot) {
        Map<String, Object> hardRequirements = new LinkedHashMap<>();
        hardRequirements.put("apiVersion", snapshot.apiVersion());
        hardRequirements.put("meshShaderExtension", snapshot.meshShaderExtension());
        hardRequirements.put("descriptorBufferExtension", snapshot.descriptorBufferExtension());
        hardRequirements.put("descriptorHeapExtensionRequired", !TerrainRendererDebugConfig.VALIDATION_DESCRIPTOR_BUFFER_ONLY);
        hardRequirements.put("descriptorHeapExtension", snapshot.descriptorHeapExtension());
        hardRequirements.put("fragmentShadingRateExtension", snapshot.fragmentShadingRateExtension());
        hardRequirements.put("presentIdExtension", snapshot.presentIdExtension());
        hardRequirements.put("presentWaitExtension", snapshot.presentWaitExtension());
        hardRequirements.put("shaderInt64", snapshot.shaderInt64());
        hardRequirements.put("scalarBlockLayout", snapshot.scalarBlockLayout());
        hardRequirements.put("maintenance4", snapshot.maintenance4());
        hardRequirements.put("shaderDemoteToHelperInvocation", snapshot.shaderDemoteToHelperInvocation());
        hardRequirements.put("dynamicRenderingLocalRead", snapshot.dynamicRenderingLocalRead());
        hardRequirements.put("maintenance5", snapshot.maintenance5());
        hardRequirements.put("maintenance6", snapshot.maintenance6());
        
        Map<String, Object> preferredFeatures = new LinkedHashMap<>();
        preferredFeatures.put("deviceGeneratedCommandsExtension", snapshot.deviceGeneratedCommandsExtension());
        preferredFeatures.put("multiDrawExtension", snapshot.multiDrawExtension());
        preferredFeatures.put("shaderObjectExtension", snapshot.shaderObjectExtension());
        preferredFeatures.put("graphicsPipelineLibraryExtension", snapshot.graphicsPipelineLibraryExtension());
        preferredFeatures.put("pipelineLibraryExtension", snapshot.pipelineLibraryExtension());
        preferredFeatures.put("memoryBudgetExtension", snapshot.memoryBudgetExtension());
        preferredFeatures.put("memoryPriorityExtension", snapshot.memoryPriorityExtension());
        preferredFeatures.put("pageableDeviceLocalMemoryExtension", snapshot.pageableDeviceLocalMemoryExtension());
        preferredFeatures.put("hostImageCopyExtension", snapshot.hostImageCopyExtension());
        
        Map<String, Object> rtReadiness = new LinkedHashMap<>();
        rtReadiness.put("accelerationStructureExtension", snapshot.accelerationStructureExtension());
        rtReadiness.put("rayQueryExtension", snapshot.rayQueryExtension());
        rtReadiness.put("rayTracingPipelineExtension", snapshot.rayTracingPipelineExtension());
        rtReadiness.put("rayTracingMaintenance1Extension", snapshot.rayTracingMaintenance1Extension());
        rtReadiness.put("rayTracingPositionFetchExtension", snapshot.rayTracingPositionFetchExtension());
        rtReadiness.put("opacityMicromapExtension", snapshot.opacityMicromapExtension());
        rtReadiness.put("rayTracingInvocationReorderExtension", snapshot.rayTracingInvocationReorderExtension());
        
        Map<String, Object> selectedPaths = new LinkedHashMap<>();
        selectedPaths.put("descriptorPath", TerrainRendererDebugConfig.VALIDATION_DESCRIPTOR_BUFFER_ONLY ? "descriptor-buffer-only" : "descriptor-buffer-plus-heap");
        selectedPaths.put("presentPacing", snapshot.presentIdExtension() && snapshot.presentWaitExtension() && !TerrainRendererDebugConfig.DISABLE_PRESENT_PACING);
        selectedPaths.put("fragmentShadingRate", snapshot.fragmentShadingRateExtension() && TerrainRendererDebugConfig.fragmentShadingRateEnabled());
        selectedPaths.put("terrainRendererMode", snapshot.terrainRendererMode());
        
        Map<String, Object> disabledReasons = new LinkedHashMap<>();
        preferredFeatures.forEach((name, enabled) -> addDisabledReason(disabledReasons, name, enabled, "optional extension not enabled by Minecraft/Vulkan device"));
        rtReadiness.forEach((name, enabled) -> addDisabledReason(disabledReasons, name, enabled, "RT/PT readiness extension not enabled by Minecraft/Vulkan device"));
        if (TerrainRendererDebugConfig.DISABLE_PRESENT_PACING) {
            disabledReasons.put("presentPacing", "disabled by vim.disablePresentPacing");
        } else if (!snapshot.presentIdExtension() || !snapshot.presentWaitExtension()) {
            disabledReasons.put("presentPacing", "requires present id and present wait");
        }
        if (!TerrainRendererDebugConfig.fragmentShadingRateEnabled()) {
            disabledReasons.put("fragmentShadingRate", "disabled by vim.disableFragmentShadingRate");
        } else if (!snapshot.fragmentShadingRateExtension()) {
            disabledReasons.put("fragmentShadingRate", "requires fragment shading-rate extension");
        }
        
        return new VulkanRuntimeProfile(hardRequirements, preferredFeatures, rtReadiness, selectedPaths, disabledReasons);
    }
    
    private static void addDisabledReason(Map<String, Object> disabledReasons, String name, Object enabled, String reason) {
        if (enabled instanceof Boolean supported && !supported) {
            disabledReasons.put(name, reason);
        }
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hardRequirements", this.hardRequirements);
        map.put("preferredFeatures", this.preferredFeatures);
        map.put("rtReadiness", this.rtReadiness);
        map.put("selectedPaths", this.selectedPaths);
        map.put("disabledReasons", this.disabledReasons);
        return map;
    }
}
