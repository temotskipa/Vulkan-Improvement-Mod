package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.LinkedHashMap;
import java.util.Map;

record TerrainVisibleMeshletRing(int capacity, int cursor) {
    TerrainVisibleMeshletRing {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
        if (cursor < 0) {
            throw new IllegalArgumentException("cursor must be non-negative");
        }
        if (cursor > capacity) {
            throw new IllegalArgumentException("cursor must not exceed capacity");
        }
    }

    Allocation allocate(int requestedRecords, boolean wrapAllowed) {
        if (requestedRecords <= 0) {
            return Allocation.unavailable(requestedRecords, this.capacity, this.cursor, "no visible meshlet records requested", false);
        }
        if (this.capacity <= 0) {
            return Allocation.unavailable(requestedRecords, this.capacity, this.cursor, "visible meshlet ring has no capacity", false);
        }
        if (requestedRecords > this.capacity) {
            return Allocation.unavailable(requestedRecords, this.capacity, this.cursor, "visible meshlet records exceed ring capacity", false);
        }

        long next = (long) this.cursor + requestedRecords;
        if (next <= this.capacity) {
            return new Allocation(requestedRecords, this.cursor, requestedRecords, this.capacity, (int) next, false, true, false, "");
        }
        if (!wrapAllowed) {
            return Allocation.unavailable(requestedRecords, this.capacity, this.cursor, "visible meshlet ring wrap requires completed terrain read fence", true);
        }
        return new Allocation(requestedRecords, 0, requestedRecords, this.capacity, requestedRecords, true, true, false, "");
    }

    record Allocation(int requestedRecords, int offset, int count, int capacity, int nextOffset,
                      boolean wrapped, boolean ready, boolean wrapBlocked, String reason) {
        Allocation {
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

        static Allocation unavailable(int requestedRecords, int capacity, int nextOffset, String reason, boolean wrapBlocked) {
            return new Allocation(Math.max(requestedRecords, 0), 0, 0, capacity, nextOffset, false, false, wrapBlocked, reason);
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
}
