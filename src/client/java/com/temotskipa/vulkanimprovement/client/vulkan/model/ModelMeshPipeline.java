package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import org.lwjgl.vulkan.VK10;

/**
 * A built mesh pipeline and its mesh module.
 */
public record ModelMeshPipeline(long pipeline, long module, int pushConstantOffset) {
    public void destroy(VulkanDevice device) {
        VK10.vkDestroyPipeline(device.vkDevice(), this.pipeline, null);
        VK10.vkDestroyShaderModule(device.vkDevice(), this.module, null);
    }
}