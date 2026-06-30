package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainGpuLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

import java.util.Map;

public final class VulkanRuntimeProfileInvariantCheck {
    private VulkanRuntimeProfileInvariantCheck() {
    }
    
    @SuppressWarnings("unused")
    static void main(String[] args) {
        TerrainRendererDebugConfig.setFragmentShadingRateEnabled(true);
        checkEmptySnapshotRuntimeProfile();
        checkSupportedSnapshotRuntimeProfile();
        checkSnapshotMapIncludesRuntimeProfile();
    }
    
    private static void checkEmptySnapshotRuntimeProfile() {
        Map<String, Object> profile = profile(VulkanImprovementCapabilities.Snapshot.empty());
        Map<?, ?> hardRequirements = child(profile, "hardRequirements");
        Map<?, ?> preferredFeatures = child(profile, "preferredFeatures");
        Map<?, ?> rtReadiness = child(profile, "rtReadiness");
        Map<?, ?> selectedPaths = child(profile, "selectedPaths");
        Map<?, ?> disabledReasons = child(profile, "disabledReasons");
        require("unknown".equals(hardRequirements.get("apiVersion")), "empty profile must preserve unknown API version");
        require(Boolean.TRUE.equals(hardRequirements.get("descriptorHeapExtensionRequired")), "descriptor heap should be required outside descriptor-buffer-only validation mode");
        require(Boolean.FALSE.equals(preferredFeatures.get("deviceGeneratedCommandsExtension")), "empty profile must report optional DGC unavailable");
        require(Boolean.FALSE.equals(rtReadiness.get("accelerationStructureExtension")), "empty profile must report RT acceleration structures unavailable");
        require("descriptor-buffer-plus-heap".equals(selectedPaths.get("descriptorPath")), "default descriptor path must include heap");
        require("unavailable".equals(selectedPaths.get("descriptorBuffer")), "empty profile must report descriptor buffer unavailable");
        require("unavailable".equals(selectedPaths.get("descriptorHeap")), "empty profile must report descriptor heap unavailable");
        require("unavailable".equals(selectedPaths.get("meshTaskDispatch")), "empty profile must report mesh-task dispatch unavailable");
        require("unavailable".equals(selectedPaths.get("multiDrawFallback")), "empty profile must report multi-draw fallback unavailable");
        require("unavailable".equals(selectedPaths.get("deviceGeneratedCommands")), "empty profile must report device-generated commands unavailable");
        require("not-ready".equals(selectedPaths.get("rtPtReadiness")), "empty profile must report RT/PT path not ready");
        require(Boolean.FALSE.equals(selectedPaths.get("presentPacing")), "empty profile must disable present pacing");
        require(Boolean.FALSE.equals(selectedPaths.get("fragmentShadingRate")), "empty profile must disable fragment shading rate");
        require(disabledReasons.containsKey("deviceGeneratedCommandsExtension"), "empty profile must explain missing optional DGC");
        require(disabledReasons.containsKey("accelerationStructureExtension"), "empty profile must explain missing RT readiness");
        require(disabledReasons.containsKey("descriptorBuffer"), "empty profile must explain descriptor buffer selected path");
        require(disabledReasons.containsKey("descriptorHeap"), "empty profile must explain descriptor heap selected path");
        require(disabledReasons.containsKey("meshTaskDispatch"), "empty profile must explain mesh-task dispatch selected path");
        require(disabledReasons.containsKey("multiDrawFallback"), "empty profile must explain multi-draw fallback selected path");
        require(disabledReasons.containsKey("deviceGeneratedCommands"), "empty profile must explain device-generated commands selected path");
        require(disabledReasons.containsKey("rtPtReadiness"), "empty profile must explain RT/PT selected path");
        require("requires present id and present wait".equals(disabledReasons.get("presentPacing")), "empty profile must explain present pacing requirements");
        require("requires fragment shading-rate extension".equals(disabledReasons.get("fragmentShadingRate")), "empty profile must explain FSR requirements");
    }
    
    private static void checkSupportedSnapshotRuntimeProfile() {
        Map<String, Object> profile = profile(supportedSnapshot());
        Map<?, ?> hardRequirements = child(profile, "hardRequirements");
        Map<?, ?> preferredFeatures = child(profile, "preferredFeatures");
        Map<?, ?> rtReadiness = child(profile, "rtReadiness");
        Map<?, ?> selectedPaths = child(profile, "selectedPaths");
        Map<?, ?> disabledReasons = child(profile, "disabledReasons");
        require("1.4.0".equals(hardRequirements.get("apiVersion")), "supported profile must preserve API version");
        require(Boolean.TRUE.equals(hardRequirements.get("meshShaderExtension")), "supported profile must report mesh shader hard requirement");
        require(Boolean.TRUE.equals(hardRequirements.get("descriptorBufferExtension")), "supported profile must report descriptor buffer hard requirement");
        require(Boolean.TRUE.equals(preferredFeatures.get("deviceGeneratedCommandsExtension")), "supported profile must report DGC availability");
        require(Boolean.TRUE.equals(rtReadiness.get("rayTracingPipelineExtension")), "supported profile must report RT pipeline readiness");
        require("selected".equals(selectedPaths.get("descriptorBuffer")), "supported profile must select descriptor buffer");
        require("selected".equals(selectedPaths.get("descriptorHeap")), "supported profile must select descriptor heap");
        require("mesh-task-indirect".equals(selectedPaths.get("meshTaskDispatch")), "supported profile must select mesh-task indirect dispatch");
        require("available".equals(selectedPaths.get("multiDrawFallback")), "supported profile must report multi-draw fallback availability");
        require("available".equals(selectedPaths.get("deviceGeneratedCommands")), "supported profile must report DGC availability");
        require("ready".equals(selectedPaths.get("rtPtReadiness")), "supported profile must report RT/PT readiness");
        require(Boolean.TRUE.equals(selectedPaths.get("presentPacing")), "supported profile must enable present pacing");
        require(Boolean.TRUE.equals(selectedPaths.get("fragmentShadingRate")), "supported profile must enable fragment shading rate");
        require("mesh-required".equals(selectedPaths.get("terrainRendererMode")), "supported profile must preserve terrain renderer mode");
        require(!disabledReasons.containsKey("deviceGeneratedCommandsExtension"), "supported profile must not explain enabled DGC as disabled");
        require(!disabledReasons.containsKey("rayTracingPipelineExtension"), "supported profile must not explain enabled RT pipeline as disabled");
        require(!disabledReasons.containsKey("descriptorBuffer"), "supported profile must not disable selected descriptor buffer");
        require(!disabledReasons.containsKey("descriptorHeap"), "supported profile must not disable selected descriptor heap");
        require(!disabledReasons.containsKey("meshTaskDispatch"), "supported profile must not disable selected mesh-task dispatch");
        require(!disabledReasons.containsKey("multiDrawFallback"), "supported profile must not disable available multi-draw fallback");
        require(!disabledReasons.containsKey("deviceGeneratedCommands"), "supported profile must not disable available DGC");
        require(!disabledReasons.containsKey("rtPtReadiness"), "supported profile must not disable ready RT/PT path");
        require(!disabledReasons.containsKey("presentPacing"), "supported profile must not disable present pacing");
        require(!disabledReasons.containsKey("fragmentShadingRate"), "supported profile must not disable fragment shading rate");
    }
    
    private static void checkSnapshotMapIncludesRuntimeProfile() {
        Map<String, Object> snapshot = supportedSnapshot().asMap();
        require(snapshot.get("runtimeProfile") instanceof Map<?, ?>, "snapshot diagnostics must include nested runtime profile");
    }
    
    private static Map<String, Object> profile(VulkanImprovementCapabilities.Snapshot snapshot) {
        return VulkanRuntimeProfile.from(snapshot).asMap();
    }
    
    private static VulkanImprovementCapabilities.Snapshot supportedSnapshot() {
        return new VulkanImprovementCapabilities.Snapshot("test device", "test vendor", "test driver", "1.4.0", "mesh-required", true, true, true, true, true, true, true, true, true, true, true, true, true, TerrainGpuLayout.MAX_MESH_OUTPUT_VERTICES, TerrainGpuLayout.MAX_MESH_OUTPUT_PRIMITIVES, 32, 32, 1L << 20, 1L << 20, 32L, 32L, 32L, 32L, 16, 16, 32L, 32L, 32L, 32L, 1L << 20, 1L << 20, 4, 4, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true);
    }
    
    private static Map<?, ?> child(Map<String, Object> profile, String key) {
        Object value = profile.get(key);
        require(value instanceof Map<?, ?>, "runtime profile child '" + key + "' must be a map");
        return (Map<?, ?>) value;
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}