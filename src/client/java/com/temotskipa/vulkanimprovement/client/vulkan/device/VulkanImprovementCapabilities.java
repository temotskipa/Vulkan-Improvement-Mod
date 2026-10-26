package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

public final class VulkanImprovementCapabilities {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile VulkanCapabilitySnapshot lastSnapshot = VulkanCapabilitySnapshot.empty();
    private static volatile int maxTaskWorkGroups;
    private static volatile long maxBufferBytes;
    
    private VulkanImprovementCapabilities() {
    }
    
    public static VulkanCapabilitySnapshot capture(VulkanPhysicalDevice physicalDevice, Set<String> enabledDeviceExtensions) {
        VulkanCapabilitySnapshot snapshot;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMeshShaderPropertiesEXT meshProperties = VkPhysicalDeviceMeshShaderPropertiesEXT.calloc(stack).sType$Default();
            VkPhysicalDeviceDescriptorHeapPropertiesEXT descriptorHeapProperties = VkPhysicalDeviceDescriptorHeapPropertiesEXT.calloc(stack).sType$Default();
            VkPhysicalDeviceDescriptorBufferPropertiesEXT descriptorBufferProperties = VkPhysicalDeviceDescriptorBufferPropertiesEXT.calloc(stack).sType$Default();
            VkPhysicalDeviceFragmentShadingRatePropertiesKHR fragmentShadingRateProperties = VkPhysicalDeviceFragmentShadingRatePropertiesKHR.calloc(stack).sType$Default();
            // Core in Vulkan 1.4, which the mod requires.
            VkPhysicalDeviceVulkan13Properties vulkan13Properties = VkPhysicalDeviceVulkan13Properties.calloc(stack).sType$Default();
            VkPhysicalDeviceVulkan11Properties vulkan11Properties = VkPhysicalDeviceVulkan11Properties.calloc(stack).sType$Default().pNext(vulkan13Properties.address());
            
            long pNext = vulkan11Properties.address();
            if (physicalDevice.hasDeviceExtension(KHRFragmentShadingRate.VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME)) {
                fragmentShadingRateProperties.pNext(pNext);
                pNext = fragmentShadingRateProperties.address();
            }
            if (physicalDevice.hasDeviceExtension(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME)) {
                descriptorBufferProperties.pNext(pNext);
                pNext = descriptorBufferProperties.address();
            }
            if (!TerrainRendererDebugConfig.validationDescriptorBufferOnly() && physicalDevice.hasDeviceExtension(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME)) {
                descriptorHeapProperties.pNext(pNext);
                pNext = descriptorHeapProperties.address();
            }
            if (physicalDevice.hasDeviceExtension(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME)) {
                meshProperties.pNext(pNext);
                pNext = meshProperties.address();
            }
            
            VkPhysicalDeviceProperties2 properties2 = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(pNext);
            VK12.vkGetPhysicalDeviceProperties2(physicalDevice.vkPhysicalDevice(), properties2);
            maxTaskWorkGroups = Math.min(meshProperties.maxTaskWorkGroupCount(0), meshProperties.maxTaskWorkGroupTotalCount());
            maxBufferBytes = Math.min(deviceSize(vulkan13Properties.maxBufferSize()), deviceSize(vulkan11Properties.maxMemoryAllocationSize()));
            
            VkPhysicalDeviceMaintenance6FeaturesKHR maintenance6Features = VkPhysicalDeviceMaintenance6FeaturesKHR.calloc(stack).sType$Default();
            VkPhysicalDeviceMaintenance5FeaturesKHR maintenance5Features = VkPhysicalDeviceMaintenance5FeaturesKHR.calloc(stack).sType$Default().pNext(maintenance6Features.address());
            VkPhysicalDeviceDynamicRenderingLocalReadFeaturesKHR dynamicRenderingLocalReadFeatures = VkPhysicalDeviceDynamicRenderingLocalReadFeaturesKHR.calloc(stack).sType$Default().pNext(maintenance5Features.address());
            VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT shaderDemoteFeatures = VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT.calloc(stack).sType$Default().pNext(dynamicRenderingLocalReadFeatures.address());
            VkPhysicalDeviceMaintenance4FeaturesKHR maintenance4Features = VkPhysicalDeviceMaintenance4FeaturesKHR.calloc(stack).sType$Default().pNext(shaderDemoteFeatures.address());
            VkPhysicalDeviceVulkan12Features vulkan12Features = VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default().pNext(maintenance4Features.address());
            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(vulkan12Features.address());
            VK12.vkGetPhysicalDeviceFeatures2(physicalDevice.vkPhysicalDevice(), features2);
            
            snapshot = new VulkanCapabilitySnapshot(physicalDevice.deviceName(), physicalDevice.vendorName(), physicalDevice.driverInfo(), VulkanFeatureRequirements.versionString(properties2.properties().apiVersion()), TerrainRendererDebugConfig.rendererMode(), enabledDeviceExtensions.contains(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME), enabledDeviceExtensions.contains(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME), enabledDeviceExtensions.contains(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME), enabledDeviceExtensions.contains(KHRFragmentShadingRate.VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME), enabledDeviceExtensions.contains(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME), enabledDeviceExtensions.contains(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME), features2.features().shaderInt64(), vulkan12Features.scalarBlockLayout(), vulkan12Features.bufferDeviceAddress(), maintenance4Features.maintenance4(), shaderDemoteFeatures.shaderDemoteToHelperInvocation(), dynamicRenderingLocalReadFeatures.dynamicRenderingLocalRead(), maintenance5Features.maintenance5(), maintenance6Features.maintenance6(), meshProperties.maxMeshOutputVertices(), meshProperties.maxMeshOutputPrimitives(), meshProperties.maxPreferredTaskWorkGroupInvocations(), meshProperties.maxPreferredMeshWorkGroupInvocations(), descriptorHeapProperties.maxSamplerHeapSize(), descriptorHeapProperties.maxResourceHeapSize(), descriptorHeapProperties.samplerDescriptorSize(), descriptorHeapProperties.imageDescriptorSize(), descriptorHeapProperties.bufferDescriptorSize(), descriptorBufferProperties.descriptorBufferOffsetAlignment(), descriptorBufferProperties.maxResourceDescriptorBufferBindings(), descriptorBufferProperties.maxSamplerDescriptorBufferBindings(), descriptorBufferProperties.samplerDescriptorSize(), descriptorBufferProperties.sampledImageDescriptorSize(), descriptorBufferProperties.uniformBufferDescriptorSize(), descriptorBufferProperties.storageBufferDescriptorSize(), descriptorBufferProperties.maxSamplerDescriptorBufferRange(), descriptorBufferProperties.maxResourceDescriptorBufferRange(), fragmentShadingRateProperties.maxFragmentSize().width(), fragmentShadingRateProperties.maxFragmentSize().height(), physicalDevice.hasDeviceExtension(EXTDeviceGeneratedCommands.VK_EXT_DEVICE_GENERATED_COMMANDS_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTShaderObject.VK_EXT_SHADER_OBJECT_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTGraphicsPipelineLibrary.VK_EXT_GRAPHICS_PIPELINE_LIBRARY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRPipelineLibrary.VK_KHR_PIPELINE_LIBRARY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTMemoryPriority.VK_EXT_MEMORY_PRIORITY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTPageableDeviceLocalMemory.VK_EXT_PAGEABLE_DEVICE_LOCAL_MEMORY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTHostImageCopy.VK_EXT_HOST_IMAGE_COPY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayTracingMaintenance1.VK_KHR_RAY_TRACING_MAINTENANCE_1_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayTracingPositionFetch.VK_KHR_RAY_TRACING_POSITION_FETCH_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTOpacityMicromap.VK_EXT_OPACITY_MICROMAP_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTRayTracingInvocationReorder.VK_EXT_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME));
        }
        
        lastSnapshot = snapshot;
        if (TerrainRendererDebugConfig.dumpCapabilities()) {
            LOGGER.info("[Vulkan Improvement] {}", snapshot.toJson());
        }
        return snapshot;
    }
    
    /**
     * A size the device reports, which is unsigned: a device with no limit reports the largest
     * value, which Java reads as negative.
     */
    private static long deviceSize(long size) {
        return size < 0L ? Long.MAX_VALUE : size;
    }
    
    public static VulkanCapabilitySnapshot snapshot() {
        return lastSnapshot;
    }
    
    /**
     * The most tasks one mesh-task draw may launch in one row, or 0 before a device with mesh
     * shaders was looked at.
     */
    public static int maxTaskWorkGroups() {
        return maxTaskWorkGroups;
    }
    
    /**
     * The most bytes one buffer may hold, in one allocation, or 0 before a device was looked at.
     */
    public static long maxBufferBytes() {
        return maxBufferBytes;
    }
    
    public static Map<String, Object> asMap() {
        return lastSnapshot.asMap();
    }
    
    
    public static String json() {
        return lastSnapshot.toJson();
    }
}