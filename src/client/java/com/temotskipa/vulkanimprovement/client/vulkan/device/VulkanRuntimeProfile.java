package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VulkanRuntimeProfile {
	public static final String RT_PT_ACCELERATION_ALLOCATION = "rtPtAccelerationAllocation";
	static final String RT_PT_ALLOCATION_NOT_PLANNED = "device supports it; no acceleration-structure allocation plan is active";
	
	private final VulkanSelectedPaths selectedPaths;
	private final Map<String, String> disabledReasons;
	private final boolean rtPtAccelerationAllocationSupported;
	
	private VulkanRuntimeProfile(VulkanSelectedPaths selectedPaths, Map<String, String> disabledReasons, boolean rtPtAccelerationAllocationSupported) {
		this.selectedPaths = selectedPaths;
		this.disabledReasons = Collections.unmodifiableMap(disabledReasons);
		this.rtPtAccelerationAllocationSupported = rtPtAccelerationAllocationSupported;
	}
	
	public static VulkanRuntimeProfile from(VulkanCapabilitySnapshot snapshot) {
		boolean descriptorBufferSelected = snapshot.descriptorBufferExtension();
		boolean descriptorHeapSelected = !TerrainRendererDebugConfig.validationDescriptorBufferOnly() && snapshot.descriptorHeapExtension();
		boolean meshTaskDispatchSelected = snapshot.meshShaderExtension() && snapshot.descriptorBufferExtension();
		boolean gpuGeneratedMeshTaskCommandsSelected = meshTaskDispatchSelected && TerrainRendererDebugConfig.enableGpuGeneratedMeshTaskCommands();
		boolean rtPtReady = rtPtReady(snapshot);
		// No acceleration structure is allocated yet, so the path is disabled on every device; the
		// reason says whether this device is what stands in the way.
		String rtPtAllocationBlocker = rtPtAccelerationAllocationBlocker(snapshot, descriptorBufferSelected);
		String descriptorPath = TerrainRendererDebugConfig.validationDescriptorBufferOnly() ? "descriptor-buffer-only" : "descriptor-buffer-plus-heap";
		String descriptorHeap = TerrainRendererDebugConfig.validationDescriptorBufferOnly() ? "disabled-by-validation" : selectedState(descriptorHeapSelected);
		boolean presentPacing = snapshot.presentIdExtension() && snapshot.presentWaitExtension() && !TerrainRendererDebugConfig.disablePresentPacing();
		boolean fragmentShadingRate = snapshot.fragmentShadingRateExtension() && TerrainRendererDebugConfig.fragmentShadingRateEnabled();
		VulkanSelectedPaths selectedPaths = new VulkanSelectedPaths(descriptorPath, selectedState(descriptorBufferSelected), descriptorHeap, meshTaskDispatchSelected ? "mesh-task-indirect" : "unavailable", gpuGeneratedMeshTaskCommandsSelected ? "enabled" : "disabled", snapshot.multiDrawExtension() ? "available" : "unavailable", snapshot.deviceGeneratedCommandsExtension() ? "available" : "unavailable", rtPtReady ? "ready" : "not-ready", "disabled", presentPacing, fragmentShadingRate, snapshot.terrainRendererMode());
		
		Map<String, String> disabledReasons = new LinkedHashMap<>();
		if (!descriptorBufferSelected) {
			disabledReasons.put("descriptorBuffer", "requires descriptor buffer extension");
		}
		if (TerrainRendererDebugConfig.validationDescriptorBufferOnly()) {
			disabledReasons.put("descriptorHeap", "disabled by vim.validationDescriptorBufferOnly");
		}
		else if (!descriptorHeapSelected) {
			disabledReasons.put("descriptorHeap", "requires descriptor heap extension");
		}
		if (!meshTaskDispatchSelected) {
			disabledReasons.put("meshTaskDispatch", "requires mesh shader and descriptor buffer");
		}
		if (!TerrainRendererDebugConfig.enableGpuGeneratedMeshTaskCommands()) {
			disabledReasons.put("gpuGeneratedMeshTaskCommands", "disabled by vim.enableGpuGeneratedMeshTaskCommands=false");
		}
		else if (!meshTaskDispatchSelected) {
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
		disabledReasons.put(RT_PT_ACCELERATION_ALLOCATION, rtPtAllocationBlocker.isEmpty() ? RT_PT_ALLOCATION_NOT_PLANNED : "requires " + rtPtAllocationBlocker);
		if (TerrainRendererDebugConfig.disablePresentPacing()) {
			disabledReasons.put("presentPacing", "disabled by vim.disablePresentPacing");
		}
		else if (!snapshot.presentIdExtension() || !snapshot.presentWaitExtension()) {
			disabledReasons.put("presentPacing", "requires present id and present wait");
		}
		if (!TerrainRendererDebugConfig.fragmentShadingRateEnabled()) {
			disabledReasons.put("fragmentShadingRate", "disabled by vim.disableFragmentShadingRate");
		}
		else if (!snapshot.fragmentShadingRateExtension()) {
			disabledReasons.put("fragmentShadingRate", "requires fragment shading-rate extension");
		}
		
		return new VulkanRuntimeProfile(selectedPaths, disabledReasons, rtPtAllocationBlocker.isEmpty());
	}
	
	private static boolean rtPtReady(VulkanCapabilitySnapshot snapshot) {
		return snapshot.accelerationStructureExtension() && snapshot.rayQueryExtension() && snapshot.rayTracingPipelineExtension() && snapshot.rayTracingMaintenance1Extension() && snapshot.rayTracingPositionFetchExtension() && snapshot.opacityMicromapExtension() && snapshot.rayTracingInvocationReorderExtension();
	}
	
	private static String selectedState(boolean selected) {
		return selected ? "selected" : "unavailable";
	}
	
	/**
	 * What the device lacks for allocating acceleration structures, or nothing. Allocation needs
	 * the structures themselves, a pipeline to trace them with, device addresses for their build
	 * inputs, and the descriptor path the renderer binds them through.
	 */
	private static String rtPtAccelerationAllocationBlocker(VulkanCapabilitySnapshot snapshot, boolean descriptorBufferSelected) {
		List<String> missing = new ArrayList<>();
		if (!snapshot.accelerationStructureExtension()) {
			missing.add("acceleration structures");
		}
		if (!snapshot.rayTracingPipelineExtension()) {
			missing.add("ray-tracing pipelines");
		}
		if (!snapshot.bufferDeviceAddress()) {
			missing.add("buffer device addresses");
		}
		if (!descriptorBufferSelected) {
			missing.add("descriptor buffers");
		}
		return String.join(", ", missing);
	}
	
	/**
	 * Whether the device could allocate acceleration structures. This is the device half of the
	 * gate; whether allocation is switched on is the acceleration-data registry's to say.
	 */
	public boolean rtPtAccelerationAllocationSupported() {
		return this.rtPtAccelerationAllocationSupported;
	}
	
	public String rtPtAccelerationAllocationDisabledReason() {
		return this.disabledReasons.get(RT_PT_ACCELERATION_ALLOCATION);
	}
	
	public VulkanSelectedPaths selectedPaths() {
		return this.selectedPaths;
	}
	
	/**
	 * Why a path above is not selected, by its name.
	 */
	public Map<String, String> disabledReasons() {
		return this.disabledReasons;
	}
	
	public String describe() {
		return "selectedPaths=" + this.selectedPaths + ", disabledReasons=" + this.disabledReasons;
	}
}