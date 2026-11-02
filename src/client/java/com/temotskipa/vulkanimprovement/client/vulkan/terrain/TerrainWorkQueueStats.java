package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record TerrainWorkQueueStats(int uploaded, int dropped, int offset, int capacity, int nextOffset, boolean wrapped, boolean wrapBlocked, String reason) {
	static TerrainWorkQueueStats empty() {
		return new TerrainWorkQueueStats(0, 0, 0, 0, 0, false, false, "");
	}
	
	static TerrainWorkQueueStats unavailable(int dropped, int capacity, int nextOffset, String reason, boolean wrapBlocked) {
		return new TerrainWorkQueueStats(0, dropped, 0, capacity, nextOffset, false, wrapBlocked, reason);
	}
}