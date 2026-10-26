package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * The records the terrain path writes for the GPU, laid out as the shaders declare them: little
 * endian and packed, as the shaders read them with scalar layout. Every value may sit at any byte
 * offset, so the same layouts serve mapped GPU memory and copies kept on the Java heap. Each
 * record's member names are the names its shader struct gives them, and its offsets are taken
 * from the layout, not counted by hand.
 */
public final class TerrainGpuRecords {
    public static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    
    private TerrainGpuRecords() {
    }
    
    static long offset(StructLayout layout, String member) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(member));
    }
}