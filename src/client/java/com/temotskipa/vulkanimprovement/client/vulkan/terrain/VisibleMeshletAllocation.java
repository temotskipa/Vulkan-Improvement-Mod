package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

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
}