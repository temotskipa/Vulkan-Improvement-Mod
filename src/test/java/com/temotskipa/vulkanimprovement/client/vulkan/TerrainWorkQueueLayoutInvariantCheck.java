package com.temotskipa.vulkanimprovement.client.vulkan;

import java.util.Map;

public final class TerrainWorkQueueLayoutInvariantCheck {
    private TerrainWorkQueueLayoutInvariantCheck() {
    }

    public static void main(String[] args) {
        checkRecordLayout();
        checkCapacityBytes();
        checkOffsets();
        checkDiagnostics();
        checkInvalidInputsRejected();
    }

    private static void checkRecordLayout() {
        require(TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES == Integer.BYTES * 4, "work queue counters must stay four uints");
        require(TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE == Integer.BYTES * 4, "work queue record must stay four uints");
        require(TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE % Integer.BYTES == 0, "work queue record stride must be int-aligned");
    }

    private static void checkCapacityBytes() {
        require(TerrainWorkQueueLayout.bytesForCapacity(0) == TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES, "zero-capacity work queue must still include counters");
        require(TerrainWorkQueueLayout.bytesForCapacity(3) == TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES + TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE * 3L, "work queue byte size must cover counters and records");
    }

    private static void checkOffsets() {
        require(TerrainWorkQueueLayout.recordsOffset() == TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES, "records must start after counters");
        require(TerrainWorkQueueLayout.recordOffset(0) == TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES, "first record must start at records offset");
        require(TerrainWorkQueueLayout.recordOffset(4) == TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES + TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE * 4L, "record offsets must use record stride");
    }

    private static void checkDiagnostics() {
        Map<String, Object> diagnostics = TerrainWorkQueueLayout.asMap(5);
        require(diagnostics.get("counterBytes").equals(TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES), "diagnostics must include counter bytes");
        require(diagnostics.get("recordStride").equals(TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE), "diagnostics must include record stride");
        require(diagnostics.get("recordCapacity").equals(5), "diagnostics must include record capacity");
        require(diagnostics.get("bytes").equals(TerrainWorkQueueLayout.bytesForCapacity(5)), "diagnostics must include total bytes");
        Object fields = diagnostics.get("recordFields");
        require(fields instanceof Map<?, ?>, "diagnostics must include record field offsets");
        require(((Map<?, ?>) fields).get("meshletIndex").equals(0), "record diagnostics must include meshlet index offset");
        require(((Map<?, ?>) fields).get("flags").equals(12), "record diagnostics must include flags offset");
    }

    private static void checkInvalidInputsRejected() {
        requireThrows(() -> TerrainWorkQueueLayout.bytesForCapacity(-1), "negative capacity must be rejected");
        requireThrows(() -> TerrainWorkQueueLayout.recordOffset(-1), "negative record index must be rejected");
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
