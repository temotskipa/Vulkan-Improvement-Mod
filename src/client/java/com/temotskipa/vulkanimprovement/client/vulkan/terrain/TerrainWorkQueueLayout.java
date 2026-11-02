package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

final class TerrainWorkQueueLayout {
	private TerrainWorkQueueLayout() {
	}
	
	static long recordOffset(int recordIndex) {
		if (recordIndex < 0) {
			throw new IllegalArgumentException("recordIndex must be non-negative");
		}
		return recordsOffset() + (long) recordIndex * TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE;
	}
	
	static long recordsOffset() {
		return TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES;
	}
	
	static long bytesForCapacity(int recordCapacity) {
		if (recordCapacity < 0) {
			throw new IllegalArgumentException("recordCapacity must be non-negative");
		}
		return recordsOffset() + (long) recordCapacity * TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE;
	}
}