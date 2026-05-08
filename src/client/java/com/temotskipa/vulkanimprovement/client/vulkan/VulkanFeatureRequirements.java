package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.LinkedHashSet;
import java.util.Set;

public final class VulkanFeatureRequirements {
    public static final String VULKAN_14_CAPABILITY = "Vulkan Improvement Mod requires VULKAN_CORE_1_4; remove the mod to use vanilla renderer";
    
    private static final VulkanPNextStruct VULKAN_14_FEATURES = new VulkanPNextStruct(
            VK14.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_4_FEATURES,
            VkPhysicalDeviceVulkan14Features.SIZEOF
    );
    private static final VulkanPNextStruct VULKAN_12_FEATURES = new VulkanPNextStruct(
            VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES,
            VkPhysicalDeviceVulkan12Features.SIZEOF
    );
    private static final VulkanPNextStruct MESH_SHADER_FEATURES = new VulkanPNextStruct(
            EXTMeshShader.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MESH_SHADER_FEATURES_EXT,
            VkPhysicalDeviceMeshShaderFeaturesEXT.SIZEOF
    );
    private static final VulkanPNextStruct DESCRIPTOR_HEAP_FEATURES = new VulkanPNextStruct(
            EXTDescriptorHeap.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_HEAP_FEATURES_EXT,
            VkPhysicalDeviceDescriptorHeapFeaturesEXT.SIZEOF
    );
    private static final VulkanPNextStruct DESCRIPTOR_BUFFER_FEATURES = new VulkanPNextStruct(
            EXTDescriptorBuffer.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_BUFFER_FEATURES_EXT,
            VkPhysicalDeviceDescriptorBufferFeaturesEXT.SIZEOF
    );
    private static final VulkanPNextStruct FRAGMENT_SHADING_RATE_FEATURES = new VulkanPNextStruct(
            KHRFragmentShadingRate.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FRAGMENT_SHADING_RATE_FEATURES_KHR,
            VkPhysicalDeviceFragmentShadingRateFeaturesKHR.SIZEOF
    );
    private static final VulkanPNextStruct PRESENT_ID_FEATURES = new VulkanPNextStruct(
            KHRPresentId.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_ID_FEATURES_KHR,
            VkPhysicalDevicePresentIdFeaturesKHR.SIZEOF
    );
    private static final VulkanPNextStruct PRESENT_WAIT_FEATURES = new VulkanPNextStruct(
            KHRPresentWait.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PRESENT_WAIT_FEATURES_KHR,
            VkPhysicalDevicePresentWaitFeaturesKHR.SIZEOF
    );
    
    private VulkanFeatureRequirements() {
    }
    
    public static Set<String> requiredDeviceExtensions() {
        Set<String> extensions = new LinkedHashSet<>();
        extensions.add(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);
        extensions.add(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME);
        extensions.add(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME);
        extensions.add(KHRFragmentShadingRate.VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME);
        extensions.add(KHRDynamicRenderingLocalRead.VK_KHR_DYNAMIC_RENDERING_LOCAL_READ_EXTENSION_NAME);
        extensions.add(KHRMaintenance5.VK_KHR_MAINTENANCE_5_EXTENSION_NAME);
        extensions.add(KHRMaintenance6.VK_KHR_MAINTENANCE_6_EXTENSION_NAME);
        extensions.add(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME);
        extensions.add(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME);
        return extensions;
    }
    
    public static Set<VulkanFeature> requiredDeviceFeatures() {
        Set<VulkanFeature> features = new LinkedHashSet<>();
        features.add(new VulkanFeature(VULKAN_14_FEATURES, "vim.vulkan14.dynamicRenderingLocalRead", VkPhysicalDeviceVulkan14Features.DYNAMICRENDERINGLOCALREAD));
        features.add(new VulkanFeature(VULKAN_14_FEATURES, "vim.vulkan14.maintenance5", VkPhysicalDeviceVulkan14Features.MAINTENANCE5));
        features.add(new VulkanFeature(VULKAN_14_FEATURES, "vim.vulkan14.maintenance6", VkPhysicalDeviceVulkan14Features.MAINTENANCE6));
        features.add(new VulkanFeature(VULKAN_14_FEATURES, "vim.vulkan14.pushDescriptor", VkPhysicalDeviceVulkan14Features.PUSHDESCRIPTOR));
        features.add(new VulkanFeature(VULKAN_12_FEATURES, "vim.vulkan12.bufferDeviceAddress", VkPhysicalDeviceVulkan12Features.BUFFERDEVICEADDRESS));
        features.add(new VulkanFeature(MESH_SHADER_FEATURES, "vim.mesh.taskShader", VkPhysicalDeviceMeshShaderFeaturesEXT.TASKSHADER));
        features.add(new VulkanFeature(MESH_SHADER_FEATURES, "vim.mesh.meshShader", VkPhysicalDeviceMeshShaderFeaturesEXT.MESHSHADER));
        features.add(new VulkanFeature(DESCRIPTOR_HEAP_FEATURES, "vim.descriptorHeap", VkPhysicalDeviceDescriptorHeapFeaturesEXT.DESCRIPTORHEAP));
        features.add(new VulkanFeature(DESCRIPTOR_BUFFER_FEATURES, "vim.descriptorBuffer", VkPhysicalDeviceDescriptorBufferFeaturesEXT.DESCRIPTORBUFFER));
        features.add(new VulkanFeature(FRAGMENT_SHADING_RATE_FEATURES, "vim.fragmentShadingRate.pipeline", VkPhysicalDeviceFragmentShadingRateFeaturesKHR.PIPELINEFRAGMENTSHADINGRATE));
        features.add(new VulkanFeature(PRESENT_ID_FEATURES, "vim.presentId", VkPhysicalDevicePresentIdFeaturesKHR.PRESENTID));
        features.add(new VulkanFeature(PRESENT_WAIT_FEATURES, "vim.presentWait", VkPhysicalDevicePresentWaitFeaturesKHR.PRESENTWAIT));
        return features;
    }
    
    public static boolean supportsRequiredApi(VkPhysicalDevice physicalDevice) {
        return apiVersion(physicalDevice) >= VK14.VK_API_VERSION_1_4;
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
