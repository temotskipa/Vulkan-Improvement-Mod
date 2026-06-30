package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VulkanRuntimeProfile {
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
    
    public static VulkanRuntimeProfile from(VulkanImprovementCapabilities.Snapshot snapshot) {
        Map<String, Object> hardRequirements = new LinkedHashMap<>();
        hardRequirements.put("apiVersion", snapshot.apiVersion());
        hardRequirements.put("meshShaderExtension", snapshot.meshShaderExtension());
        hardRequirements.put("descriptorBufferExtension", snapshot.descriptorBufferExtension());
        hardRequirements.put("descriptorHeapExtensionRequired", !TerrainRendererDebugConfig.validationDescriptorBufferOnly());
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
        Map<String, Object> preferredFeatures = preferredFeatures(snapshot);
        Map<String, Object> rtReadiness = rtReadiness(snapshot);
        Map<String, Object> selectedPaths = new LinkedHashMap<>();
        boolean descriptorBufferSelected = snapshot.descriptorBufferExtension();
        boolean descriptorHeapSelected = !TerrainRendererDebugConfig.validationDescriptorBufferOnly() && snapshot.descriptorHeapExtension();
        boolean meshTaskDispatchSelected = snapshot.meshShaderExtension() && snapshot.descriptorBufferExtension();
        boolean gpuGeneratedMeshTaskCommandsSelected = meshTaskDispatchSelected && TerrainRendererDebugConfig.enableGpuGeneratedMeshTaskCommands();
        boolean rtPtReady = rtPtReady(snapshot);
        selectedPaths.put("descriptorPath", TerrainRendererDebugConfig.validationDescriptorBufferOnly() ? "descriptor-buffer-only" : "descriptor-buffer-plus-heap");
        selectedPaths.put("descriptorBuffer", selectedState(descriptorBufferSelected));
        selectedPaths.put("descriptorHeap", TerrainRendererDebugConfig.validationDescriptorBufferOnly() ? "disabled-by-validation" : selectedState(descriptorHeapSelected));
        selectedPaths.put("meshTaskDispatch", meshTaskDispatchSelected ? "mesh-task-indirect" : "unavailable");
        selectedPaths.put("gpuGeneratedMeshTaskCommands", gpuGeneratedMeshTaskCommandsSelected ? "enabled" : "disabled");
        selectedPaths.put("multiDrawFallback", snapshot.multiDrawExtension() ? "available" : "unavailable");
        selectedPaths.put("deviceGeneratedCommands", snapshot.deviceGeneratedCommandsExtension() ? "available" : "unavailable");
        selectedPaths.put("rtPtReadiness", rtPtReady ? "ready" : "not-ready");
        selectedPaths.put("presentPacing", snapshot.presentIdExtension() && snapshot.presentWaitExtension() && !TerrainRendererDebugConfig.disablePresentPacing());
        selectedPaths.put("fragmentShadingRate", snapshot.fragmentShadingRateExtension() && TerrainRendererDebugConfig.fragmentShadingRateEnabled());
        selectedPaths.put("terrainRendererMode", snapshot.terrainRendererMode());
        Map<String, Object> disabledReasons = new LinkedHashMap<>();
        preferredFeatures.forEach((name, enabled) -> addDisabledReason(disabledReasons, name, enabled, "optional extension not enabled by Minecraft/Vulkan device"));
        rtReadiness.forEach((name, enabled) -> addDisabledReason(disabledReasons, name, enabled, "RT/PT readiness extension not enabled by Minecraft/Vulkan device"));
        if (!descriptorBufferSelected) {
            disabledReasons.put("descriptorBuffer", "requires descriptor buffer extension");
        }
        if (TerrainRendererDebugConfig.validationDescriptorBufferOnly()) {
            disabledReasons.put("descriptorHeap", "disabled by vim.validationDescriptorBufferOnly");
        } else if (!descriptorHeapSelected) {
            disabledReasons.put("descriptorHeap", "requires descriptor heap extension");
        }
        if (!meshTaskDispatchSelected) {
            disabledReasons.put("meshTaskDispatch", "requires mesh shader and descriptor buffer");
        }
        if (!TerrainRendererDebugConfig.enableGpuGeneratedMeshTaskCommands()) {
            disabledReasons.put("gpuGeneratedMeshTaskCommands", "disabled by vim.enableGpuGeneratedMeshTaskCommands=false");
        } else if (!meshTaskDispatchSelected) {
            disabledReasons.put("gpuGeneratedMeshTaskCommands", "requires mesh-task dispatch");
        }
        if (!snapshot.multiDrawExtension()) {
            disabledReasons.put("multiDrawFallback", "optional multi-draw extension not enabled by Minecraft/Vulkan device");
        }
        if (!snapshot.deviceGeneratedCommandsExtension()) {
            disabledReasons.put("deviceGeneratedCommands", "optional device-generated commands extension not enabled by Minecraft/Vulkan device");
        }
        if (!rtPtReady) {
            disabledReasons.put("rtPtReadiness", "requires all RT/PT readiness extensions");
        }
        if (TerrainRendererDebugConfig.disablePresentPacing()) {
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
    
    private static String selectedState(boolean selected) {
        return selected ? "selected" : "unavailable";
    }
    
    private static Map<String, Object> preferredFeatures(VulkanImprovementCapabilities.Snapshot snapshot) {
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
        return preferredFeatures;
    }
    
    private static Map<String, Object> rtReadiness(VulkanImprovementCapabilities.Snapshot snapshot) {
        Map<String, Object> rtReadiness = new LinkedHashMap<>();
        rtReadiness.put("accelerationStructureExtension", snapshot.accelerationStructureExtension());
        rtReadiness.put("rayQueryExtension", snapshot.rayQueryExtension());
        rtReadiness.put("rayTracingPipelineExtension", snapshot.rayTracingPipelineExtension());
        rtReadiness.put("rayTracingMaintenance1Extension", snapshot.rayTracingMaintenance1Extension());
        rtReadiness.put("rayTracingPositionFetchExtension", snapshot.rayTracingPositionFetchExtension());
        rtReadiness.put("opacityMicromapExtension", snapshot.opacityMicromapExtension());
        rtReadiness.put("rayTracingInvocationReorderExtension", snapshot.rayTracingInvocationReorderExtension());
        return rtReadiness;
    }
    
    private static boolean rtPtReady(VulkanImprovementCapabilities.Snapshot snapshot) {
        return snapshot.accelerationStructureExtension() && snapshot.rayQueryExtension() && snapshot.rayTracingPipelineExtension() && snapshot.rayTracingMaintenance1Extension() && snapshot.rayTracingPositionFetchExtension() && snapshot.opacityMicromapExtension() && snapshot.rayTracingInvocationReorderExtension();
    }
    
    private static void addDisabledReason(Map<String, Object> disabledReasons, String name, Object enabled, String reason) {
        if (enabled instanceof Boolean supported && !supported) {
            disabledReasons.put(name, reason);
        }
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hardRequirements", this.hardRequirements);
        map.put("preferredFeatures", this.preferredFeatures);
        map.put("rtReadiness", this.rtReadiness);
        map.put("selectedPaths", this.selectedPaths);
        map.put("disabledReasons", this.disabledReasons);
        return map;
    }
}