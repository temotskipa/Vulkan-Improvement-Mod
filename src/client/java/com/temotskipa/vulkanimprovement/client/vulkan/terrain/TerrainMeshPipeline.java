package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import org.lwjgl.vulkan.VK12;

record TerrainMeshPipeline(long handle, long layout, long textureDescriptorSetLayout, TerrainTextureDescriptorLayout textureDescriptorLayout) {
    void destroy(VulkanDevice device) {
        if (this.handle != 0L) {
            VK12.vkDestroyPipeline(device.vkDevice(), this.handle, null);
        }
        if (this.layout != 0L) {
            VK12.vkDestroyPipelineLayout(device.vkDevice(), this.layout, null);
        }
        if (this.textureDescriptorSetLayout != 0L) {
            VK12.vkDestroyDescriptorSetLayout(device.vkDevice(), this.textureDescriptorSetLayout, null);
        }
    }
}