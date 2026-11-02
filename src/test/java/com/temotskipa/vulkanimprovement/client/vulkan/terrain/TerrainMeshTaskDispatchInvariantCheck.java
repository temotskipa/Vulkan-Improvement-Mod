package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public final class TerrainMeshTaskDispatchInvariantCheck {
	private TerrainMeshTaskDispatchInvariantCheck() {
	}
	
	
	static void main() {
		checkUnavailableDispatch();
		checkFullLayerDispatch();
		checkFullLayerDispatchClampsToDirectTaskLimit();
		checkCpuVisibleListDispatch();
		checkCpuWorkQueueDispatch();
		checkIndirectCommandDispatch();
		checkLayerReplacementPolicy();
		checkFullLayerSegmentationPolicy();
		checkInvalidDispatchesRejected();
	}
	
	private static void checkUnavailableDispatch() {
		TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.unavailable("test");
		
		require(!dispatch.ready(), "unavailable dispatch must not be ready");
		require("unavailable".equals(dispatch.source().id()), "unavailable dispatch must report source id");
		require("test".equals(dispatch.reason()), "unavailable dispatch must preserve reason");
		requireDispatch(dispatch, "unavailable", 0, -1, 0, 0, false, false, false);
	}
	
	private static void checkFullLayerDispatch() {
		TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(32, 2, 128);
		
		require(dispatch.ready(), "full layer dispatch with tasks must be ready");
		require(!dispatch.usesVisibleMeshletList(), "full layer dispatch must not use visible meshlet list");
		require(!dispatch.truncated(), "full layer dispatch below limit must not be truncated");
		requireDispatch(dispatch, "direct-layer", 32, 2, 128, 128, false, false, false);
	}
	
	private static void checkFullLayerDispatchClampsToDirectTaskLimit() {
		int requested = TerrainMeshTaskDispatch.MAX_DIRECT_TASKS + 10;
		TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(0, -1, requested);
		
		require(dispatch.ready(), "clamped full layer dispatch must remain ready");
		require(dispatch.taskCount() == TerrainMeshTaskDispatch.MAX_DIRECT_TASKS, "full layer dispatch must clamp task count");
		require(dispatch.truncated(), "full layer dispatch above direct limit must report truncation");
		requireDispatch(dispatch, "direct-layer", 0, -1, requested, TerrainMeshTaskDispatch.MAX_DIRECT_TASKS, false, false, true);
	}
	
	private static void checkCpuVisibleListDispatch() {
		TerrainVisibleMeshletUpload upload = new TerrainVisibleMeshletUpload(7, 64, 4096L, 0, true);
		TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuVisibleList(upload, 1);
		
		require(dispatch.ready(), "CPU visible-list dispatch must be ready when upload is ready");
		require(dispatch.usesVisibleMeshletList(), "CPU visible-list dispatch must use visible meshlet list");
		require(!dispatch.usesWorkQueue(), "CPU visible-list dispatch must not use work queue");
		require(!dispatch.truncated(), "visible-list dispatch below limit must not be truncated");
		requireDispatch(dispatch, "cpu-visible-list", 7, 1, 64, 64, true, false, false);
	}
	
	private static void checkCpuWorkQueueDispatch() {
		TerrainWorkQueueUpload upload = new TerrainWorkQueueUpload(9, 32, 8192L, 0, true);
		TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuWorkQueue(upload, 2, 96);
		
		require(dispatch.ready(), "CPU work-queue dispatch must be ready when upload is ready");
		require(!dispatch.usesVisibleMeshletList(), "CPU work-queue dispatch must not use visible meshlet list");
		require(dispatch.usesWorkQueue(), "CPU work-queue dispatch must use work queue");
		require(dispatch.truncated(), "CPU work-queue dispatch must report truncation against requested meshlets");
		requireDispatch(dispatch, "cpu-work-queue", 9, 2, 96, 32, false, true, true);
	}
	
	private static void checkIndirectCommandDispatch() {
		TerrainMeshTaskCommandUpload upload = new TerrainMeshTaskCommandUpload(2, 32, 12_288L, 24L, TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, 16_384L, 0, true, true);
		TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(4, 1, 32).withIndirectCommand(upload);
		
		require(dispatch.ready(), "dispatch with indirect command must remain ready");
		require(dispatch.usesIndirectCommand(), "dispatch must report indirect command use");
		require(dispatch.indirectCommandBuffer() == 12_288L, "dispatch must carry indirect command buffer");
		require(dispatch.indirectCommandOffset() == 24L, "dispatch must carry indirect command offset");
		require(dispatch.indirectCommandStride() == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, "dispatch must carry indirect command stride");
		require(dispatch.usesGpuGeneratedIndirectCommand(), "dispatch must preserve GPU-generated indirect command state");
		requireDispatch(dispatch, "direct-layer", 4, 1, 32, 32, false, false, false);
		require(dispatch.usesIndirectCommand(), "the dispatch must use its indirect command");
		require(dispatch.usesGpuGeneratedIndirectCommand(), "the dispatch must use its GPU-generated indirect command");
	}
	
	private static void checkLayerReplacementPolicy() {
		require(TerrainLayerReplacementPolicy.canReplaceLayer(ChunkSectionLayer.SOLID.ordinal()), "solid layer must remain mesh-replaceable");
		require(TerrainLayerReplacementPolicy.canReplaceLayer(ChunkSectionLayer.CUTOUT.ordinal()), "cutout layer must remain mesh-replaceable");
		require(!TerrainLayerReplacementPolicy.canReplaceLayer(ChunkSectionLayer.TRANSLUCENT.ordinal()), "translucent terrain must stay on vanilla until translucent effects are contracted");
		require(TerrainLayerReplacementPolicy.canReplaceLayer(ChunkSectionLayer.SOLID), "solid layer object must remain mesh-replaceable");
		require(TerrainLayerReplacementPolicy.canReplaceLayer(ChunkSectionLayer.CUTOUT), "cutout layer object must remain mesh-replaceable");
		require(!TerrainLayerReplacementPolicy.canReplaceLayer(ChunkSectionLayer.TRANSLUCENT), "translucent layer object must fall back to vanilla");
		require(!TerrainLayerReplacementPolicy.canReplaceLayer(-1), "unknown layer must fall back to vanilla");
	}
	
	private static void checkFullLayerSegmentationPolicy() {
		int limit = TerrainMeshTaskDispatch.MAX_DIRECT_TASKS;
		
		require(TerrainLayerReplacementPolicy.dispatchCountForMeshlets(0) == 0, "empty layers must not dispatch");
		require(TerrainLayerReplacementPolicy.dispatchCountForMeshlets(limit) == 1, "one full task-limit segment must use one dispatch");
		require(TerrainLayerReplacementPolicy.dispatchCountForMeshlets(limit + 1) == 2, "layers above the task limit must split instead of truncating");
		require(TerrainLayerReplacementPolicy.segmentMeshletOffset(10, 1) == 10 + limit, "segment offset must advance by the direct task limit");
		require(TerrainLayerReplacementPolicy.segmentMeshletCount(limit + 7, 0) == limit, "first segment must fill the direct task limit");
		require(TerrainLayerReplacementPolicy.segmentMeshletCount(limit + 7, 1) == 7, "last segment must contain the remaining meshlets");
		require(TerrainLayerReplacementPolicy.segmentMeshletCount(limit + 7, 2) == 0, "segments past the layer end must be empty");
	}
	
	private static void checkInvalidDispatchesRejected() {
		requireThrows(() -> new TerrainMeshTaskDispatch(-1, 0, 0, 0, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskSource.DIRECT_LAYER, ""), "negative offset must be rejected");
		requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, -1, 0, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskSource.DIRECT_LAYER, ""), "negative requested meshlets must be rejected");
		requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 2, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskSource.DIRECT_LAYER, ""), "task count above requested meshlets must be rejected");
		requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskSource.CPU_VISIBLE_LIST, ""), "visible-list dispatch without address must be rejected");
		requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskSource.CPU_WORK_QUEUE, ""), "work-queue dispatch without address must be rejected");
		requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 1L, 0L, 0, 0L, false, TerrainMeshTaskSource.DIRECT_LAYER, ""), "indirect dispatch without stride must be rejected");
		requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 0L, 1L, TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, 0L, false, TerrainMeshTaskSource.DIRECT_LAYER, ""), "indirect metadata without buffer must be rejected");
	}
	
	private static void requireDispatch(TerrainMeshTaskDispatch dispatch, String source, int offset, int layerOrdinal, int requestedMeshlets, int taskCount, boolean usesVisibleList, boolean usesWorkQueue, boolean truncated) {
		require(source.equals(dispatch.source().id()), "the dispatch must keep its source");
		require(dispatch.meshletOffset() == offset, "the dispatch must keep its meshlet offset");
		require(dispatch.layerOrdinal() == layerOrdinal, "the dispatch must keep its layer ordinal");
		require(dispatch.requestedMeshlets() == requestedMeshlets, "the dispatch must keep its requested meshlets");
		require(dispatch.taskCount() == taskCount, "the dispatch must keep its task count");
		require(dispatch.usesVisibleMeshletList() == usesVisibleList, "the dispatch must say whether it uses the visible list");
		require(dispatch.usesWorkQueue() == usesWorkQueue, "the dispatch must say whether it uses the work queue");
		require(dispatch.truncated() == truncated, "the dispatch must say whether it was truncated");
	}
	
	private static void requireThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}