package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.util.Map;

public final class VulkanRuntimeProfileInvariantCheck {
	private VulkanRuntimeProfileInvariantCheck() {
	}
	
	
	static void main() {
		TerrainRendererDebugConfig.setFragmentShadingRateEnabled(true);
		checkEmptySnapshotRuntimeProfile();
		checkSupportedSnapshotRuntimeProfile();
		checkSnapshotDescribesRuntimeProfile();
		checkRtPtAccelerationAllocationGate();
	}
	
	/**
	 * Allocation of acceleration structures is disabled on every device until an allocation plan
	 * exists. The profile says whether the device is the obstacle and names what it lacks.
	 */
	private static void checkRtPtAccelerationAllocationGate() {
		VulkanRuntimeProfile unsupported = VulkanRuntimeProfile.from(VulkanCapabilitySnapshot.empty());
		require("disabled".equals(unsupported.selectedPaths().rtPtAccelerationAllocation()), "acceleration allocation must be disabled without device support");
		require("requires acceleration structures, ray-tracing pipelines, buffer device addresses, descriptor buffers".equals(unsupported.disabledReasons().get(VulkanRuntimeProfile.RT_PT_ACCELERATION_ALLOCATION)), "a device without support must be told every capability allocation requires");
		require(!unsupported.rtPtAccelerationAllocationSupported(), "an unconfigured device must not count as supporting allocation");
		
		VulkanRuntimeProfile supported = VulkanRuntimeProfile.from(supportedSnapshot());
		require(supported.rtPtAccelerationAllocationSupported(), "a device with every capability must count as supporting allocation");
		require("disabled".equals(supported.selectedPaths().rtPtAccelerationAllocation()), "acceleration allocation must stay disabled until an allocation plan is active");
		require(VulkanRuntimeProfile.RT_PT_ALLOCATION_NOT_PLANNED.equals(supported.disabledReasons().get(VulkanRuntimeProfile.RT_PT_ACCELERATION_ALLOCATION)), "a capable device must be told that allocation is not planned, not that it lacks something");
	}
	
	private static void checkEmptySnapshotRuntimeProfile() {
		VulkanRuntimeProfile profile = VulkanRuntimeProfile.from(VulkanCapabilitySnapshot.empty());
		VulkanSelectedPaths selectedPaths = profile.selectedPaths();
		Map<String, String> disabledReasons = profile.disabledReasons();
		
		require("descriptor-buffer-plus-heap".equals(selectedPaths.descriptorPath()), "default descriptor path must include heap");
		require("unavailable".equals(selectedPaths.descriptorBuffer()), "empty profile must report descriptor buffer unavailable");
		require("unavailable".equals(selectedPaths.descriptorHeap()), "empty profile must report descriptor heap unavailable");
		require("unavailable".equals(selectedPaths.meshTaskDispatch()), "empty profile must report mesh-task dispatch unavailable");
		require("unavailable".equals(selectedPaths.multiDrawFallback()), "empty profile must report multi-draw fallback unavailable");
		require("unavailable".equals(selectedPaths.deviceGeneratedCommands()), "empty profile must report device-generated commands unavailable");
		require("not-ready".equals(selectedPaths.rtPtReadiness()), "empty profile must report RT/PT path not ready");
		require(!selectedPaths.presentPacing(), "empty profile must disable present pacing");
		require(!selectedPaths.fragmentShadingRate(), "empty profile must disable fragment shading rate");
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
		VulkanRuntimeProfile profile = VulkanRuntimeProfile.from(supportedSnapshot());
		VulkanSelectedPaths selectedPaths = profile.selectedPaths();
		Map<String, String> disabledReasons = profile.disabledReasons();
		
		require("selected".equals(selectedPaths.descriptorBuffer()), "supported profile must select descriptor buffer");
		require("selected".equals(selectedPaths.descriptorHeap()), "supported profile must select descriptor heap");
		require("mesh-task-indirect".equals(selectedPaths.meshTaskDispatch()), "supported profile must select mesh-task indirect dispatch");
		require("available".equals(selectedPaths.multiDrawFallback()), "supported profile must report multi-draw fallback availability");
		require("available".equals(selectedPaths.deviceGeneratedCommands()), "supported profile must report DGC availability");
		require("ready".equals(selectedPaths.rtPtReadiness()), "supported profile must report RT/PT readiness");
		require(selectedPaths.presentPacing(), "supported profile must enable present pacing");
		require(selectedPaths.fragmentShadingRate(), "supported profile must enable fragment shading rate");
		require("mesh-overlay-vanilla-fallback".equals(selectedPaths.terrainRendererMode()), "supported profile must preserve terrain renderer mode");
		require(!disabledReasons.containsKey("descriptorBuffer"), "supported profile must not disable selected descriptor buffer");
		require(!disabledReasons.containsKey("descriptorHeap"), "supported profile must not disable selected descriptor heap");
		require(!disabledReasons.containsKey("meshTaskDispatch"), "supported profile must not disable selected mesh-task dispatch");
		require(!disabledReasons.containsKey("multiDrawFallback"), "supported profile must not disable available multi-draw fallback");
		require(!disabledReasons.containsKey("deviceGeneratedCommands"), "supported profile must not disable available DGC");
		require(!disabledReasons.containsKey("rtPtReadiness"), "supported profile must not disable ready RT/PT path");
		require(!disabledReasons.containsKey("presentPacing"), "supported profile must not disable present pacing");
		require(!disabledReasons.containsKey("fragmentShadingRate"), "supported profile must not disable fragment shading rate");
	}
	
	private static void checkSnapshotDescribesRuntimeProfile() {
		require(supportedSnapshot().describe().contains("selectedPaths=VulkanSelectedPaths["), "a snapshot must describe the paths it selects");
	}
	
	private static VulkanCapabilitySnapshot supportedSnapshot() {
		return new VulkanCapabilitySnapshot("test device", "test vendor", "test driver", "1.4.0", "mesh-overlay-vanilla-fallback", true, true, true, true, true, true, true, true, true, true, true, true, true, true, TerrainGpuLayout.MAX_MESH_OUTPUT_VERTICES, TerrainGpuLayout.MAX_MESH_OUTPUT_PRIMITIVES, 32, 32, 1L << 20, 1L << 20, 32L, 32L, 32L, 32L, 16, 16, 32L, 32L, 32L, 32L, 1L << 20, 1L << 20, 4, 4, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true);
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}