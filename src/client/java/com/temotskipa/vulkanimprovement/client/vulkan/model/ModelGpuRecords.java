package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.foreign.ValueLayout.OfFloat;
import java.lang.foreign.ValueLayout.OfInt;
import java.nio.ByteOrder;

/**
 * The records the model vertex pass reads and writes, laid out as {@code model_vertices.comp.glsl}
 * reads them: little endian, four-byte words, read by word index. Offsets are taken from the
 * layouts, and the shader's word counts are checked against them by the build.
 */
public final class ModelGpuRecords {
    public static final OfInt INT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    /**
     * The most faces a cube has, and so the most polygons a cube job writes.
     */
    public static final int MAX_CUBE_POLYGONS = 6;
    /**
     * The vertices of a polygon: Minecraft's model faces are quads.
     */
    public static final int POLYGON_VERTICES = 4;
    
    private ModelGpuRecords() {
    }
    
    static long offset(StructLayout layout, String member) {
        return layout.byteOffset(PathElement.groupElement(member));
    }
}