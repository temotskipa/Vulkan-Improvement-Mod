package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;

record GpuWorldVisibilityCommandPass(long inputSectionCount, long culledSectionCount, long visibleSectionCount, long emittedCommandCount, long overflowedCommandCount, long fallbackCommandCount) {
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("present", true);
        map.put("inputSectionCount", this.inputSectionCount);
        map.put("culledSectionCount", this.culledSectionCount);
        map.put("visibleSectionCount", this.visibleSectionCount);
        map.put("emittedCommandCount", this.emittedCommandCount);
        map.put("overflowedCommandCount", this.overflowedCommandCount);
        map.put("fallbackCommandCount", this.fallbackCommandCount);
        return map;
    }
}