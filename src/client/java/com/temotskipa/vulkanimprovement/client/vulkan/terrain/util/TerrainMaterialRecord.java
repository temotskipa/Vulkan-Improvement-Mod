package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;

/**
 * A material's record in the material table.
 */
public final class TerrainMaterialRecord {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(TerrainGpuRecords.INT.withName("materialId"), TerrainGpuRecords.INT.withName("flags"), TerrainGpuRecords.INT.withName("blockAtlasWidth"), TerrainGpuRecords.INT.withName("blockAtlasHeight"), TerrainGpuRecords.INT.withName("blockAtlasBaseMip"), TerrainGpuRecords.INT.withName("blockAtlasMipLevels"), TerrainGpuRecords.INT.withName("lightmapWidth"), TerrainGpuRecords.INT.withName("lightmapHeight"), TerrainGpuRecords.INT.withName("normalTextureIndex"), TerrainGpuRecords.INT.withName("specularTextureIndex"), TerrainGpuRecords.INT.withName("emissionTextureIndex"), TerrainGpuRecords.INT.withName("tintFlags"), TerrainGpuRecords.INT.withName("alphaMode"), TerrainGpuRecords.INT.withName("renderLayerOrdinal"), TerrainGpuRecords.INT.withName("materialDomain"), TerrainGpuRecords.INT.withName("reserved0")).withName("MaterialRecord");
    
    private TerrainMaterialRecord() {
    }
}