package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

final class TerrainGpuBuffer implements Destroyable, AutoCloseable {
    private final VulkanDevice device;
    private final String label;
    private final long vkBuffer;
    private final long allocation;
    private final long size;
    private final long deviceAddress;
    private final long mappedAddress;
    private boolean closed;
    private boolean destroyed;
    
    TerrainGpuBuffer(VulkanDevice device, String label, long size, int usage, boolean hostVisible) {
        this.device = device;
        this.label = label;
        this.size = size;
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default();
            bufferInfo.size(size);
            bufferInfo.usage(usage | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
            bufferInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
            allocationInfo.usage(hostVisible ? Vma.VMA_MEMORY_USAGE_AUTO_PREFER_HOST : Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            if (hostVisible) {
                allocationInfo.flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT);
            }
            
            LongBuffer bufferPtr = stack.callocLong(1);
            PointerBuffer allocationPtr = stack.callocPointer(1);
            VmaAllocationInfo allocationResult = VmaAllocationInfo.calloc(stack);
            VulkanUtils.crashIfFailure(Vma.vmaCreateBuffer(device.vma(), bufferInfo, allocationInfo, bufferPtr, allocationPtr, allocationResult), "Failed to allocate Vulkan Improvement terrain buffer " + label);
            this.vkBuffer = bufferPtr.get(0);
            this.allocation = allocationPtr.get(0);
            this.mappedAddress = allocationResult.pMappedData();
            this.deviceAddress = queryDeviceAddress(stack, device, this.vkBuffer);
            device.instance().debug().setObjectName(device.vkDevice(), VK10.VK_OBJECT_TYPE_BUFFER, this.vkBuffer, label);
        }
    }
    
    private static long queryDeviceAddress(MemoryStack stack, VulkanDevice device, long buffer) {
        VkBufferDeviceAddressInfo addressInfo = VkBufferDeviceAddressInfo.calloc(stack).sType$Default();
        addressInfo.buffer(buffer);
        return VK12.vkGetBufferDeviceAddress(device.vkDevice(), addressInfo);
    }
    
    long vkBuffer() {
        return this.vkBuffer;
    }
    
    String label() {
        return this.label;
    }
    
    String vkBufferHex() {
        return "0x" + Long.toUnsignedString(this.vkBuffer, 16);
    }
    
    boolean closed() {
        return this.closed;
    }
    
    boolean destroyed() {
        return this.destroyed;
    }
    
    long size() {
        return this.size;
    }
    
    long deviceAddress() {
        return this.deviceAddress;
    }
    
    boolean hostVisible() {
        return this.mappedAddress != 0L;
    }
    
    ByteBuffer mappedByteBuffer() {
        if (this.mappedAddress == 0L) {
            throw new IllegalStateException(this.label + " is not host visible");
        }
        if (this.size > Integer.MAX_VALUE) {
            throw new IllegalStateException(this.label + " is too large to map as a Java ByteBuffer");
        }
        return MemoryUtil.memByteBuffer(this.mappedAddress, (int) this.size);
    }
    
    void flush() {
        if (this.mappedAddress != 0L) {
            Vma.vmaFlushAllocation(this.device.vma(), this.allocation, 0L, this.size);
        }
    }
    
    void invalidate() {
        if (this.mappedAddress != 0L) {
            Vma.vmaInvalidateAllocation(this.device.vma(), this.allocation, 0L, this.size);
        }
    }
    
    @Override
    public synchronized void close() {
        if (!this.closed) {
            this.closed = true;
            this.device.createCommandEncoder().queueForDestroy(this);
        }
    }
    
    synchronized void destroyNow() {
        this.closed = true;
        destroy();
    }
    
    @Override
    public synchronized void destroy() {
        if (!this.destroyed) {
            this.destroyed = true;
            Vma.vmaDestroyBuffer(this.device.vma(), this.vkBuffer, this.allocation);
        }
    }
}
