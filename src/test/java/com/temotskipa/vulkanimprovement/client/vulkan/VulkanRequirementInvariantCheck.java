package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import org.lwjgl.vulkan.*;

import java.util.Set;
import java.util.stream.Collectors;

public final class VulkanRequirementInvariantCheck {
    private VulkanRequirementInvariantCheck() {
    }

    public static void main(String[] args) {
        checkVersionString();
        checkRequiredExtensions();
        checkRequiredFeatures();
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

    private static int version(int major, int minor, int patch) {
        return major << 22 | minor << 12 | patch;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
