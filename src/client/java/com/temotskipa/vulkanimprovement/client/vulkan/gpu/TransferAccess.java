package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

/**
 * What a transfer reads or writes: a byte range of a buffer, from {@code start} to before
 * {@code end}, or a whole texture.
 */
public record TransferAccess(long handle, long start, long end, boolean image, boolean write) {
    public static TransferAccess readBuffer(long vkBuffer, long start, long end) {
        return new TransferAccess(vkBuffer, start, end, false, false);
    }
    
    public static TransferAccess writeBuffer(long vkBuffer, long start, long end) {
        return new TransferAccess(vkBuffer, start, end, false, true);
    }
    
    public static TransferAccess readImage(long vkImage) {
        return new TransferAccess(vkImage, 0L, 0L, true, false);
    }
    
    public static TransferAccess writeImage(long vkImage) {
        return new TransferAccess(vkImage, 0L, 0L, true, true);
    }
}