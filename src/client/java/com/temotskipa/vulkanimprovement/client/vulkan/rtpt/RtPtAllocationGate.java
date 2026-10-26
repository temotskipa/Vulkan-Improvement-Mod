package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import java.util.LinkedHashMap;
import java.util.Map;

public record RtPtAllocationGate(boolean deviceSupported, boolean allocationEnabled, String disabledReason) {
    public boolean canScheduleAllocation() {
        return this.deviceSupported && this.allocationEnabled;
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deviceSupported", this.deviceSupported);
        map.put("allocationEnabled", this.allocationEnabled);
        map.put("canScheduleAllocation", canScheduleAllocation());
        map.put("selectedPath", "disabled");
        map.put("disabledReason", this.disabledReason);
        return map;
    }
}