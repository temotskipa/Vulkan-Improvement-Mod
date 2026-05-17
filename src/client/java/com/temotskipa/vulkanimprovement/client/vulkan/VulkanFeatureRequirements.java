package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VulkanFeatureRequirements {
    public static final String VULKAN_14_CAPABILITY = "Vulkan Improvement Mod requires VULKAN_CORE_1_4; remove the mod to use vanilla renderer";

    private static final VulkanPNextStruct PHYSICAL_DEVICE_FEATURES = new VulkanPNextStruct(VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2, VkPhysicalDeviceFeatures2.SIZEOF);
    private static final VulkanPNextStruct VULKAN_12_FEATURES = new VulkanPNextStruct(VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES, VkPhysicalDeviceVulkan12Features.SIZEOF);
    private static final VulkanPNextStruct MAINTENANCE_4_FEATURES = new VulkanPNextStruct(VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES, VkPhysicalDeviceMaintenance4FeaturesKHR.SIZEOF);
    private static final VulkanPNextStruct SHADER_DEMOTE_FEATURES = new VulkanPNextStruct(VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_DEMOTE_TO_HELPER_INVOCATION_FEATURES, VkPhysicalDeviceShaderDemoteToHelperInvocationFeaturesEXT.SIZEOF);
    private static final VulkanPNextStruct DYNAMIC_RENDERING_LOCAL_READ_FEATURES = new VulkanPNextStruct(VK14.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_LOCAL_READ_FEATURES, VkPhysicalDeviceDynamicRenderingLocalReadFeaturesKHR.SIZEOF);
    private static final VulkanPNextStruct MAINTENANCE_5_FEATURES = new VulkanPNextStruct(VK14.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_5_FEATURES, VkPhysicalDeviceMaintenance5FeaturesKHR.SIZEOF);
    private static final VulkanPNextStruct MAINTENANCE_6_FEATURES = new VulkanPNextStruct(VK14.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_6_FEATURES, VkPhysicalDeviceMaintenance6FeaturesKHR.SIZEOF);
    private static final VulkanPNextStruct MESH_SHADER_FEATURES = new VulkanPNextStruct(EXTMeshShader.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MESH_SHADER_FEATURES_EXT, VkPhysicalDeviceMeshShaderFeaturesEXT.SIZEOF);
    private static final VulkanPNextStruct DESCRIPTOR_HEAP_FEATURES = new VulkanPNextStruct(EXTDescriptorHeap.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_HEAP_FEATURES_EXT, VkPhysicalDeviceDescriptorHeapFeaturesEXT.SIZEOF);
    private static final VulkanPNextStruct DESCRIPTOR_BUFFER_FEATURES = new VulkanPNextStruct(EXTDescriptorBuffer.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_BUFFER_FEATURES_EXT, VkPhysicalDeviceDescriptorBufferFeaturesEXT.SIZEOF);
    private static final VulkanPNextStruct FRAGMENT_SHADING_RATE_FEATURES = new VulkanPNextStruct(KHRFragmentShadingRate.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_FEATURES_KHR, VkPhysicalDeviceFragmentShadingRateFeaturesKHR.SIZEOF);
    private static final VulkanPNextStruct PRESENT_ID_FEATURES = new VulkanPNextStruct(KHRPresentId.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_ID_FEATURES_KHR, VkPhysicalDevicePresentIdFeaturesKHR.SIZEOF);
    private static final VulkanPNextStruct PRESENT_WAIT_FEATURES = new VulkanPNextStruct(KHRPresentWait.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_WAIT_FEATURES_KHR, VkPhysicalDevicePresentWaitFeaturesKHR.SIZEOF);

    private VulkanFeatureRequirements() {
    }

    public static Set<String> requiredDeviceExtensions() {
        Set<String> extensions = new LinkedHashSet<>();
        extensions.add(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);
        if (!TerrainRendererDebugConfig.VALIDATION_DESCRIPTOR_BUFFER_ONLY) {
            extensions.add(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME);
        }
        extensions.add(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME);
        extensions.add(KHRFragmentShadingRate.VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME);
        extensions.add(KHRDynamicRenderingLocalRead.VK_KHR_DYNAMIC_RENDERING_LOCAL_READ_EXTENSION_NAME);
        extensions.add(KHRMaintenance4.VK_KHR_MAINTENANCE_4_EXTENSION_NAME);
        extensions.add(KHRMaintenance5.VK_KHR_MAINTENANCE_5_EXTENSION_NAME);
        extensions.add(KHRMaintenance6.VK_KHR_MAINTENANCE_6_EXTENSION_NAME);
        extensions.add(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME);
        extensions.add(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME);
        return extensions;
    }

    public static Set<VulkanFeature> requiredDeviceFeatures() {
        Set<VulkanFeature> features = new LinkedHashSet<>();
        features.add(new VulkanFeature(PHYSICAL_DEVICE_FEATURES, "vim.shaderInt64", VkPhysicalDeviceFeatures.SHADERINT64));
        features.add(new VulkanFeature(MAINTENANCE_4_FEATURES, "vim.vulkan13.maintenance4", VkPhysicalDeviceMaintenance4Features.MAINTENANCE4));
        features.add(new VulkanFeature(SHADER_DEMOTE_FEATURES, "vim.vulkan13.shaderDemoteToHelperInvocation", VkPhysicalDeviceShaderDemoteToHelperInvocationFeatures.SHADERDEMOTETOHELPERINVOCATION));
        features.add(new VulkanFeature(DYNAMIC_RENDERING_LOCAL_READ_FEATURES, "vim.vulkan14.dynamicRenderingLocalRead", VkPhysicalDeviceDynamicRenderingLocalReadFeatures.DYNAMICRENDERINGLOCALREAD));
        features.add(new VulkanFeature(MAINTENANCE_5_FEATURES, "vim.vulkan14.maintenance5", VkPhysicalDeviceMaintenance5Features.MAINTENANCE5));
        features.add(new VulkanFeature(MAINTENANCE_6_FEATURES, "vim.vulkan14.maintenance6", VkPhysicalDeviceMaintenance6Features.MAINTENANCE6));
        features.add(new VulkanFeature(VULKAN_12_FEATURES, "vim.vulkan12.bufferDeviceAddress", VkPhysicalDeviceVulkan12Features.BUFFERDEVICEADDRESS));
        features.add(new VulkanFeature(VULKAN_12_FEATURES, "vim.vulkan12.scalarBlockLayout", VkPhysicalDeviceVulkan12Features.SCALARBLOCKLAYOUT));
        features.add(new VulkanFeature(MESH_SHADER_FEATURES, "vim.mesh.taskShader", VkPhysicalDeviceMeshShaderFeaturesEXT.TASKSHADER));
        features.add(new VulkanFeature(MESH_SHADER_FEATURES, "vim.mesh.meshShader", VkPhysicalDeviceMeshShaderFeaturesEXT.MESHSHADER));
        if (!TerrainRendererDebugConfig.VALIDATION_DESCRIPTOR_BUFFER_ONLY) {
            features.add(new VulkanFeature(DESCRIPTOR_HEAP_FEATURES, "vim.descriptorHeap", VkPhysicalDeviceDescriptorHeapFeaturesEXT.DESCRIPTORHEAP));
        }
        features.add(new VulkanFeature(DESCRIPTOR_BUFFER_FEATURES, "vim.descriptorBuffer", VkPhysicalDeviceDescriptorBufferFeaturesEXT.DESCRIPTORBUFFER));
        features.add(new VulkanFeature(FRAGMENT_SHADING_RATE_FEATURES, "vim.fragmentShadingRate.pipeline", VkPhysicalDeviceFragmentShadingRateFeaturesKHR.PIPELINEFRAGMENTSHADINGRATE));
        features.add(new VulkanFeature(PRESENT_ID_FEATURES, "vim.presentId", VkPhysicalDevicePresentIdFeaturesKHR.PRESENTID));
        features.add(new VulkanFeature(PRESENT_WAIT_FEATURES, "vim.presentWait", VkPhysicalDevicePresentWaitFeaturesKHR.PRESENTWAIT));
        return features;
    }

    public static boolean supportsRequiredApi(VkPhysicalDevice physicalDevice) {
        return apiVersion(physicalDevice) >= VK14.VK_API_VERSION_1_4;
    }

    public static List<String> missingRequiredCapabilities(VkPhysicalDevice vkPhysicalDevice) throws BackendCreationException {
        List<String> missingCapabilities = new ArrayList<>();
        int apiVersion = apiVersion(vkPhysicalDevice);
        if (apiVersion < VK14.VK_API_VERSION_1_4) {
            missingCapabilities.add(VULKAN_14_CAPABILITY + " (found " + versionString(apiVersion) + ")");
            return missingCapabilities;
        }

        try (VulkanPhysicalDevice physicalDevice = new VulkanPhysicalDevice(vkPhysicalDevice)) {
            missingCapabilities.addAll(physicalDevice.getMissingExtensions(requiredDeviceExtensions()));
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures2 deviceFeatures = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            for (VulkanFeature requiredDeviceFeature : requiredDeviceFeatures()) {
                requiredDeviceFeature.struct().findOrCreateStructInPNextChain(deviceFeatures, stack);
            }
            VK12.vkGetPhysicalDeviceFeatures2(vkPhysicalDevice, deviceFeatures);
            for (VulkanFeature requiredDeviceFeature : requiredDeviceFeatures()) {
                if (!requiredDeviceFeature.get(deviceFeatures)) {
                    missingCapabilities.add(requiredDeviceFeature.name());
                }
            }
        }

        return List.copyOf(missingCapabilities);
    }

    public static int apiVersion(VkPhysicalDevice physicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK12.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            return properties.apiVersion();
        }
    }

    public static String versionString(int version) {
        int major = version >>> 22;
        int minor = version >>> 12 & 0x3ff;
        int patch = version & 0xfff;
        return major + "." + minor + "." + patch;
    }
}
