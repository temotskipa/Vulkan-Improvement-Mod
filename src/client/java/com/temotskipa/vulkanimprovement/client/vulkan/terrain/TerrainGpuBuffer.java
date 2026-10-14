package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.Destroyable;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
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
import java.nio.IntBuffer;
import java.nio.LongBuffer;

final class TerrainGpuBuffer implements Destroyable, AutoCloseable {
    private final VulkanDevice device;
    private final String label;
    private final long vkBuffer;
    private final long allocation;
    private final long size;
    private final long deviceAddress;
    private final long mappedAddress;
    // Whether the CPU's writes reach the GPU, and the GPU's the CPU, without a flush or an
    // invalidation. A flush of a whole buffer otherwise costs the render thread every frame.
    private final boolean coherent;
    private boolean closed;
    private boolean destroyed;
    
    /**
     * Where a buffer's memory lives and how the CPU reaches it.
     */
    enum Placement {
        /**
         * Device memory the CPU cannot map; written by copies only.
         */
        DEVICE,
        /**
         * Device memory the CPU maps and writes in order, for what the GPU reads or updates every
         * frame. Where the device has no such memory, host memory the GPU reaches over the bus.
         */
        DEVICE_MAPPED,
        /**
         * Host memory the CPU reads and writes at random, for staging and read-back.
         */
        HOST
    }
    
    TerrainGpuBuffer(VulkanDevice device, String label, long size, int usage, Placement placement) {
        this.device = device;
        this.label = label;
        this.size = size;
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack).sType$Default();
            bufferInfo.size(size);
            bufferInfo.usage(usage | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
            bufferInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            
            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack);
            allocationInfo.usage(placement == Placement.HOST ? Vma.VMA_MEMORY_USAGE_AUTO_PREFER_HOST : Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            if (placement == Placement.HOST) {
                allocationInfo.flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT | Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT);
            }
            else if (placement == Placement.DEVICE_MAPPED) {
                allocationInfo.flags(Vma.VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT | Vma.VMA_ALLOCATION_CREATE_MAPPED_BIT);
            }
            
            LongBuffer bufferPtr = stack.callocLong(1);
            PointerBuffer allocationPtr = stack.callocPointer(1);
            VmaAllocationInfo allocationResult = VmaAllocationInfo.calloc(stack);
            VulkanUtils.crashIfFailure(device, Vma.vmaCreateBuffer(device.vma(), bufferInfo, allocationInfo, bufferPtr, allocationPtr, allocationResult), "Failed to allocate Vulkan Improvement terrain buffer " + label);
            this.vkBuffer = bufferPtr.get(0);
            this.allocation = allocationPtr.get(0);
            this.mappedAddress = allocationResult.pMappedData();
            IntBuffer memoryFlags = stack.callocInt(1);
            Vma.vmaGetAllocationMemoryProperties(device.vma(), this.allocation, memoryFlags);
            this.coherent = (memoryFlags.get(0) & VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0;
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
        if (this.size > Integer.MAX_VALUE - 8) {
            throw new IllegalStateException(this.label + " exceeds ByteBuffer limit (" + this.size + " bytes); use mappedSlice() instead");
        }
        return MemoryUtil.memByteBuffer(this.mappedAddress, (int) this.size);
    }
    
    void flush() {
        flush(0L, this.size);
    }
    
    /**
     * Makes what the CPU wrote to a range visible to the GPU.
     */
    void flush(long offset, long length) {
        if (this.mappedAddress != 0L && !this.coherent && length > 0L) {
            Vma.vmaFlushAllocation(this.device.vma(), this.allocation, offset, length);
        }
    }
    
    void invalidate() {
        if (this.mappedAddress != 0L && !this.coherent) {
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