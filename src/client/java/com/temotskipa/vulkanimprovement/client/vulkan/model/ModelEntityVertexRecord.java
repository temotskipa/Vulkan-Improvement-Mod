package com.temotskipa.vulkanimprovement.client.vulkan.model;

/**
 * The vertex the pass writes: Minecraft's {@code DefaultVertexFormat.ENTITY}, position, color,
 * texture coordinate, overlay, light and normal, 36 bytes, as {@code BufferBuilder} writes it.
 */
public final class ModelEntityVertexRecord {
    public static final long BYTES = 36;
    
    private ModelEntityVertexRecord() {
    }
}