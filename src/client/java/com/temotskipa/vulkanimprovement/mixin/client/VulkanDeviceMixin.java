package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanInstance;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.VulkanImprovementCapabilities;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(VulkanDevice.class)
public abstract class VulkanDeviceMixin {
    @Inject(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;close()V")
    )
    private void vim$captureCapabilities(
            ShaderSource defaultShaderSource,
            VulkanInstance instance,
            VulkanPhysicalDevice physicalDevice,
            Set<String> enabledDeviceExtensions,
            VkDevice vkDevice,
            long vma,
            CallbackInfo ci
    ) {
        MeshTerrainRenderer.get().configure((VulkanDevice) (Object) this, VulkanImprovementCapabilities.capture(physicalDevice, enabledDeviceExtensions));
    }
    
    @Inject(method = "close", at = @At("HEAD"))
    private void vim$shutdownTerrainRenderer(CallbackInfo ci) {
        MeshTerrainRenderer.get().shutdown();
    }
}
