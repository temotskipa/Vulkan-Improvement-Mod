package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.Map;

public final class TerrainMeshTaskDispatchInvariantCheck {
    private TerrainMeshTaskDispatchInvariantCheck() {
    }

    public static void main(String[] args) {
        checkUnavailableDispatch();
        checkFullLayerDispatch();
        checkFullLayerDispatchClampsToDirectTaskLimit();
        checkCpuVisibleListDispatch();
        checkCpuWorkQueueDispatch();
        checkIndirectCommandDispatch();
        checkInvalidDispatchesRejected();
    }

    private static void checkUnavailableDispatch() {
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.unavailable("test");

        require(!dispatch.ready(), "unavailable dispatch must not be ready");
        require("unavailable".equals(dispatch.source().id()), "unavailable dispatch must report source id");
        require("test".equals(dispatch.reason()), "unavailable dispatch must preserve reason");
        requireMap(dispatch.asMap(), "unavailable", 0, -1, 0, 0, false, false, false);
    }

    private static void checkFullLayerDispatch() {
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(32, 2, 128);

        require(dispatch.ready(), "full layer dispatch with tasks must be ready");
        require(!dispatch.usesVisibleMeshletList(), "full layer dispatch must not use visible meshlet list");
        require(!dispatch.truncated(), "full layer dispatch below limit must not be truncated");
        requireMap(dispatch.asMap(), "direct-layer", 32, 2, 128, 128, false, false, false);
    }

    private static void checkFullLayerDispatchClampsToDirectTaskLimit() {
        int requested = TerrainMeshTaskDispatch.MAX_DIRECT_TASKS + 10;
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(0, -1, requested);

        require(dispatch.ready(), "clamped full layer dispatch must remain ready");
        require(dispatch.taskCount() == TerrainMeshTaskDispatch.MAX_DIRECT_TASKS, "full layer dispatch must clamp task count");
        require(dispatch.truncated(), "full layer dispatch above direct limit must report truncation");
        requireMap(dispatch.asMap(), "direct-layer", 0, -1, requested, TerrainMeshTaskDispatch.MAX_DIRECT_TASKS, false, false, true);
    }

    private static void checkCpuVisibleListDispatch() {
        DescriptorHeapTerrainResources.VisibleMeshletUpload upload = new DescriptorHeapTerrainResources.VisibleMeshletUpload(7, 64, 4096L, 0, true);
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuVisibleList(upload, 1);

        require(dispatch.ready(), "CPU visible-list dispatch must be ready when upload is ready");
        require(dispatch.usesVisibleMeshletList(), "CPU visible-list dispatch must use visible meshlet list");
        require(!dispatch.usesWorkQueue(), "CPU visible-list dispatch must not use work queue");
        require(!dispatch.truncated(), "visible-list dispatch below limit must not be truncated");
        requireMap(dispatch.asMap(), "cpu-visible-list", 7, 1, 64, 64, true, false, false);
    }

    private static void checkCpuWorkQueueDispatch() {
        DescriptorHeapTerrainResources.TerrainWorkQueueUpload upload = new DescriptorHeapTerrainResources.TerrainWorkQueueUpload(9, 32, 8192L, 0, true);
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.cpuWorkQueue(upload, 2, 96);

        require(dispatch.ready(), "CPU work-queue dispatch must be ready when upload is ready");
        require(!dispatch.usesVisibleMeshletList(), "CPU work-queue dispatch must not use visible meshlet list");
        require(dispatch.usesWorkQueue(), "CPU work-queue dispatch must use work queue");
        require(dispatch.truncated(), "CPU work-queue dispatch must report truncation against requested meshlets");
        requireMap(dispatch.asMap(), "cpu-work-queue", 9, 2, 96, 32, false, true, true);
    }

    private static void checkIndirectCommandDispatch() {
        DescriptorHeapTerrainResources.TerrainMeshTaskCommandUpload upload = new DescriptorHeapTerrainResources.TerrainMeshTaskCommandUpload(2, 32, 12_288L, 24L, TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, 16_384L, 0, true, true);
        TerrainMeshTaskDispatch dispatch = TerrainMeshTaskDispatch.fullLayer(4, 1, 32).withIndirectCommand(upload);

        require(dispatch.ready(), "dispatch with indirect command must remain ready");
        require(dispatch.usesIndirectCommand(), "dispatch must report indirect command use");
        require(dispatch.indirectCommandBuffer() == 12_288L, "dispatch must carry indirect command buffer");
        require(dispatch.indirectCommandOffset() == 24L, "dispatch must carry indirect command offset");
        require(dispatch.indirectCommandStride() == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, "dispatch must carry indirect command stride");
        require(dispatch.usesGpuGeneratedIndirectCommand(), "dispatch must preserve GPU-generated indirect command state");
        requireMap(dispatch.asMap(), "direct-layer", 4, 1, 32, 32, false, false, false);
        require(dispatch.asMap().get("usesIndirectCommand").equals(true), "dispatch diagnostics must include indirect command use");
        require(dispatch.asMap().get("usesGpuGeneratedIndirectCommand").equals(true), "dispatch diagnostics must include GPU-generated indirect command use");
    }

    private static void checkInvalidDispatchesRejected() {
        requireThrows(() -> new TerrainMeshTaskDispatch(-1, 0, 0, 0, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskDispatch.Source.DIRECT_LAYER, ""), "negative offset must be rejected");
        requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, -1, 0, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskDispatch.Source.DIRECT_LAYER, ""), "negative requested meshlets must be rejected");
        requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 2, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskDispatch.Source.DIRECT_LAYER, ""), "task count above requested meshlets must be rejected");
        requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskDispatch.Source.CPU_VISIBLE_LIST, ""), "visible-list dispatch without address must be rejected");
        requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 0L, 0L, 0, 0L, false, TerrainMeshTaskDispatch.Source.CPU_WORK_QUEUE, ""), "work-queue dispatch without address must be rejected");
        requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 1L, 0L, 0, 0L, false, TerrainMeshTaskDispatch.Source.DIRECT_LAYER, ""), "indirect dispatch without stride must be rejected");
        requireThrows(() -> new TerrainMeshTaskDispatch(0, 0, 1, 1, 0L, 0L, 0L, 1L, TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, 0L, false, TerrainMeshTaskDispatch.Source.DIRECT_LAYER, ""), "indirect metadata without buffer must be rejected");
    }

    private static void requireMap(Map<String, Object> map, String source, int offset, int layerOrdinal,
                                   int requestedMeshlets, int taskCount, boolean usesVisibleList,
                                   boolean usesWorkQueue, boolean truncated) {
        require(source.equals(map.get("source")), "dispatch diagnostics must include source");
        require(map.get("meshletOffset").equals(offset), "dispatch diagnostics must include meshlet offset");
        require(map.get("layerOrdinal").equals(layerOrdinal), "dispatch diagnostics must include layer ordinal");
        require(map.get("requestedMeshlets").equals(requestedMeshlets), "dispatch diagnostics must include requested meshlets");
        require(map.get("taskCount").equals(taskCount), "dispatch diagnostics must include task count");
        require(map.get("usesVisibleMeshletList").equals(usesVisibleList), "dispatch diagnostics must include visible-list use");
        require(map.get("usesWorkQueue").equals(usesWorkQueue), "dispatch diagnostics must include work-queue use");
        require(map.containsKey("usesIndirectCommand"), "dispatch diagnostics must include indirect-command use");
        require(map.containsKey("usesGpuGeneratedIndirectCommand"), "dispatch diagnostics must include GPU-generated indirect-command use");
        require(map.get("truncated").equals(truncated), "dispatch diagnostics must include truncation state");
        require(map.get("directMeshTaskLimit").equals(TerrainMeshTaskDispatch.MAX_DIRECT_TASKS), "dispatch diagnostics must include direct task limit");
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
