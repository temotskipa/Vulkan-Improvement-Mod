package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import com.temotskipa.vulkanimprovement.client.vulkan.PresentPacingController;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanGpuSurface.class)
public abstract class VulkanGpuSurfaceMixin {
    @Shadow
    @Final
    private VulkanDevice device;
    
    @Shadow
    private long swapchain;
    
    @Inject(method = "present", at = @At("HEAD"))
    private void vim$beforePresent(CallbackInfo ci) {
        PresentPacingController.get().beforePresent();
    }
    
    @Inject(method = "present", at = @At("RETURN"))
    private void vim$afterPresent(CallbackInfo ci) {
        PresentPacingController.get().afterPresent();
    }
    
    @Redirect(
            method = "present",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkQueuePresentKHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkPresentInfoKHR;)I", remap = false)
    )
    private int vim$presentWithPresentIdAndWait(VkQueue queue, VkPresentInfoKHR pPresentInfo) {
        return PresentPacingController.get().present(this.device, queue, this.swapchain, pPresentInfo);
    }
}
