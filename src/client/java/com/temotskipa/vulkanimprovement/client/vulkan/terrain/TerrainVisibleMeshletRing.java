package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

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
    
    VisibleMeshletAllocation allocate(int requestedRecords, boolean wrapAllowed) {
        if (requestedRecords <= 0) {
            return VisibleMeshletAllocation.unavailable(requestedRecords, this.capacity, this.cursor, "no visible meshlet records requested", false);
        }
        if (this.capacity <= 0) {
            return VisibleMeshletAllocation.unavailable(requestedRecords, this.capacity, this.cursor, "visible meshlet ring has no capacity", false);
        }
        if (requestedRecords > this.capacity) {
            return VisibleMeshletAllocation.unavailable(requestedRecords, this.capacity, this.cursor, "visible meshlet records exceed ring capacity", false);
        }
        
        long next = (long) this.cursor + requestedRecords;
        if (next <= this.capacity) {
            return new VisibleMeshletAllocation(requestedRecords, this.cursor, requestedRecords, this.capacity, (int) next, false, true, false, "");
        }
        if (!wrapAllowed) {
            return VisibleMeshletAllocation.unavailable(requestedRecords, this.capacity, this.cursor, "visible meshlet ring wrap requires completed terrain read fence", true);
        }
        return new VisibleMeshletAllocation(requestedRecords, 0, requestedRecords, this.capacity, requestedRecords, true, true, false, "");
    }
}