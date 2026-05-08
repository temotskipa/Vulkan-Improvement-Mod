package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.vulkan.VulkanInstance;
import org.lwjgl.vulkan.VK14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(VulkanInstance.class)
public abstract class VulkanInstanceMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkApplicationInfo;apiVersion(I)Lorg/lwjgl/vulkan/VkApplicationInfo;"),
            index = 0
    )
    private int vim$requestVulkan14Instance(int original) {
        return VK14.VK_API_VERSION_1_4;
    }
}
