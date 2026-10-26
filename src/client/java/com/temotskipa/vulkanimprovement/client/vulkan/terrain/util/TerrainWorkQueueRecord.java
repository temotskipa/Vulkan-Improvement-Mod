package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A record of the CPU-written work queue: one meshlet of a layer.
 */
public final class TerrainWorkQueueRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(TerrainGpuRecords.INT.withName("meshletIndex"), TerrainGpuRecords.INT.withName("layerOrdinal"), TerrainGpuRecords.INT.withName("lodLevel"), TerrainGpuRecords.INT.withName("flags")).withName("TerrainWorkQueueRecord");
    public static final long MESHLET_INDEX = TerrainGpuRecords.offset(LAYOUT, "meshletIndex");
    public static final long LAYER_ORDINAL = TerrainGpuRecords.offset(LAYOUT, "layerOrdinal");
    public static final long LOD_LEVEL = TerrainGpuRecords.offset(LAYOUT, "lodLevel");
    public static final long FLAGS = TerrainGpuRecords.offset(LAYOUT, "flags");
    
    private TerrainWorkQueueRecord() {
    }
}