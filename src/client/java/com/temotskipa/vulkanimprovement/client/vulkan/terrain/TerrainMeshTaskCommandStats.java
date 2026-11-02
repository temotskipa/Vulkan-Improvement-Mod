package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record TerrainMeshTaskCommandStats(int uploaded, int dropped, int offset, int capacity, int nextOffset, boolean wrapped, boolean wrapBlocked, String reason, boolean gpuGenerated) {
	static TerrainMeshTaskCommandStats empty() {
		return new TerrainMeshTaskCommandStats(0, 0, 0, 0, 0, false, false, "", false);
	}
	
	static TerrainMeshTaskCommandStats unavailable(int dropped, int capacity, int nextOffset, String reason, boolean wrapBlocked, boolean gpuGenerated) {
		return new TerrainMeshTaskCommandStats(0, dropped, 0, capacity, nextOffset, false, wrapBlocked, reason, gpuGenerated);
	}
}