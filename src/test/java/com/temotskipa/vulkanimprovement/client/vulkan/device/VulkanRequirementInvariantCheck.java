package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import org.lwjgl.vulkan.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class VulkanRequirementInvariantCheck {
    private VulkanRequirementInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) {
        checkVersionString();
        checkRequirementGroups();
        checkRequiredExtensions();
        checkRequiredFeatures();
        checkDescriptorBufferOnlyAdapter();
        checkOptionalAndRtGroupsExcludedFromStartupAdapter();
    }

    private static void checkVersionString() {
        require("1.4.0".equals(VulkanFeatureRequirements.versionString(VK14.VK_API_VERSION_1_4)), "Vulkan 1.4 version string must be stable");
        require("1.4.12".equals(VulkanFeatureRequirements.versionString(version(1, 4, 12))), "Vulkan patch version string must be stable");
        require("2.0.3".equals(VulkanFeatureRequirements.versionString(version(2, 0, 3))), "Vulkan major/minor version string must be stable");
    }

    private static void checkRequiredExtensions() {
        Set<String> extensions = VulkanFeatureRequirements.requiredDeviceExtensions();
        require(extensions.contains(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME), "mesh shader extension must be required");
        require(extensions.contains(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME), "descriptor buffer extension must be required");
        require(extensions.contains(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME), "descriptor heap extension must be required by default");
        require(extensions.contains(KHRFragmentShadingRate.VK_KHR_FRAGMENT_SHADING_RATE_EXTENSION_NAME), "fragment shading-rate extension must be required");
        require(extensions.contains(KHRDynamicRenderingLocalRead.VK_KHR_DYNAMIC_RENDERING_LOCAL_READ_EXTENSION_NAME), "dynamic rendering local read extension must be required");
        require(extensions.contains(KHRMaintenance4.VK_KHR_MAINTENANCE_4_EXTENSION_NAME), "maintenance4 extension must be required");
        require(extensions.contains(KHRMaintenance5.VK_KHR_MAINTENANCE_5_EXTENSION_NAME), "maintenance5 extension must be required");
        require(extensions.contains(KHRMaintenance6.VK_KHR_MAINTENANCE_6_EXTENSION_NAME), "maintenance6 extension must be required");
        require(extensions.contains(KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME), "present id extension must be required");
        require(extensions.contains(KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME), "present wait extension must be required");
    }

    private static void checkRequirementGroups() {
        List<VulkanFeatureRequirements.RequirementGroup> groups = VulkanFeatureRequirements.requirementGroups(true);
        require(groups.size() == 5, "Vulkan requirements must stay split into five plan-aligned groups");

        VulkanFeatureRequirements.RequirementGroup base = group(groups, "base-vulkan-api");
        require(base.scope() == RequirementScope.BASE_MINECRAFT, "base group must represent Minecraft/Vulkan baseline");
        require(base.startupRequired(), "base Vulkan API group must be startup-required");
        require(base.deviceExtensions().isEmpty(), "base Vulkan API group must not pretend API version is a device extension");
        require(base.deviceFeatures().isEmpty(), "base Vulkan API group must not pretend API version is a feature struct");
        require(VulkanFeatureRequirements.baseCapabilityNames().contains(VulkanFeatureRequirements.VULKAN_14_CAPABILITY), "base capabilities must include Vulkan 1.4 requirement label");

        VulkanFeatureRequirements.RequirementGroup hard = group(groups, "vim-hard-rendering");
        require(hard.scope() == RequirementScope.VIM_HARD, "hard group must represent VIM startup requirements");
        require(hard.startupRequired(), "hard VIM requirements must be startup-required");
        require(hard.deviceExtensions().contains(EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME), "hard group must include mesh shader extension");
        require(hard.deviceExtensions().contains(EXTDescriptorBuffer.VK_EXT_DESCRIPTOR_BUFFER_EXTENSION_NAME), "hard group must include descriptor buffer extension");
        require(featureNames(hard.deviceFeatures()).contains("vim.mesh.taskShader"), "hard group must include task shader feature");
        require(featureNames(hard.deviceFeatures()).contains("vim.descriptorBuffer"), "hard group must include descriptor buffer feature");

        VulkanFeatureRequirements.RequirementGroup descriptorHeap = group(groups, "descriptor-heap-path");
        require(descriptorHeap.scope() == RequirementScope.REQUIRED_IF_ENABLED, "descriptor heap group must be required-if-enabled");
        require(descriptorHeap.startupRequired(), "descriptor heap group must be required in the default path");
        require(descriptorHeap.deviceExtensions().contains(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME), "descriptor heap group must include descriptor heap extension");
        require(featureNames(descriptorHeap.deviceFeatures()).contains("vim.descriptorHeap"), "descriptor heap group must include descriptor heap feature");

        VulkanFeatureRequirements.RequirementGroup optional = group(groups, "optional-acceleration");
        require(optional.scope() == RequirementScope.OPTIONAL_ACCELERATION, "optional group must represent acceleration extensions");
        require(!optional.startupRequired(), "optional acceleration group must not be startup-required");
        require(optional.deviceExtensions().contains(EXTDeviceGeneratedCommands.VK_EXT_DEVICE_GENERATED_COMMANDS_EXTENSION_NAME), "optional group must include device-generated commands");
        require(optional.deviceExtensions().contains(EXTMultiDraw.VK_EXT_MULTI_DRAW_EXTENSION_NAME), "optional group must include multi-draw");

        VulkanFeatureRequirements.RequirementGroup rtPt = group(groups, "rt-pt-readiness");
        require(rtPt.scope() == RequirementScope.RT_PT_READINESS, "RT/PT group must represent ray-tracing readiness");
        require(!rtPt.startupRequired(), "RT/PT readiness group must not be startup-required");
        require(rtPt.deviceExtensions().contains(KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME), "RT/PT group must include acceleration structures");
        require(rtPt.deviceExtensions().contains(KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME), "RT/PT group must include ray-tracing pipeline");
    }

    private static void checkRequiredFeatures() {
        Set<String> featureNames = VulkanFeatureRequirements.requiredDeviceFeatures()
                .stream()
                .map(VulkanFeature::name)
                .collect(Collectors.toSet());

        require(featureNames.contains("vim.shaderInt64"), "shaderInt64 feature must be required");
        require(featureNames.contains("vim.vulkan12.bufferDeviceAddress"), "buffer device address feature must be required");
        require(featureNames.contains("vim.vulkan12.scalarBlockLayout"), "scalar block layout feature must be required");
        require(featureNames.contains("vim.vulkan13.maintenance4"), "maintenance4 feature must be required");
        require(featureNames.contains("vim.vulkan13.shaderDemoteToHelperInvocation"), "shader demote feature must be required");
        require(featureNames.contains("vim.vulkan14.dynamicRenderingLocalRead"), "dynamic rendering local read feature must be required");
        require(featureNames.contains("vim.vulkan14.maintenance5"), "maintenance5 feature must be required");
        require(featureNames.contains("vim.vulkan14.maintenance6"), "maintenance6 feature must be required");
        require(featureNames.contains("vim.mesh.taskShader"), "task shader feature must be required");
        require(featureNames.contains("vim.mesh.meshShader"), "mesh shader feature must be required");
        require(featureNames.contains("vim.descriptorHeap"), "descriptor heap feature must be required by default");
        require(featureNames.contains("vim.descriptorBuffer"), "descriptor buffer feature must be required");
        require(featureNames.contains("vim.fragmentShadingRate.pipeline"), "pipeline fragment shading-rate feature must be required");
        require(featureNames.contains("vim.presentId"), "present id feature must be required");
        require(featureNames.contains("vim.presentWait"), "present wait feature must be required");
    }

    private static void checkDescriptorBufferOnlyAdapter() {
        Set<String> defaultExtensions = VulkanFeatureRequirements.requiredDeviceExtensions(true);
        Set<String> validationExtensions = VulkanFeatureRequirements.requiredDeviceExtensions(false);
        require(defaultExtensions.contains(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME), "default adapter must require descriptor heap extension");
        require(!validationExtensions.contains(EXTDescriptorHeap.VK_EXT_DESCRIPTOR_HEAP_EXTENSION_NAME), "descriptor-buffer-only adapter must not require descriptor heap extension");

        Set<String> defaultFeatures = featureNames(VulkanFeatureRequirements.requiredDeviceFeatures(true));
        Set<String> validationFeatures = featureNames(VulkanFeatureRequirements.requiredDeviceFeatures(false));
        require(defaultFeatures.contains("vim.descriptorHeap"), "default adapter must require descriptor heap feature");
        require(!validationFeatures.contains("vim.descriptorHeap"), "descriptor-buffer-only adapter must not require descriptor heap feature");

        VulkanFeatureRequirements.RequirementGroup descriptorHeap = group(VulkanFeatureRequirements.requirementGroups(false), "descriptor-heap-path");
        require(!descriptorHeap.startupRequired(), "descriptor-buffer-only group state must mark descriptor heap as not startup-required");
        require("disabled by vim.validationDescriptorBufferOnly".equals(descriptorHeap.disabledReason()), "descriptor-buffer-only group state must explain why descriptor heap is disabled");
    }

    private static void checkOptionalAndRtGroupsExcludedFromStartupAdapter() {
        Set<String> requiredExtensions = VulkanFeatureRequirements.requiredDeviceExtensions(true);
        for (String extension : VulkanFeatureRequirements.optionalAccelerationExtensions()) {
            require(!requiredExtensions.contains(extension), "optional acceleration extension must not be a startup requirement: " + extension);
        }
        for (String extension : VulkanFeatureRequirements.rtPtReadinessExtensions()) {
            require(!requiredExtensions.contains(extension), "RT/PT readiness extension must not be a startup requirement: " + extension);
        }
    }

    private static VulkanFeatureRequirements.RequirementGroup group(List<VulkanFeatureRequirements.RequirementGroup> groups, String id) {
        return groups.stream()
                .filter(group -> group.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Vulkan requirement group: " + id));
    }

    private static Set<String> featureNames(Set<VulkanFeature> features) {
        return features.stream()
                .map(VulkanFeature::name)
                .collect(Collectors.toSet());
    }

    private static int version(int major, int minor, int patch) {
        return major << 22 | minor << 12 | patch;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
