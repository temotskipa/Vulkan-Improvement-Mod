package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VulkanFeatureRequirements {
    public static final String VULKAN_14_CAPABILITY = "Vulkan Improvement Mod requires VULKAN_CORE_1_4; remove the mod to use vanilla renderer";

    private static final VulkanPNextStruct PHYSICAL_DEVICE_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceFeatures2.class);
    private static final VulkanPNextStruct VULKAN_12_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceVulkan12Features.class);
    private static final VulkanPNextStruct MAINTENANCE_4_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceMaintenance4Features.class);
    private static final VulkanPNextStruct SHADER_DEMOTE_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceShaderDemoteToHelperInvocationFeatures.class);
    private static final VulkanPNextStruct DYNAMIC_RENDERING_LOCAL_READ_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceDynamicRenderingLocalReadFeatures.class);
    private static final VulkanPNextStruct MAINTENANCE_5_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceMaintenance5Features.class);
    private static final VulkanPNextStruct MAINTENANCE_6_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceMaintenance6Features.class);
    private static final VulkanPNextStruct MESH_SHADER_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceMeshShaderFeaturesEXT.class);
    private static final VulkanPNextStruct DESCRIPTOR_HEAP_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceDescriptorHeapFeaturesEXT.class);
    private static final VulkanPNextStruct DESCRIPTOR_BUFFER_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceDescriptorBufferFeaturesEXT.class);
    private static final VulkanPNextStruct FRAGMENT_SHADING_RATE_FEATURES = new VulkanPNextStruct(VkPhysicalDeviceFragmentShadingRateFeaturesKHR.class);
    private static final VulkanPNextStruct PRESENT_ID_FEATURES = new VulkanPNextStruct(VkPhysicalDevicePresentIdFeaturesKHR.class);
    private static final VulkanPNextStruct PRESENT_WAIT_FEATURES = new VulkanPNextStruct(VkPhysicalDevicePresentWaitFeaturesKHR.class);

    /**
     * The name under which Minecraft logs and reports each of the mod's feature sets.
     */
    public static final String FEATURE_SET_PREFIX = "Vulkan Improvement ";

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
                Set.of(new VulkanFeature(DESCRIPTOR_HEAP_FEATURES, "descriptorHeap"))));

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
        // A feature is named by its field in the Vulkan struct, as Minecraft names its own, so
        // the two agree when both ask for the same one.
        hardFeatures.add(new VulkanFeature(PHYSICAL_DEVICE_FEATURES, "shaderInt64"));
        hardFeatures.add(new VulkanFeature(MAINTENANCE_4_FEATURES, "maintenance4"));
        hardFeatures.add(new VulkanFeature(SHADER_DEMOTE_FEATURES, "shaderDemoteToHelperInvocation"));
        hardFeatures.add(new VulkanFeature(DYNAMIC_RENDERING_LOCAL_READ_FEATURES, "dynamicRenderingLocalRead"));
        hardFeatures.add(new VulkanFeature(MAINTENANCE_5_FEATURES, "maintenance5"));
        hardFeatures.add(new VulkanFeature(MAINTENANCE_6_FEATURES, "maintenance6"));
        hardFeatures.add(new VulkanFeature(VULKAN_12_FEATURES, "bufferDeviceAddress"));
        hardFeatures.add(new VulkanFeature(VULKAN_12_FEATURES, "scalarBlockLayout"));
        hardFeatures.add(new VulkanFeature(MESH_SHADER_FEATURES, "taskShader"));
        hardFeatures.add(new VulkanFeature(MESH_SHADER_FEATURES, "meshShader"));
        hardFeatures.add(new VulkanFeature(DESCRIPTOR_BUFFER_FEATURES, "descriptorBuffer"));
        hardFeatures.add(new VulkanFeature(FRAGMENT_SHADING_RATE_FEATURES, "pipelineFragmentShadingRate"));
        hardFeatures.add(new VulkanFeature(PRESENT_ID_FEATURES, "presentId"));
        hardFeatures.add(new VulkanFeature(PRESENT_WAIT_FEATURES, "presentWait"));
        return hardFeatures;
    }

    /**
     * The mod's startup requirements as Minecraft feature sets, one per requirement group that
     * names an extension or a feature. Minecraft adds them to its own required sets, so device
     * selection, the missing-capability report and device creation all take them into account.
     */
    public static List<FeatureSet> requiredFeatureSets() {
        return requiredFeatureSets(!TerrainRendererDebugConfig.validationDescriptorBufferOnly());
    }

    static List<FeatureSet> requiredFeatureSets(boolean requireDescriptorHeap) {
        List<FeatureSet> featureSets = new ArrayList<>();
        for (RequirementGroup group : startupRequiredRequirementGroups(requireDescriptorHeap)) {
            if (!group.deviceExtensions().isEmpty() || !group.deviceFeatures().isEmpty()) {
                featureSets.add(new FeatureSet(FEATURE_SET_PREFIX + "[" + group.id() + "]", group.deviceExtensions(), group.deviceFeatures()));
            }
        }
        return List.copyOf(featureSets);
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
