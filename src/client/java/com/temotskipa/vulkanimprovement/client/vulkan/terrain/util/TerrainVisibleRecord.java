package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A task's record in the visible list: which meshlets it draws, and how far its section has
 * faded in.
 */
public final class TerrainVisibleRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(TerrainGpuRecords.INT.withName("meshletIndex"), TerrainGpuRecords.FLOAT.withName("visibility")).withName("VisibleMeshletRecord");
    public static final long MESHLET_INDEX = TerrainGpuRecords.offset(LAYOUT, "meshletIndex");
    public static final long VISIBILITY = TerrainGpuRecords.offset(LAYOUT, "visibility");
    
    private TerrainVisibleRecord() {
    }
}