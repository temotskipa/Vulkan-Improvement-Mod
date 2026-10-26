package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A cube to write: the part that poses it, its first resident polygon, its draw in the low 24
 * bits and its polygon count above them, and the vertex its first polygon starts at within its
 * draw.
 */
public final class ModelJobRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(ModelGpuRecords.INT.withName("part"), ModelGpuRecords.INT.withName("firstPolygon"), ModelGpuRecords.INT.withName("drawAndPolygons"), ModelGpuRecords.INT.withName("firstVertex")).withName("Job");
    public static final long BYTES = LAYOUT.byteSize();
    public static final long PART = ModelGpuRecords.offset(LAYOUT, "part");
    public static final long FIRST_POLYGON = ModelGpuRecords.offset(LAYOUT, "firstPolygon");
    public static final long DRAW_AND_POLYGONS = ModelGpuRecords.offset(LAYOUT, "drawAndPolygons");
    public static final long FIRST_VERTEX = ModelGpuRecords.offset(LAYOUT, "firstVertex");
    public static final int DRAW_BITS = 24;
    public static final int MAX_DRAWS = 1 << DRAW_BITS;
    
    private ModelJobRecord() {
    }
}