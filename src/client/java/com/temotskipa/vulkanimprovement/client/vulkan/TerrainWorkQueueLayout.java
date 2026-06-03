package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.LinkedHashMap;
import java.util.Map;

final class TerrainWorkQueueLayout {
    private TerrainWorkQueueLayout() {
    }

    static long recordsOffset() {
        return TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES;
    }

    static long recordOffset(int recordIndex) {
        if (recordIndex < 0) {
            throw new IllegalArgumentException("recordIndex must be non-negative");
        }
        return recordsOffset() + (long) recordIndex * TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE;
    }

    static long bytesForCapacity(int recordCapacity) {
        if (recordCapacity < 0) {
            throw new IllegalArgumentException("recordCapacity must be non-negative");
        }
        return recordsOffset() + (long) recordCapacity * TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE;
    }

    static Map<String, Object> asMap(int recordCapacity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("counterBytes", TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES);
        map.put("recordStride", TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE);
        map.put("recordCapacity", recordCapacity);
        map.put("recordsOffset", recordsOffset());
        map.put("bytes", bytesForCapacity(recordCapacity));
        map.put("recordFields", Map.of(
                "meshletIndex", 0,
                "layerOrdinal", 4,
                "lodLevel", 8,
                "flags", 12
        ));
        return map;
    }
}
