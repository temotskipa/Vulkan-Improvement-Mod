package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.logging.LogUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class VulkanImprovementCapabilities {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Snapshot lastSnapshot = Snapshot.empty();
    
    private VulkanImprovementCapabilities() {
    }
    
    public static Snapshot capture(VulkanPhysicalDevice physicalDevice, Set<String> enabledDeviceExtensions) {
        Snapshot snapshot;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMeshShaderPropertiesEXT meshProperties = VkPhysicalDeviceMeshShaderPropertiesEXT.calloc(stack).sType$Default();
            VkPhysicalDeviceDescriptorHeapPropertiesEXT descriptorHeapProperties = VkPhysicalDeviceDescriptorHeapPropertiesEXT.calloc(stack).sType$Default();
            VkPhysicalDeviceDescriptorBufferPropertiesEXT descriptorBufferProperties = VkPhysicalDeviceDescriptorBufferPropertiesEXT.calloc(stack).sType$Default();
            VkPhysicalDeviceFragmentShadingRatePropertiesKHR fragmentShadingRateProperties = VkPhysicalDeviceFragmentShadingRatePropertiesKHR.calloc(stack).sType$Default();
            
            long pNext = 0L;
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
            
            VkPhysicalDeviceMaintenance6FeaturesKHR maintenance6Features = VkPhysicalDeviceMaintenance6FeaturesKHR.calloc(stack).sType$Default();
            VkPhysicalDeviceMaintenance5FeaturesKHR maintenance5Features = VkPhysicalDeviceMaintenance5FeaturesKHR.calloc(stack).sType$Default().pNext(maintenance6Features.address());
            VkPhysicalDeviceDynamicRenderingLocalReadFeaturesKHR dynamicRenderingLocalReadFeatures = VkPhysicalDeviceDynamicRenderingLocalReadFeaturesKHR.calloc(stack).sType$Default().pNext(maintenance5Features.address());
            VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT shaderDemoteFeatures = VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT.calloc(stack).sType$Default().pNext(dynamicRenderingLocalReadFeatures.address());
            VkPhysicalDeviceMaintenance4FeaturesKHR maintenance4Features = VkPhysicalDeviceMaintenance4FeaturesKHR.calloc(stack).sType$Default().pNext(shaderDemoteFeatures.address());
            VkPhysicalDeviceVulkan12Features vulkan12Features = VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default().pNext(maintenance4Features.address());
            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(vulkan12Features.address());
            VK12.vkGetPhysicalDeviceFeatures2(physicalDevice.vkPhysicalDevice(), features2);
            
            snapshot = new Snapshot(physicalDevice.deviceName(), physicalDevice.vendorName(), physicalDevice.driverInfo(), VulkanFeatureRequirements.versionString(properties2.properties().apiVersion()), TerrainRendererDebugConfig.rendererMode(), enabledDeviceExtensions.contains(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME), enabledDeviceExtensions.contains(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME), enabledDeviceExtensions.contains(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME), enabledDeviceExtensions.contains(KHRFragmentShadingRate.VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME), enabledDeviceExtensions.contains(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME), enabledDeviceExtensions.contains(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME), features2.features().shaderInt64(), vulkan12Features.scalarBlockLayout(), maintenance4Features.maintenance4(), shaderDemoteFeatures.shaderDemoteToHelperInvocation(), dynamicRenderingLocalReadFeatures.dynamicRenderingLocalRead(), maintenance5Features.maintenance5(), maintenance6Features.maintenance6(), meshProperties.maxMeshOutputVertices(), meshProperties.maxMeshOutputPrimitives(), meshProperties.maxPreferredTaskWorkGroupInvocations(), meshProperties.maxPreferredMeshWorkGroupInvocations(), descriptorHeapProperties.maxSamplerHeapSize(), descriptorHeapProperties.maxResourceHeapSize(), descriptorHeapProperties.samplerDescriptorSize(), descriptorHeapProperties.imageDescriptorSize(), descriptorHeapProperties.bufferDescriptorSize(), descriptorBufferProperties.descriptorBufferOffsetAlignment(), descriptorBufferProperties.maxResourceDescriptorBufferBindings(), descriptorBufferProperties.maxSamplerDescriptorBufferBindings(), descriptorBufferProperties.samplerDescriptorSize(), descriptorBufferProperties.sampledImageDescriptorSize(), descriptorBufferProperties.uniformBufferDescriptorSize(), descriptorBufferProperties.storageBufferDescriptorSize(), descriptorBufferProperties.maxSamplerDescriptorBufferRange(), descriptorBufferProperties.maxResourceDescriptorBufferRange(), fragmentShadingRateProperties.maxFragmentSize().width(), fragmentShadingRateProperties.maxFragmentSize().height(), physicalDevice.hasDeviceExtension(EXTDeviceGeneratedCommands.VK_EXT_DEVICE_GENERATED_COMMANDS_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTShaderObject.VK_EXT_SHADER_OBJECT_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTGraphicsPipelineLibrary.VK_EXT_GRAPHICS_PIPELINE_LIBRARY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRPipelineLibrary.VK_KHR_PIPELINE_LIBRARY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTMemoryPriority.VK_EXT_MEMORY_PRIORITY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTPageableDeviceLocalMemory.VK_EXT_PAGEABLE_DEVICE_LOCAL_MEMORY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTHostImageCopy.VK_EXT_HOST_IMAGE_COPY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayTracingMaintenance1.VK_KHR_RAY_TRACING_MAINTENANCE_1_EXTENSION_NAME), physicalDevice.hasDeviceExtension(KHRRayTracingPositionFetch.VK_KHR_RAY_TRACING_POSITION_FETCH_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTOpacityMicromap.VK_EXT_OPACITY_MICROMAP_EXTENSION_NAME), physicalDevice.hasDeviceExtension(EXTRayTracingInvocationReorder.VK_EXT_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME));
        }
        
        lastSnapshot = snapshot;
        if (TerrainRendererDebugConfig.dumpCapabilities()) {
            LOGGER.info("[Vulkan Improvement] {}", snapshot.toJson());
        }
        return snapshot;
    }
    
    public static Snapshot snapshot() {
        return lastSnapshot;
    }
    
    public static Map<String, Object> asMap() {
        return lastSnapshot.asMap();
    }
    
    @SuppressWarnings("unused")
    public static String json() {
        return lastSnapshot.toJson();
    }
    
    public record Snapshot(String deviceName, String vendorName, String driverInfo, String apiVersion,
                           String terrainRendererMode, boolean meshShaderExtension, boolean descriptorHeapExtension,
                           boolean descriptorBufferEnabled, boolean fragmentShadingRateExtension,
                           boolean presentIdExtension, boolean presentWaitExtension, boolean shaderInt64,
                           boolean scalarBlockLayout, boolean maintenance4, boolean shaderDemoteToHelperInvocation,
                           boolean dynamicRenderingLocalRead, boolean maintenance5, boolean maintenance6,
                           int maxMeshOutputVertices, int maxMeshOutputPrimitives,
                           int maxPreferredTaskWorkGroupInvocations, int maxPreferredMeshWorkGroupInvocations,
                           long maxSamplerHeapSize, long maxResourceHeapSize, long samplerDescriptorSize,
                           long imageDescriptorSize, long bufferDescriptorSize, long descriptorBufferOffsetAlignment,
                           int maxResourceDescriptorBufferBindings, int maxSamplerDescriptorBufferBindings,
                           long descriptorBufferSamplerDescriptorSize, long descriptorBufferSampledImageDescriptorSize,
                           long descriptorBufferUniformBufferDescriptorSize,
                           long descriptorBufferStorageBufferDescriptorSize, long maxSamplerDescriptorBufferRange,
                           long maxResourceDescriptorBufferRange, int maxFragmentWidth, int maxFragmentHeight,
                           boolean deviceGeneratedCommandsExtension, boolean multiDrawExtension,
                           boolean shaderObjectExtension, boolean graphicsPipelineLibraryExtension,
                           boolean pipelineLibraryExtension, boolean descriptorBufferExtension,
                           boolean memoryBudgetExtension, boolean memoryPriorityExtension,
                           boolean pageableDeviceLocalMemoryExtension, boolean hostImageCopyExtension,
                           boolean accelerationStructureExtension, boolean rayQueryExtension,
                           boolean rayTracingPipelineExtension, boolean rayTracingMaintenance1Extension,
                           boolean rayTracingPositionFetchExtension, boolean opacityMicromapExtension,
                           boolean rayTracingInvocationReorderExtension) {
        public static Snapshot empty() {
            return new Snapshot("unknown", "unknown", "unknown", "unknown", TerrainRendererDebugConfig.rendererMode(), false, false, false, false, false, false, false, false, false, false, false, false, false, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
        }
        
        private static void appendJsonValue(StringBuilder builder, Object value) {
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else if (value instanceof Map<?, ?> map) {
                builder.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) {
                        builder.append(',');
                    }
                    first = false;
                    builder.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append("\":");
                    appendJsonValue(builder, entry.getValue());
                }
                builder.append('}');
            } else {
                builder.append('"').append(escapeJson(String.valueOf(value))).append('"');
            }
        }
        
        private static String escapeJson(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
        
        public Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("deviceName", this.deviceName);
            map.put("vendorName", this.vendorName);
            map.put("driverInfo", this.driverInfo);
            map.put("apiVersion", this.apiVersion);
            map.put("terrainRendererMode", this.terrainRendererMode);
            map.put("meshShaderExtension", this.meshShaderExtension);
            map.put("descriptorHeapExtension", this.descriptorHeapExtension);
            map.put("descriptorBufferEnabled", this.descriptorBufferEnabled);
            map.put("fragmentShadingRateExtension", this.fragmentShadingRateExtension);
            map.put("presentIdExtension", this.presentIdExtension);
            map.put("presentWaitExtension", this.presentWaitExtension);
            map.put("shaderInt64", this.shaderInt64);
            map.put("scalarBlockLayout", this.scalarBlockLayout);
            map.put("maintenance4", this.maintenance4);
            map.put("shaderDemoteToHelperInvocation", this.shaderDemoteToHelperInvocation);
            map.put("dynamicRenderingLocalRead", this.dynamicRenderingLocalRead);
            map.put("maintenance5", this.maintenance5);
            map.put("maintenance6", this.maintenance6);
            map.put("maxMeshOutputVertices", this.maxMeshOutputVertices);
            map.put("maxMeshOutputPrimitives", this.maxMeshOutputPrimitives);
            map.put("maxPreferredTaskWorkGroupInvocations", this.maxPreferredTaskWorkGroupInvocations);
            map.put("maxPreferredMeshWorkGroupInvocations", this.maxPreferredMeshWorkGroupInvocations);
            map.put("maxSamplerHeapSize", this.maxSamplerHeapSize);
            map.put("maxResourceHeapSize", this.maxResourceHeapSize);
            map.put("samplerDescriptorSize", this.samplerDescriptorSize);
            map.put("imageDescriptorSize", this.imageDescriptorSize);
            map.put("bufferDescriptorSize", this.bufferDescriptorSize);
            map.put("descriptorBufferOffsetAlignment", this.descriptorBufferOffsetAlignment);
            map.put("maxResourceDescriptorBufferBindings", this.maxResourceDescriptorBufferBindings);
            map.put("maxSamplerDescriptorBufferBindings", this.maxSamplerDescriptorBufferBindings);
            map.put("descriptorBufferSamplerDescriptorSize", this.descriptorBufferSamplerDescriptorSize);
            map.put("descriptorBufferSampledImageDescriptorSize", this.descriptorBufferSampledImageDescriptorSize);
            map.put("descriptorBufferUniformBufferDescriptorSize", this.descriptorBufferUniformBufferDescriptorSize);
            map.put("descriptorBufferStorageBufferDescriptorSize", this.descriptorBufferStorageBufferDescriptorSize);
            map.put("maxSamplerDescriptorBufferRange", this.maxSamplerDescriptorBufferRange);
            map.put("maxResourceDescriptorBufferRange", this.maxResourceDescriptorBufferRange);
            map.put("maxFragmentWidth", this.maxFragmentWidth);
            map.put("maxFragmentHeight", this.maxFragmentHeight);
            map.put("deviceGeneratedCommandsExtension", this.deviceGeneratedCommandsExtension);
            map.put("multiDrawExtension", this.multiDrawExtension);
            map.put("shaderObjectExtension", this.shaderObjectExtension);
            map.put("graphicsPipelineLibraryExtension", this.graphicsPipelineLibraryExtension);
            map.put("pipelineLibraryExtension", this.pipelineLibraryExtension);
            map.put("descriptorBufferExtension", this.descriptorBufferExtension);
            map.put("memoryBudgetExtension", this.memoryBudgetExtension);
            map.put("memoryPriorityExtension", this.memoryPriorityExtension);
            map.put("pageableDeviceLocalMemoryExtension", this.pageableDeviceLocalMemoryExtension);
            map.put("hostImageCopyExtension", this.hostImageCopyExtension);
            map.put("accelerationStructureExtension", this.accelerationStructureExtension);
            map.put("rayQueryExtension", this.rayQueryExtension);
            map.put("rayTracingPipelineExtension", this.rayTracingPipelineExtension);
            map.put("rayTracingMaintenance1Extension", this.rayTracingMaintenance1Extension);
            map.put("rayTracingPositionFetchExtension", this.rayTracingPositionFetchExtension);
            map.put("opacityMicromapExtension", this.opacityMicromapExtension);
            map.put("rayTracingInvocationReorderExtension", this.rayTracingInvocationReorderExtension);
            map.put("runtimeProfile", VulkanRuntimeProfile.from(this).asMap());
            return map;
        }
        
        public String toJson() {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : this.asMap().entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(entry.getKey()).append("\":");
                appendJsonValue(builder, entry.getValue());
            }
            return builder.append('}').toString();
        }
    }
}
