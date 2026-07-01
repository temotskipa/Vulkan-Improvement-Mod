package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.GpuMaterialRecord;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainGpuLayout;
import java.util.LinkedHashMap;
import java.util.Map;

record GpuWorldMaterialTableContract(int revision, int capacity, int recordStride, int defaultMaterialId) {
    GpuWorldMaterialTableContract {
        if (revision <= 0) {
            throw new IllegalArgumentException("Material table revision must be positive: " + revision);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Material table capacity must be positive: " + capacity);
        }
        if (recordStride != GpuMaterialRecord.BYTES) {
            throw new IllegalArgumentException("Material table record stride must match GpuMaterialRecord.BYTES: " + recordStride);
        }
        if (defaultMaterialId < 0 || defaultMaterialId >= capacity) {
            throw new IllegalArgumentException("Default material id must fit the table: " + defaultMaterialId);
        }
    }
    
    static GpuWorldMaterialTableContract terrainDefault() {
        return new GpuWorldMaterialTableContract(1, TerrainGpuLayout.MATERIAL_TABLE_CAPACITY, TerrainGpuLayout.MATERIAL_RECORD_STRIDE, GpuMaterialRecord.DEFAULT_TERRAIN_MATERIAL_ID);
    }
    
    GpuWorldMaterialTableContract nextRevision() {
        int next = this.revision == Integer.MAX_VALUE ? 1 : this.revision + 1;
        return new GpuWorldMaterialTableContract(next, this.capacity, this.recordStride, this.defaultMaterialId);
    }
    
    boolean containsMaterialId(int materialId) {
        return materialId >= 0 && materialId < this.capacity;
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("revision", this.revision);
        map.put("capacity", this.capacity);
        map.put("recordStride", this.recordStride);
        map.put("defaultMaterialId", this.defaultMaterialId);
        return map;
    }
}
