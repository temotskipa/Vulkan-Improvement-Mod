package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;

record GpuWorldResidentTerrainUsage(long livePages, long usedBytes, long capacityBytes, long allocationRefusals) {
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("livePages", this.livePages);
        map.put("usedBytes", this.usedBytes);
        map.put("capacityBytes", this.capacityBytes);
        map.put("allocationRefusals", this.allocationRefusals);
        return map;
    }
}