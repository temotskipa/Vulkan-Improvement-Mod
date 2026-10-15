package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.Destroyable;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.FramebufferCache;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.LongBuffer;

/**
 * Gives a render pass the framebuffer it had in an earlier frame, instead of creating one for every
 * render pass and destroying it two submits later ({@link FramebufferCache}).
 */
@Mixin(VulkanCommandEncoder.class)
public abstract class VulkanCommandEncoderFramebufferMixin implements FramebufferCache.Owner {
    @Shadow
    @Final
    private VulkanDevice device;
    
    @Unique
    private final FramebufferCache vim$framebuffers = new FramebufferCache(this::vim$destroyFramebufferLater);
    
    /**
     * Whether the framebuffer of the render pass being created is kept, so that its destruction is
     * not queued.
     */
    @Unique
    private boolean vim$framebufferKept;
    
    @Shadow
    public abstract void queueForDestroy(Destroyable destroyable);
    
    @Override
    public FramebufferCache vim$framebuffers() {
        return vim$framebuffers;
    }
    
    @WrapOperation(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCreateFramebuffer(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkFramebufferCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private int vim$keepFramebuffer(VkDevice vkDevice, VkFramebufferCreateInfo createInfo, @Nullable VkAllocationCallbacks allocator, LongBuffer framebuffer, Operation<Integer> original) {
        vim$framebufferKept = false;
        if (!TerrainRendererDebugConfig.keepFramebuffersEnabled()) {
            vim$framebuffers.releaseAll();
            return original.call(vkDevice, createInfo, allocator, framebuffer);
        }
        long kept = vim$framebuffers.find(createInfo);
        if (kept == VK10.VK_NULL_HANDLE) {
            int result = original.call(vkDevice, createInfo, allocator, framebuffer);
            if (result != VK10.VK_SUCCESS) {
                return result;
            }
            kept = framebuffer.get(framebuffer.position());
            vim$framebuffers.keep(createInfo, kept);
        }
        framebuffer.put(framebuffer.position(), kept);
        vim$framebufferKept = true;
        return VK10.VK_SUCCESS;
    }
    
    @WrapOperation(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanCommandEncoder;queueForDestroy(Lcom/mojang/renderpearl/backend/vulkan/Destroyable;)V"))
    private void vim$keepKeptFramebuffer(VulkanCommandEncoder encoder, Destroyable destroyable, Operation<Void> original) {
        if (vim$framebufferKept) {
            vim$framebufferKept = false;
            return;
        }
        original.call(encoder, destroyable);
    }
    
    /**
     * Queues the kept framebuffers for destruction before Minecraft waits for the queue to idle and
     * empties its destruction queue.
     */
    @Inject(method = "destroy", at = @At("HEAD"))
    private void vim$releaseFramebuffers(CallbackInfo ci) {
        vim$framebuffers.releaseAll();
    }
    
    @Unique
    private void vim$destroyFramebufferLater(long framebuffer) {
        VkDevice vkDevice = this.device.vkDevice();
        queueForDestroy(() -> VK10.vkDestroyFramebuffer(vkDevice, framebuffer, null));
    }
}