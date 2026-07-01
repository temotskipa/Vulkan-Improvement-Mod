package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.Map;

public final class GpuResourceRetirementStatsInvariantCheck {
    private GpuResourceRetirementStatsInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) {
        checkEmptyStats();
        checkSingleSetStats();
        checkMergedStats();
        checkSaturatingMerge();
        checkNegativeStatsRejected();
    }

    private static void checkEmptyStats() {
        GpuResourceRetirementStats stats = GpuResourceRetirementStats.empty();
        require(stats.resourceSets() == 0, "empty stats must report zero resource sets");
        require(stats.buffers() == 0, "empty stats must report zero buffers");
        require(stats.bytes() == 0L, "empty stats must report zero bytes");
        requireMap(stats.asMap(), 0, 0, 0L);
    }

    private static void checkSingleSetStats() {
        GpuResourceRetirementStats stats = GpuResourceRetirementStats.singleSet(11, 4096L);
        require(stats.resourceSets() == 1, "non-empty single set stats must count one resource set");
        require(stats.buffers() == 11, "single set stats must keep buffer count");
        require(stats.bytes() == 4096L, "single set stats must keep byte count");
        requireMap(stats.asMap(), 1, 11, 4096L);
    }

    private static void checkMergedStats() {
        GpuResourceRetirementStats first = GpuResourceRetirementStats.singleSet(4, 128L);
        GpuResourceRetirementStats second = GpuResourceRetirementStats.singleSet(7, 256L);
        GpuResourceRetirementStats merged = first.plus(second);

        require(merged.resourceSets() == 2, "merged stats must add resource sets");
        require(merged.buffers() == 11, "merged stats must add buffers");
        require(merged.bytes() == 384L, "merged stats must add bytes");
        requireMap(merged.asMap(), 2, 11, 384L);
    }

    private static void checkSaturatingMerge() {
        GpuResourceRetirementStats first = new GpuResourceRetirementStats(Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);
        GpuResourceRetirementStats second = GpuResourceRetirementStats.singleSet(1, 1L);
        GpuResourceRetirementStats merged = first.plus(second);

        require(merged.resourceSets() == Integer.MAX_VALUE, "resource set overflow must saturate");
        require(merged.buffers() == Integer.MAX_VALUE, "buffer overflow must saturate");
        require(merged.bytes() == Long.MAX_VALUE, "byte overflow must saturate");
    }

    private static void checkNegativeStatsRejected() {
        requireThrows(() -> new GpuResourceRetirementStats(-1, 0, 0L), "negative resource sets must be rejected");
        requireThrows(() -> new GpuResourceRetirementStats(0, -1, 0L), "negative buffers must be rejected");
        requireThrows(() -> new GpuResourceRetirementStats(0, 0, -1L), "negative bytes must be rejected");
    }

    private static void requireMap(Map<String, Object> map, int resourceSets, int buffers, long bytes) {
        require(map.get("resourceSets").equals(resourceSets), "diagnostic map must include resource set count");
        require(map.get("buffers").equals(buffers), "diagnostic map must include buffer count");
        require(map.get("bytes").equals(bytes), "diagnostic map must include byte count");
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
