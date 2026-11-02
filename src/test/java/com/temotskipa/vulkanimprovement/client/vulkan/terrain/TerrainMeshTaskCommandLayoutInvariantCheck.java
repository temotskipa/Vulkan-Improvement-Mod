package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainMeshTaskCommandRecord;

import java.io.IOException;

public final class TerrainMeshTaskCommandLayoutInvariantCheck {
	private TerrainMeshTaskCommandLayoutInvariantCheck() {
	}
	
	
	static void main() throws IOException {
		checkCommandLayout(GlslSource.read("mesh_task_command.comp.glsl"), GlslSource.read("mesh_terrain.mesh.glsl"));
		checkCapacityBytes();
		checkInvalidInputsRejected();
	}
	
	private static void checkCommandLayout(String commandShader, String meshShader) {
		require(TerrainMeshTaskCommandRecord.LAYOUT.byteSize() == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, "mesh-task indirect command must stay three uints");
		require(GlslSource.bytes(GlslSource.body(commandShader, "uniform CommandPushConstants {")) == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES, "visibility pass push constants must cover four addresses, eight uints and the camera");
		require(GlslSource.bytes(GlslSource.body(meshShader, "buffer TerrainDebugCounters {")) <= TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET, "the mesh stage's counters must end before the visibility counters begin");
		require(TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET + GlslSource.bytes(GlslSource.body(commandShader, "buffer VisibilityCounters {")) <= TerrainGpuLayout.DEBUG_COUNTER_BYTES, "the visibility counters must fit behind the mesh stage's in the debug counters");
	}
	
	private static void checkCapacityBytes() {
		require(TerrainMeshTaskCommandLayout.bytesForCapacity() == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE * (long) TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY, "command byte size must cover every command record");
		require(TerrainMeshTaskCommandLayout.commandOffset(4) == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE * 4L, "command offsets must use command stride");
	}
	
	private static void checkInvalidInputsRejected() {
		requireThrows(() -> TerrainMeshTaskCommandLayout.commandOffset(-1));
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
	
	private static void requireThrows(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("negative command index must be rejected");
	}
}