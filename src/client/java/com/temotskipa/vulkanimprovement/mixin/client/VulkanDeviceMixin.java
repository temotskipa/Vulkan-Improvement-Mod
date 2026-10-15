package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanInstance;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.mojang.renderpearl.backend.vulkan.checkpoints.CheckpointExtension;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanDevice.class)
public final class VulkanDeviceMixin {
    @Shadow
    @Final
    private VkDevice vkDevice;
    
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanPhysicalDevice;close()V"))
    private void vim$captureCapabilities(VulkanInstance instance, VulkanPhysicalDevice physicalDevice, FeatureSet enabledFeatureSet, VkDevice vkDevice, long vma, CheckpointExtension checkpointExtension, CallbackInfo ci) {
        VulkanDevice device = (VulkanDevice) (Object) this;
        MeshTerrainRenderer.get().configure(device, VulkanImprovementCapabilities.capture(physicalDevice, enabledFeatureSet.extensions()));
        ModelGpuVertices.get().configure(device);
    }
    
    @Inject(method = "close", at = @At("HEAD"))
    private void vim$shutdownTerrainRenderer(CallbackInfo ci) {
        VK12.vkDeviceWaitIdle(this.vkDevice);
        MeshTerrainRenderer.get().shutdownNow();
        ModelGpuVertices.get().shutdownNow();
    }
}