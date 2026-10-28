package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A posed part: the first three rows of its pose matrix, column by column as JOML names them
 * ({@code m00 m01 m02 m10 ... m32}), its normal for each direction already packed as Minecraft
 * packs normals, and the color (ABGR, as Minecraft stores it), overlay and light its cubes are
 * drawn with.
 */
public final class ModelPartRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(MemoryLayout.sequenceLayout(12, ModelGpuRecords.FLOAT).withName("pose"), MemoryLayout.sequenceLayout(ModelGpuRecords.MAX_CUBE_POLYGONS, ModelGpuRecords.INT).withName("normals"), ModelGpuRecords.INT.withName("color"), ModelGpuRecords.INT.withName("overlay"), ModelGpuRecords.INT.withName("light"), ModelGpuRecords.INT.withName("reserved0"), ModelGpuRecords.INT.withName("reserved1"), ModelGpuRecords.INT.withName("reserved2")).withName("Part");
    public static final long BYTES = LAYOUT.byteSize();
    public static final long POSE = ModelGpuRecords.offset(LAYOUT, "pose");
    public static final long NORMALS = ModelGpuRecords.offset(LAYOUT, "normals");
    public static final long COLOR = ModelGpuRecords.offset(LAYOUT, "color");
    public static final long OVERLAY = ModelGpuRecords.offset(LAYOUT, "overlay");
    public static final long LIGHT = ModelGpuRecords.offset(LAYOUT, "light");
    public static final int WORDS = Math.toIntExact(BYTES / Integer.BYTES);
    public static final int POSE_WORD = Math.toIntExact(POSE / Integer.BYTES);
    public static final int NORMALS_WORD = Math.toIntExact(NORMALS / Integer.BYTES);
    public static final int COLOR_WORD = Math.toIntExact(COLOR / Integer.BYTES);
    public static final int OVERLAY_WORD = Math.toIntExact(OVERLAY / Integer.BYTES);
    public static final int LIGHT_WORD = Math.toIntExact(LIGHT / Integer.BYTES);
    
    private ModelPartRecord() {
    }
}