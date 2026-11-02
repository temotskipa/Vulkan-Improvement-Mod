package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

public record VulkanCapabilitySnapshot(String deviceName, String vendorName, String driverInfo, String apiVersion, String terrainRendererMode, boolean meshShaderExtension, boolean descriptorHeapExtension, boolean descriptorBufferEnabled, boolean fragmentShadingRateExtension, boolean presentIdExtension, boolean presentWaitExtension, boolean shaderInt64, boolean scalarBlockLayout, boolean bufferDeviceAddress, boolean maintenance4, boolean shaderDemoteToHelperInvocation, boolean dynamicRenderingLocalRead, boolean maintenance5, boolean maintenance6, int maxMeshOutputVertices, int maxMeshOutputPrimitives, int maxPreferredTaskWorkGroupInvocations, int maxPreferredMeshWorkGroupInvocations, long maxSamplerHeapSize, long maxResourceHeapSize, long samplerDescriptorSize, long imageDescriptorSize, long bufferDescriptorSize, long descriptorBufferOffsetAlignment, int maxResourceDescriptorBufferBindings, int maxSamplerDescriptorBufferBindings, long descriptorBufferSamplerDescriptorSize, long descriptorBufferSampledImageDescriptorSize, long descriptorBufferUniformBufferDescriptorSize, long descriptorBufferStorageBufferDescriptorSize, long maxSamplerDescriptorBufferRange, long maxResourceDescriptorBufferRange, int maxFragmentWidth, int maxFragmentHeight, boolean deviceGeneratedCommandsExtension, boolean multiDrawExtension, boolean shaderObjectExtension, boolean graphicsPipelineLibraryExtension, boolean pipelineLibraryExtension, boolean descriptorBufferExtension, boolean memoryBudgetExtension, boolean memoryPriorityExtension, boolean pageableDeviceLocalMemoryExtension, boolean hostImageCopyExtension, boolean accelerationStructureExtension, boolean rayQueryExtension, boolean rayTracingPipelineExtension, boolean rayTracingMaintenance1Extension, boolean rayTracingPositionFetchExtension, boolean opacityMicromapExtension, boolean rayTracingInvocationReorderExtension) {
	public static VulkanCapabilitySnapshot empty() {
		return new VulkanCapabilitySnapshot("unknown", "unknown", "unknown", "unknown", TerrainRendererDebugConfig.rendererMode(), false, false, false, false, false, false, false, false, false, false, false, false, false, false, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
	}
	
	/**
	 * Every capability, and which paths they select.
	 */
	public String describe() {
		return this + ", " + VulkanRuntimeProfile.from(this).describe();
	}
}