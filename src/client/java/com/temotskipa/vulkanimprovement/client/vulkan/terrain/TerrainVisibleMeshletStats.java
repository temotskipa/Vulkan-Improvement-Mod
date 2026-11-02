package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record TerrainVisibleMeshletStats(int uploaded, int dropped, int offset, int capacity, int nextOffset, boolean wrapped, boolean wrapBlocked, String reason) {
	static TerrainVisibleMeshletStats empty() {
		return new TerrainVisibleMeshletStats(0, 0, 0, 0, 0, false, false, "");
	}
	
	static TerrainVisibleMeshletStats fromAllocation(int uploaded, int dropped, VisibleMeshletAllocation allocation) {
		return new TerrainVisibleMeshletStats(uploaded, dropped, allocation.offset(), allocation.capacity(), allocation.nextOffset(), allocation.wrapped(), allocation.wrapBlocked(), allocation.reason());
	}
}