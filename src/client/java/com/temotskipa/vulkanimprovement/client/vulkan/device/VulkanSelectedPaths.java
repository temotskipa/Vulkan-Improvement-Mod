package com.temotskipa.vulkanimprovement.client.vulkan.device;

/**
 * Which rendering paths a device and the mod's flags select.
 */
public record VulkanSelectedPaths(String descriptorPath, String descriptorBuffer, String descriptorHeap, String meshTaskDispatch, String gpuGeneratedMeshTaskCommands, String multiDrawFallback, String deviceGeneratedCommands, String rtPtReadiness, String rtPtAccelerationAllocation, boolean presentPacing, boolean fragmentShadingRate, String terrainRendererMode) {
}
