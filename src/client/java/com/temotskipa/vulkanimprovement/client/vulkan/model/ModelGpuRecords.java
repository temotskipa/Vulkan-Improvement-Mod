package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * The records the model vertex pass reads and writes, laid out as {@code model_vertices.comp.glsl}
 * reads them: little endian, four-byte words, read by word index. Offsets are taken from the
 * layouts, and the shader's word counts are checked against them by the build.
 */
public final class ModelGpuRecords {
    public static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
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
    
    private static long offset(StructLayout layout, String member) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(member));
    }
    
    /**
     * A face of a resident cube: its corners in the order Minecraft writes them, each a position in
     * blocks (Minecraft's {@code worldX} and the rest) and a texture coordinate, and the direction
     * it faces, an index into {@code Direction.values()}.
     */
    public static final class Polygon {
        public static final StructLayout CORNER = MemoryLayout.structLayout(FLOAT.withName("x"), FLOAT.withName("y"), FLOAT.withName("z"), FLOAT.withName("u"), FLOAT.withName("v")).withName("Corner");
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(MemoryLayout.sequenceLayout(POLYGON_VERTICES, CORNER).withName("corners"), INT.withName("direction"), INT.withName("reserved0"), INT.withName("reserved1"), INT.withName("reserved2")).withName("Polygon");
        public static final long BYTES = LAYOUT.byteSize();
        public static final long CORNERS = offset(LAYOUT, "corners");
        public static final long CORNER_BYTES = CORNER.byteSize();
        public static final long CORNER_X = offset(CORNER, "x");
        public static final long CORNER_Y = offset(CORNER, "y");
        public static final long CORNER_Z = offset(CORNER, "z");
        public static final long CORNER_U = offset(CORNER, "u");
        public static final long CORNER_V = offset(CORNER, "v");
        public static final long DIRECTION = offset(LAYOUT, "direction");
        
        private Polygon() {
        }
    }
    
    /**
     * A posed part: the first three rows of its pose matrix, column by column as JOML names them
     * ({@code m00 m01 m02 m10 ... m32}), its normal for each direction already packed as Minecraft
     * packs normals, and the color (ABGR, as Minecraft stores it), overlay and light its cubes are
     * drawn with.
     */
    public static final class Part {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(MemoryLayout.sequenceLayout(12, FLOAT).withName("pose"), MemoryLayout.sequenceLayout(MAX_CUBE_POLYGONS, INT).withName("normals"), INT.withName("color"), INT.withName("overlay"), INT.withName("light"), INT.withName("reserved0"), INT.withName("reserved1"), INT.withName("reserved2")).withName("Part");
        public static final long BYTES = LAYOUT.byteSize();
        public static final long POSE = offset(LAYOUT, "pose");
        public static final long NORMALS = offset(LAYOUT, "normals");
        public static final long COLOR = offset(LAYOUT, "color");
        public static final long OVERLAY = offset(LAYOUT, "overlay");
        public static final long LIGHT = offset(LAYOUT, "light");
        
        private Part() {
        }
    }
    
    /**
     * A cube to write: the part that poses it, its first resident polygon, its draw in the low 24
     * bits and its polygon count above them, and the vertex its first polygon starts at within its
     * draw.
     */
    public static final class Job {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(INT.withName("part"), INT.withName("firstPolygon"), INT.withName("drawAndPolygons"), INT.withName("firstVertex")).withName("Job");
        public static final long BYTES = LAYOUT.byteSize();
        public static final long PART = offset(LAYOUT, "part");
        public static final long FIRST_POLYGON = offset(LAYOUT, "firstPolygon");
        public static final long DRAW_AND_POLYGONS = offset(LAYOUT, "drawAndPolygons");
        public static final long FIRST_VERTEX = offset(LAYOUT, "firstVertex");
        public static final int DRAW_BITS = 24;
        public static final int MAX_DRAWS = 1 << DRAW_BITS;
        
        private Job() {
        }
    }
    
    /**
     * The vertex the pass writes: Minecraft's {@code DefaultVertexFormat.ENTITY}, position, color,
     * texture coordinate, overlay, light and normal, 36 bytes, as {@code BufferBuilder} writes it.
     */
    public static final class EntityVertex {
        public static final long BYTES = 36;
        
        private EntityVertex() {
        }
    }
}
