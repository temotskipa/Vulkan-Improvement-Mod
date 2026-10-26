package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A section layer's record in the page table.
 */
public final class TerrainResidentPageRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(TerrainGpuRecords.LONG.withName("sectionNode"), TerrainGpuRecords.INT.withName("sectionX"), TerrainGpuRecords.INT.withName("sectionY"), TerrainGpuRecords.INT.withName("sectionZ"), TerrainGpuRecords.INT.withName("layer"), TerrainGpuRecords.INT.withName("firstMeshlet"), TerrainGpuRecords.INT.withName("meshletCount"), TerrainGpuRecords.INT.withName("estimatedVertices"), TerrainGpuRecords.INT.withName("estimatedMeshlets"), TerrainGpuRecords.INT.withName("vertexBytes"), TerrainGpuRecords.INT.withName("indexBytes"), TerrainGpuRecords.LONG.withName("vertexByteOffset"), TerrainGpuRecords.LONG.withName("indexByteOffset"), TerrainGpuRecords.INT.withName("fadeStart"), TerrainGpuRecords.INT.withName("customIndexBuffer"), TerrainGpuRecords.INT.withName("reserved1"), TerrainGpuRecords.INT.withName("reserved2")).withName("ResidentPage");
    public static final long SECTION_NODE = TerrainGpuRecords.offset(LAYOUT, "sectionNode");
    public static final long SECTION_X = TerrainGpuRecords.offset(LAYOUT, "sectionX");
    public static final long SECTION_Y = TerrainGpuRecords.offset(LAYOUT, "sectionY");
    public static final long SECTION_Z = TerrainGpuRecords.offset(LAYOUT, "sectionZ");
    public static final long LAYER = TerrainGpuRecords.offset(LAYOUT, "layer");
    public static final long FIRST_MESHLET = TerrainGpuRecords.offset(LAYOUT, "firstMeshlet");
    public static final long MESHLET_COUNT = TerrainGpuRecords.offset(LAYOUT, "meshletCount");
    public static final long ESTIMATED_VERTICES = TerrainGpuRecords.offset(LAYOUT, "estimatedVertices");
    public static final long ESTIMATED_MESHLETS = TerrainGpuRecords.offset(LAYOUT, "estimatedMeshlets");
    public static final long VERTEX_BYTES = TerrainGpuRecords.offset(LAYOUT, "vertexBytes");
    public static final long INDEX_BYTES = TerrainGpuRecords.offset(LAYOUT, "indexBytes");
    public static final long VERTEX_BYTE_OFFSET = TerrainGpuRecords.offset(LAYOUT, "vertexByteOffset");
    public static final long INDEX_BYTE_OFFSET = TerrainGpuRecords.offset(LAYOUT, "indexByteOffset");
    public static final long FADE_START = TerrainGpuRecords.offset(LAYOUT, "fadeStart");
    public static final long CUSTOM_INDEX_BUFFER = TerrainGpuRecords.offset(LAYOUT, "customIndexBuffer");
    
    private TerrainResidentPageRecord() {
    }
}