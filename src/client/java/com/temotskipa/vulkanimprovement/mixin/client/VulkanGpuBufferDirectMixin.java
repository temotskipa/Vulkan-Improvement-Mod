package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage.Addressable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives Minecraft's Vulkan buffers their device address, asked of the driver once. Only a buffer
 * made with {@link ShaderStorage#USAGE_SHADER_STORAGE} has one, which {@link ShaderStorage#address}
 * checks before it asks.
 */
@Mixin(VulkanGpuBuffer.Direct.class)
public abstract class VulkanGpuBufferDirectMixin extends VulkanGpuBuffer implements Addressable {
    @Shadow
    @Final
    protected VulkanDevice device;
    
    @Unique
    private long vim$deviceAddress;
    
    private VulkanGpuBufferDirectMixin() {
        super(0L, 0, 0L);
    }
    
    @Override
    public long vim$deviceAddress() {
        if (this.vim$deviceAddress == 0L) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferDeviceAddressInfo addressInfo = VkBufferDeviceAddressInfo.calloc(stack).sType$Default().buffer(vkBuffer());
                this.vim$deviceAddress = VK12.vkGetBufferDeviceAddress(this.device.vkDevice(), addressInfo);
            }
        }
        return this.vim$deviceAddress;
    }
}