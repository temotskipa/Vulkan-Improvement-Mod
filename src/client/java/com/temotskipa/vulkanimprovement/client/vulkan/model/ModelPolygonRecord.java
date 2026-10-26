package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A face of a resident cube: its corners in the order Minecraft writes them, each a position in
 * blocks (Minecraft's {@code worldX} and the rest) and a texture coordinate, and the direction
 * it faces, an index into {@code Direction.values()}.
 */
public final class ModelPolygonRecord {
    public static final StructLayout CORNER = MemoryLayout.structLayout(ModelGpuRecords.FLOAT.withName("x"), ModelGpuRecords.FLOAT.withName("y"), ModelGpuRecords.FLOAT.withName("z"), ModelGpuRecords.FLOAT.withName("u"), ModelGpuRecords.FLOAT.withName("v")).withName("Corner");
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(MemoryLayout.sequenceLayout(ModelGpuRecords.POLYGON_VERTICES, CORNER).withName("corners"), ModelGpuRecords.INT.withName("direction"), ModelGpuRecords.INT.withName("reserved0"), ModelGpuRecords.INT.withName("reserved1"), ModelGpuRecords.INT.withName("reserved2")).withName("Polygon");
    public static final long BYTES = LAYOUT.byteSize();
    public static final long CORNERS = ModelGpuRecords.offset(LAYOUT, "corners");
    public static final long CORNER_BYTES = CORNER.byteSize();
    public static final long CORNER_X = ModelGpuRecords.offset(CORNER, "x");
    public static final long CORNER_Y = ModelGpuRecords.offset(CORNER, "y");
    public static final long CORNER_Z = ModelGpuRecords.offset(CORNER, "z");
    public static final long CORNER_U = ModelGpuRecords.offset(CORNER, "u");
    public static final long CORNER_V = ModelGpuRecords.offset(CORNER, "v");
    public static final long DIRECTION = ModelGpuRecords.offset(LAYOUT, "direction");
    
    private ModelPolygonRecord() {
    }
}