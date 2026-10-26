package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public record VulkanCapabilitySnapshot(String deviceName, String vendorName, String driverInfo, String apiVersion, String terrainRendererMode, boolean meshShaderExtension, boolean descriptorHeapExtension, boolean descriptorBufferEnabled, boolean fragmentShadingRateExtension, boolean presentIdExtension, boolean presentWaitExtension, boolean shaderInt64, boolean scalarBlockLayout, boolean bufferDeviceAddress, boolean maintenance4, boolean shaderDemoteToHelperInvocation, boolean dynamicRenderingLocalRead, boolean maintenance5, boolean maintenance6, int maxMeshOutputVertices, int maxMeshOutputPrimitives, int maxPreferredTaskWorkGroupInvocations, int maxPreferredMeshWorkGroupInvocations, long maxSamplerHeapSize, long maxResourceHeapSize, long samplerDescriptorSize, long imageDescriptorSize, long bufferDescriptorSize, long descriptorBufferOffsetAlignment, int maxResourceDescriptorBufferBindings, int maxSamplerDescriptorBufferBindings, long descriptorBufferSamplerDescriptorSize, long descriptorBufferSampledImageDescriptorSize, long descriptorBufferUniformBufferDescriptorSize, long descriptorBufferStorageBufferDescriptorSize, long maxSamplerDescriptorBufferRange, long maxResourceDescriptorBufferRange, int maxFragmentWidth, int maxFragmentHeight, boolean deviceGeneratedCommandsExtension, boolean multiDrawExtension, boolean shaderObjectExtension, boolean graphicsPipelineLibraryExtension, boolean pipelineLibraryExtension, boolean descriptorBufferExtension, boolean memoryBudgetExtension, boolean memoryPriorityExtension, boolean pageableDeviceLocalMemoryExtension, boolean hostImageCopyExtension, boolean accelerationStructureExtension, boolean rayQueryExtension, boolean rayTracingPipelineExtension, boolean rayTracingMaintenance1Extension, boolean rayTracingPositionFetchExtension, boolean opacityMicromapExtension, boolean rayTracingInvocationReorderExtension) {
    public static VulkanCapabilitySnapshot empty() {
        return new VulkanCapabilitySnapshot("unknown", "unknown", "unknown", "unknown", TerrainRendererDebugConfig.rendererMode(), false, false, false, false, false, false, false, false, false, false, false, false, false, false, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
    }
    
    private static void appendJsonValue(StringBuilder builder, Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
        }
        else if (value instanceof Map<?, ?> map) {
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
        }
        else {
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
        map.put("bufferDeviceAddress", this.bufferDeviceAddress);
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