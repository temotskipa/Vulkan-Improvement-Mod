package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

final class TerrainMeshTaskCommandLayout {
	private TerrainMeshTaskCommandLayout() {
	}
	
	static long commandOffset(int commandIndex) {
		if (commandIndex < 0) {
			throw new IllegalArgumentException("commandIndex must be non-negative");
		}
		return (long) commandIndex * TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE;
	}
	
	static long bytesForCapacity() {
		if (TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY < 0) {
			throw new IllegalArgumentException("commandCapacity must be non-negative");
		}
		return (long) TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY * TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE;
	}
}