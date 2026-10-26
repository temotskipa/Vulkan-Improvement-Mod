package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

record VisibleMeshletAllocation(int requestedRecords, int offset, int count, int capacity, int nextOffset, boolean wrapped, boolean ready, boolean wrapBlocked, String reason) {
    VisibleMeshletAllocation {
        if (requestedRecords < 0) {
            requestedRecords = 0;
        }
        if (offset < 0 || count < 0 || capacity < 0 || nextOffset < 0) {
            throw new IllegalArgumentException("ring allocation counters must be non-negative");
        }
        if (offset > capacity || nextOffset > capacity) {
            throw new IllegalArgumentException("ring allocation offsets must stay within capacity");
        }
        reason = reason == null ? "" : reason;
    }
    
    static VisibleMeshletAllocation unavailable(int requestedRecords, int capacity, int nextOffset, String reason, boolean wrapBlocked) {
        return new VisibleMeshletAllocation(Math.max(requestedRecords, 0), 0, 0, capacity, nextOffset, false, false, wrapBlocked, reason);
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("requestedRecords", this.requestedRecords);
        map.put("offset", this.offset);
        map.put("count", this.count);
        map.put("capacity", this.capacity);
        map.put("nextOffset", this.nextOffset);
        map.put("wrapped", this.wrapped);
        map.put("ready", this.ready);
        map.put("wrapBlocked", this.wrapBlocked);
        map.put("reason", this.reason);
        return map;
    }
}