package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A meshlet's header: where its vertices and indices are, its box and the way its faces point.
 * Where they are is a byte offset into the payload buffers, 64 bits wide like the addresses
 * the shaders add it to.
 */
public final class TerrainMeshletHeaderRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(TerrainGpuRecords.INT.withName("sectionX"), TerrainGpuRecords.INT.withName("sectionY"), TerrainGpuRecords.INT.withName("sectionZ"), TerrainGpuRecords.INT.withName("layer"), TerrainGpuRecords.INT.withName("firstVertex"), TerrainGpuRecords.INT.withName("vertexCount"), TerrainGpuRecords.INT.withName("firstIndex"), TerrainGpuRecords.INT.withName("indexCount"), TerrainGpuRecords.LONG.withName("vertexByteOffset"), TerrainGpuRecords.LONG.withName("indexByteOffset"), TerrainGpuRecords.INT.withName("vertexBytes"), TerrainGpuRecords.INT.withName("indexBytes"), TerrainGpuRecords.INT.withName("flags0"), TerrainGpuRecords.INT.withName("materialId"), MemoryLayout.sequenceLayout(3, TerrainGpuRecords.FLOAT).withName("boundsMin"), MemoryLayout.sequenceLayout(3, TerrainGpuRecords.FLOAT).withName("boundsMax"), TerrainGpuRecords.INT.withName("facing"), TerrainGpuRecords.INT.withName("reserved0")).withName("MeshletHeader");
    public static final long SECTION_X = TerrainGpuRecords.offset(LAYOUT, "sectionX");
    public static final long SECTION_Y = TerrainGpuRecords.offset(LAYOUT, "sectionY");
    public static final long SECTION_Z = TerrainGpuRecords.offset(LAYOUT, "sectionZ");
    public static final long LAYER = TerrainGpuRecords.offset(LAYOUT, "layer");
    public static final long FIRST_VERTEX = TerrainGpuRecords.offset(LAYOUT, "firstVertex");
    public static final long VERTEX_COUNT = TerrainGpuRecords.offset(LAYOUT, "vertexCount");
    public static final long FIRST_INDEX = TerrainGpuRecords.offset(LAYOUT, "firstIndex");
    public static final long INDEX_COUNT = TerrainGpuRecords.offset(LAYOUT, "indexCount");
    public static final long VERTEX_BYTE_OFFSET = TerrainGpuRecords.offset(LAYOUT, "vertexByteOffset");
    public static final long INDEX_BYTE_OFFSET = TerrainGpuRecords.offset(LAYOUT, "indexByteOffset");
    public static final long VERTEX_BYTES = TerrainGpuRecords.offset(LAYOUT, "vertexBytes");
    public static final long INDEX_BYTES = TerrainGpuRecords.offset(LAYOUT, "indexBytes");
    public static final long FLAGS = TerrainGpuRecords.offset(LAYOUT, "flags0");
    public static final long MATERIAL_ID = TerrainGpuRecords.offset(LAYOUT, "materialId");
    public static final long BOUNDS_MIN = TerrainGpuRecords.offset(LAYOUT, "boundsMin");
    public static final long BOUNDS_MAX = TerrainGpuRecords.offset(LAYOUT, "boundsMax");
    public static final long FACING = TerrainGpuRecords.offset(LAYOUT, "facing");
    
    private TerrainMeshletHeaderRecord() {
    }
}