package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.FramebufferCache;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.FramebufferCacheOwner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases the framebuffers kept with a view as their attachment when the view closes, before
 * Minecraft queues the view itself for destruction ({@link FramebufferCache}).
 */
@Mixin(VulkanGpuTextureView.class)
public abstract class VulkanGpuTextureViewMixin {
    @Shadow
    @Final
    private VulkanDevice device;
    
    @Shadow
    @Final
    private long vkImageView;
    
    @Shadow
    private boolean closed;
    
    @Inject(method = "close", at = @At("HEAD"))
    private void vim$releaseFramebuffers(CallbackInfo ci) {
        if (!this.closed) {
            ((FramebufferCacheOwner) this.device.createCommandEncoder()).vim$framebuffers().release(this.vkImageView);
        }
    }
}