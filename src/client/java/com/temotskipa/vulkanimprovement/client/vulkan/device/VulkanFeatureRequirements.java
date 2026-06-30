package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.Collections;
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

    public record RequirementGroup(String id, RequirementScope scope, boolean startupRequired, String disabledReason,
                                   Set<String> deviceExtensions, Set<VulkanFeature> deviceFeatures) {
        public RequirementGroup {
            deviceExtensions = Collections.unmodifiableSet(new LinkedHashSet<>(deviceExtensions));
            deviceFeatures = Collections.unmodifiableSet(new LinkedHashSet<>(deviceFeatures));
        }
    }

    private VulkanFeatureRequirements() {
    }

    public static List<RequirementGroup> requirementGroups() {
        return requirementGroups(!TerrainRendererDebugConfig.validationDescriptorBufferOnly());
    }

    public static Set<String> requiredDeviceExtensions() {
        return requiredDeviceExtensions(!TerrainRendererDebugConfig.validationDescriptorBufferOnly());
    }

    static Set<String> requiredDeviceExtensions(boolean requireDescriptorHeap) {
        Set<String> extensions = new LinkedHashSet<>();
        for (RequirementGroup group : startupRequiredRequirementGroups(requireDescriptorHeap)) {
            extensions.addAll(group.deviceExtensions());
        }
        return extensions;
    }

    public static Set<VulkanFeature> requiredDeviceFeatures() {
        return requiredDeviceFeatures(!TerrainRendererDebugConfig.validationDescriptorBufferOnly());
    }

    static Set<VulkanFeature> requiredDeviceFeatures(boolean requireDescriptorHeap) {
        Set<VulkanFeature> features = new LinkedHashSet<>();
        for (RequirementGroup group : startupRequiredRequirementGroups(requireDescriptorHeap)) {
            features.addAll(group.deviceFeatures());
        }
        return features;
    }

    static List<RequirementGroup> requirementGroups(boolean requireDescriptorHeap) {
        List<RequirementGroup> groups = new ArrayList<>();
        groups.add(new RequirementGroup(
                "base-vulkan-api",
                RequirementScope.BASE_MINECRAFT,
                true,
                "requires Vulkan 1.4 device API",
                Set.of(),
                Set.of()));

        Set<String> hardExtensions = hardRenderingExtensions();
        Set<VulkanFeature> hardFeatures = hardRenderingFeatures();
        groups.add(new RequirementGroup(
                "vim-hard-rendering",
                RequirementScope.VIM_HARD,
                true,
                "required by mesh terrain, descriptor-buffer resources, present pacing, and diagnostics",
                hardExtensions,
                hardFeatures));

        groups.add(new RequirementGroup(
                "descriptor-heap-path",
                RequirementScope.REQUIRED_IF_ENABLED,
                requireDescriptorHeap,
                requireDescriptorHeap ? "required by descriptor-buffer plus heap path" : "disabled by vim.validationDescriptorBufferOnly",
                Set.of(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME),
                Set.of(new VulkanFeature(DESCRIPTOR_HEAP_FEATURES, "vim.descriptorHeap", VkPhysicalDeviceDescriptorHeapFeaturesEXT.DESCRIPTORHEAP))));

        groups.add(new RequirementGroup(
                "optional-acceleration",
                RequirementScope.OPTIONAL_ACCELERATION,
                false,
                "optional acceleration path is unavailable when the extension is not enabled by Minecraft/Vulkan",
                optionalAccelerationExtensionSet(),
                Set.of()));

        groups.add(new RequirementGroup(
                "rt-pt-readiness",
                RequirementScope.RT_PT_READINESS,
                false,
                "RT/PT readiness path is unavailable until all required ray-tracing extensions are enabled by Minecraft/Vulkan",
                rtPtReadinessExtensionsSet(),
                Set.of()));

        return List.copyOf(groups);
    }

    private static Set<String> hardRenderingExtensions() {
        Set<String> hardExtensions = new LinkedHashSet<>();
        hardExtensions.add(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);
        hardExtensions.add(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME);
        hardExtensions.add(KHRFragmentShadingRate.VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME);
        hardExtensions.add(KHRDynamicRenderingLocalRead.VK_KHR_DYNAMIC_RENDERING_LOCAL_READ_EXTENSION_NAME);
        hardExtensions.add(KHRMaintenance4.VK_KHR_MAINTENANCE_4_EXTENSION_NAME);
        hardExtensions.add(KHRMaintenance5.VK_KHR_MAINTENANCE_5_EXTENSION_NAME);
        hardExtensions.add(KHRMaintenance6.VK_KHR_MAINTENANCE_6_EXTENSION_NAME);
        hardExtensions.add(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME);
        hardExtensions.add(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME);
        return hardExtensions;
    }

    private static Set<String> optionalAccelerationExtensionSet() {
        Set<String> optionalAccelerationExtensions = new LinkedHashSet<>();
        optionalAccelerationExtensions.add(EXTDeviceGeneratedCommands.VK_EXT_DEVICE_GENERATED_COMMANDS_EXTENSION_NAME);
        optionalAccelerationExtensions.add(EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME);
        optionalAccelerationExtensions.add(EXTShaderObject.VK_EXT_SHADER_OBJECT_EXTENSION_NAME);
        optionalAccelerationExtensions.add(EXTGraphicsPipelineLibrary.VK_EXT_GRAPHICS_PIPELINE_LIBRARY_EXTENSION_NAME);
        optionalAccelerationExtensions.add(KHRPipelineLibrary.VK_KHR_PIPELINE_LIBRARY_EXTENSION_NAME);
        optionalAccelerationExtensions.add(EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME);
        optionalAccelerationExtensions.add(EXTMemoryPriority.VK_EXT_MEMORY_PRIORITY_EXTENSION_NAME);
        optionalAccelerationExtensions.add(EXTPageableDeviceLocalMemory.VK_EXT_PAGEABLE_DEVICE_LOCAL_MEMORY_EXTENSION_NAME);
        optionalAccelerationExtensions.add(EXTHostImageCopy.VK_EXT_HOST_IMAGE_COPY_EXTENSION_NAME);
        return optionalAccelerationExtensions;
    }

    private static Set<String> rtPtReadinessExtensionsSet() {
        Set<String> rtPtExtensions = new LinkedHashSet<>();
        rtPtExtensions.add(KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME);
        rtPtExtensions.add(KHRRayQuery.VK_KHR_RAY_QUERY_EXTENSION_NAME);
        rtPtExtensions.add(KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME);
        rtPtExtensions.add(KHRRayTracingMaintenance1.VK_KHR_RAY_TRACING_MAINTENANCE_1_EXTENSION_NAME);
        rtPtExtensions.add(KHRRayTracingPositionFetch.VK_KHR_RAY_TRACING_POSITION_FETCH_EXTENSION_NAME);
        rtPtExtensions.add(EXTOpacityMicromap.VK_EXT_OPACITY_MICROMAP_EXTENSION_NAME);
        rtPtExtensions.add(EXTRayTracingInvocationReorder.VK_EXT_RAY_TRACING_INVOCATION_REORDER_EXTENSION_NAME);
        return rtPtExtensions;
    }

    private static Set<VulkanFeature> hardRenderingFeatures() {
        Set<VulkanFeature> hardFeatures = new LinkedHashSet<>();
        hardFeatures.add(new VulkanFeature(PHYSICAL_DEVICE_FEATURES, "vim.shaderInt64", VkPhysicalDeviceFeatures.SHADERINT64));
        hardFeatures.add(new VulkanFeature(MAINTENANCE_4_FEATURES, "vim.vulkan13.maintenance4", VkPhysicalDeviceMaintenance4Features.MAINTENANCE4));
        hardFeatures.add(new VulkanFeature(SHADER_DEMOTE_FEATURES, "vim.vulkan13.shaderDemoteToHelperInvocation", VkPhysicalDeviceShaderDemoteToHelperInvocationFeatures.SHADERDEMOTETOHELPERINVOCATION));
        hardFeatures.add(new VulkanFeature(DYNAMIC_RENDERING_LOCAL_READ_FEATURES, "vim.vulkan14.dynamicRenderingLocalRead", VkPhysicalDeviceDynamicRenderingLocalReadFeatures.DYNAMICRENDERINGLOCALREAD));
        hardFeatures.add(new VulkanFeature(MAINTENANCE_5_FEATURES, "vim.vulkan14.maintenance5", VkPhysicalDeviceMaintenance5Features.MAINTENANCE5));
        hardFeatures.add(new VulkanFeature(MAINTENANCE_6_FEATURES, "vim.vulkan14.maintenance6", VkPhysicalDeviceMaintenance6Features.MAINTENANCE6));
        hardFeatures.add(new VulkanFeature(VULKAN_12_FEATURES, "vim.vulkan12.bufferDeviceAddress", VkPhysicalDeviceVulkan12Features.BUFFERDEVICEADDRESS));
        hardFeatures.add(new VulkanFeature(VULKAN_12_FEATURES, "vim.vulkan12.scalarBlockLayout", VkPhysicalDeviceVulkan12Features.SCALARBLOCKLAYOUT));
        hardFeatures.add(new VulkanFeature(MESH_SHADER_FEATURES, "vim.mesh.taskShader", VkPhysicalDeviceMeshShaderFeaturesEXT.TASKSHADER));
        hardFeatures.add(new VulkanFeature(MESH_SHADER_FEATURES, "vim.mesh.meshShader", VkPhysicalDeviceMeshShaderFeaturesEXT.MESHSHADER));
        hardFeatures.add(new VulkanFeature(DESCRIPTOR_BUFFER_FEATURES, "vim.descriptorBuffer", VkPhysicalDeviceDescriptorBufferFeaturesEXT.DESCRIPTORBUFFER));
        hardFeatures.add(new VulkanFeature(FRAGMENT_SHADING_RATE_FEATURES, "vim.fragmentShadingRate.pipeline", VkPhysicalDeviceFragmentShadingRateFeaturesKHR.PIPELINEFRAGMENTSHADINGRATE));
        hardFeatures.add(new VulkanFeature(PRESENT_ID_FEATURES, "vim.presentId", VkPhysicalDevicePresentIdFeaturesKHR.PRESENTID));
        hardFeatures.add(new VulkanFeature(PRESENT_WAIT_FEATURES, "vim.presentWait", VkPhysicalDevicePresentWaitFeaturesKHR.PRESENTWAIT));
        return hardFeatures;
    }

    static List<RequirementGroup> startupRequiredRequirementGroups(boolean requireDescriptorHeap) {
        return requirementGroups(requireDescriptorHeap).stream()
                .filter(RequirementGroup::startupRequired)
                .toList();
    }

    public static Set<String> optionalAccelerationExtensions() {
        return extensionsByScope(RequirementScope.OPTIONAL_ACCELERATION);
    }

    public static Set<String> rtPtReadinessExtensions() {
        return extensionsByScope(RequirementScope.RT_PT_READINESS);
    }

    private static Set<String> extensionsByScope(RequirementScope scope) {
        Set<String> extensions = new LinkedHashSet<>();
        for (RequirementGroup group : requirementGroups()) {
            if (group.scope() == scope) {
                extensions.addAll(group.deviceExtensions());
            }
        }
        return extensions;
    }

    @SuppressWarnings("unused")
    public static Set<VulkanFeature> requiredIfEnabledDeviceFeatures() {
        Set<VulkanFeature> features = new LinkedHashSet<>();
        for (RequirementGroup group : requirementGroups()) {
            if (group.scope() == RequirementScope.REQUIRED_IF_ENABLED) {
                features.addAll(group.deviceFeatures());
            }
        }
        return features;
    }

    @SuppressWarnings("unused")
    public static Set<String> requiredIfEnabledDeviceExtensions() {
        return extensionsByScope(RequirementScope.REQUIRED_IF_ENABLED);
    }

    @SuppressWarnings("unused")
    public static Set<VulkanFeature> hardDeviceFeatures() {
        Set<VulkanFeature> features = new LinkedHashSet<>();
        for (RequirementGroup group : requirementGroups()) {
            if (group.scope() == RequirementScope.VIM_HARD) {
                features.addAll(group.deviceFeatures());
            }
        }
        return features;
    }

    @SuppressWarnings("unused")
    public static Set<String> hardDeviceExtensions() {
        return extensionsByScope(RequirementScope.VIM_HARD);
    }

    public static Set<String> baseCapabilityNames() {
        Set<String> capabilities = new LinkedHashSet<>();
        for (RequirementGroup group : requirementGroups()) {
            if (group.scope() == RequirementScope.BASE_MINECRAFT) {
                capabilities.add(VULKAN_14_CAPABILITY);
            }
        }
        return capabilities;
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
