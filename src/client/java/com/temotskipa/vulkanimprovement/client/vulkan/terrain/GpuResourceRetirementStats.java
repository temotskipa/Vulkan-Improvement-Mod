package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

record GpuResourceRetirementStats(int resourceSets, int buffers, long bytes) {
    GpuResourceRetirementStats {
        if (resourceSets < 0) {
            throw new IllegalArgumentException("resourceSets must be non-negative");
        }
        if (buffers < 0) {
            throw new IllegalArgumentException("buffers must be non-negative");
        }
        if (bytes < 0L) {
            throw new IllegalArgumentException("bytes must be non-negative");
        }
    }
    
    static GpuResourceRetirementStats empty() {
        return new GpuResourceRetirementStats(0, 0, 0L);
    }
    
    static GpuResourceRetirementStats singleSet(int buffers, long bytes) {
        if (buffers == 0 && bytes == 0L) {
            return empty();
        }
        return new GpuResourceRetirementStats(1, buffers, bytes);
    }
    
    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
    
    private static long saturatingAdd(long left, long right) {
        long sum = left + right;
        return sum < 0L ? Long.MAX_VALUE : sum;
    }
    
    GpuResourceRetirementStats plus(GpuResourceRetirementStats other) {
        return new GpuResourceRetirementStats(saturatingAdd(this.resourceSets, other.resourceSets), saturatingAdd(this.buffers, other.buffers), saturatingAdd(this.bytes, other.bytes));
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("resourceSets", this.resourceSets);
        map.put("buffers", this.buffers);
        map.put("bytes", this.bytes);
        return map;
    }
}