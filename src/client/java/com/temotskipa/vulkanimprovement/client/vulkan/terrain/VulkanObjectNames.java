package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.VulkanDebug;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanInstance;

/**
 * Names the mod's Vulkan objects for debuggers and validation messages, through the debug
 * utilities of the instance Minecraft created the device from.
 */
final class VulkanObjectNames {
    private VulkanObjectNames() {
    }
    
    static void name(VulkanDevice device, int objectType, long handle, String name) {
        debug(device.instance()).setObjectName(device.vkDevice(), objectType, handle, name);
    }
    
    /**
     * The instance's debug utilities. The instance is Minecraft's, which closes it when the game
     * ends.
     */
    private static VulkanDebug debug(VulkanInstance instance) {
        return instance.debug();
    }
}