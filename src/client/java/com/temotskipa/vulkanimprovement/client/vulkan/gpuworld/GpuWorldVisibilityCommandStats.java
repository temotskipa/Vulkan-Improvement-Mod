package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GpuWorldVisibilityCommandStats {
    private long compactionPassCount;
    private long inputSectionCount;
    private long culledSectionCount;
    private long visibleSectionCount;
    private long emittedCommandCount;
    private long overflowedCommandCount;
    private long fallbackCommandCount;
    private VisibilityCommandPass lastPass;

    private GpuWorldVisibilityCommandStats() {
    }

    public static GpuWorldVisibilityCommandStats empty() {
        return new GpuWorldVisibilityCommandStats();
    }

    public synchronized void recordPass(long inputSectionCount, long culledSectionCount,
                                        long visibleSectionCount, long emittedCommandCount,
                                        long overflowedCommandCount, long fallbackCommandCount) {
        requireNonNegative(inputSectionCount, "inputSectionCount");
        requireNonNegative(culledSectionCount, "culledSectionCount");
        requireNonNegative(visibleSectionCount, "visibleSectionCount");
        requireNonNegative(emittedCommandCount, "emittedCommandCount");
        requireNonNegative(overflowedCommandCount, "overflowedCommandCount");
        requireNonNegative(fallbackCommandCount, "fallbackCommandCount");
        long describedSections = checkedAdd(culledSectionCount, visibleSectionCount,
                "GPU world visibility section counts overflow long");
        if (describedSections > inputSectionCount) {
            throw new IllegalArgumentException("culled plus visible sections cannot exceed input sections: "
                    + describedSections + " > " + inputSectionCount);
        }

        this.compactionPassCount = checkedAdd(this.compactionPassCount, 1L,
                "GPU world compaction pass count overflow long");
        this.inputSectionCount = checkedAdd(this.inputSectionCount, inputSectionCount,
                "GPU world input section count overflow long");
        this.culledSectionCount = checkedAdd(this.culledSectionCount, culledSectionCount,
                "GPU world culled section count overflow long");
        this.visibleSectionCount = checkedAdd(this.visibleSectionCount, visibleSectionCount,
                "GPU world visible section count overflow long");
        this.emittedCommandCount = checkedAdd(this.emittedCommandCount, emittedCommandCount,
                "GPU world emitted command count overflow long");
        this.overflowedCommandCount = checkedAdd(this.overflowedCommandCount, overflowedCommandCount,
                "GPU world overflowed command count overflow long");
        this.fallbackCommandCount = checkedAdd(this.fallbackCommandCount, fallbackCommandCount,
                "GPU world fallback command count overflow long");
        this.lastPass = new VisibilityCommandPass(inputSectionCount, culledSectionCount, visibleSectionCount,
                emittedCommandCount, overflowedCommandCount, fallbackCommandCount);
    }

    synchronized void resetForTests() {
        this.compactionPassCount = 0L;
        this.inputSectionCount = 0L;
        this.culledSectionCount = 0L;
        this.visibleSectionCount = 0L;
        this.emittedCommandCount = 0L;
        this.overflowedCommandCount = 0L;
        this.fallbackCommandCount = 0L;
        this.lastPass = null;
    }

    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("compactionPassCount", this.compactionPassCount);
        map.put("inputSectionCount", this.inputSectionCount);
        map.put("culledSectionCount", this.culledSectionCount);
        map.put("visibleSectionCount", this.visibleSectionCount);
        map.put("emittedCommandCount", this.emittedCommandCount);
        map.put("overflowedCommandCount", this.overflowedCommandCount);
        map.put("fallbackCommandCount", this.fallbackCommandCount);
        map.put("gpuResidentInputRequired", true);
        map.put("affectsVanillaCancellation", false);
        map.put("lastPass", this.lastPass == null ? Map.of("present", false) : this.lastPass.asMap());
        return map;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static long checkedAdd(long left, long right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private record VisibilityCommandPass(long inputSectionCount, long culledSectionCount,
                                         long visibleSectionCount, long emittedCommandCount,
                                         long overflowedCommandCount, long fallbackCommandCount) {
        private Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("present", true);
            map.put("inputSectionCount", this.inputSectionCount);
            map.put("culledSectionCount", this.culledSectionCount);
            map.put("visibleSectionCount", this.visibleSectionCount);
            map.put("emittedCommandCount", this.emittedCommandCount);
            map.put("overflowedCommandCount", this.overflowedCommandCount);
            map.put("fallbackCommandCount", this.fallbackCommandCount);
            return map;
        }
    }
}
