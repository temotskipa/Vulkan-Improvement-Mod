package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class GpuWorldResidencyStats {
    private final GpuWorldResidencyBudget budget;
    private final EnumMap<GpuWorldPageKind, Long> livePageCountsByKind = zeroedPageKindMap();
    private final EnumMap<GpuWorldPageKind, Long> usedBytesByKind = zeroedPageKindMap();
    private long acceptedPageCount;
    private long acceptedBytes;
    private long evictedPageCount;
    private long evictedBytes;
    private long overBudgetRefusals;
    private long overBudgetRefusedBytes;

    private GpuWorldResidencyStats(GpuWorldResidencyBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public static GpuWorldResidencyStats empty(GpuWorldResidencyBudget budget) {
        return new GpuWorldResidencyStats(budget);
    }

    public synchronized boolean tryReservePage(GpuWorldPageKind pageKind, long residentBytes) {
        requireNonNegative(residentBytes, "residentBytes");
        GpuWorldPageKind kind = Objects.requireNonNull(pageKind, "pageKind");
        if (wouldExceedKindBudget(kind, residentBytes) || wouldExceedResidentBudget(residentBytes)) {
            this.overBudgetRefusals = checkedAdd(this.overBudgetRefusals, 1L);
            this.overBudgetRefusedBytes = checkedAdd(this.overBudgetRefusedBytes, residentBytes);
            return false;
        }
        this.livePageCountsByKind.put(kind, checkedAdd(this.livePageCountsByKind.get(kind), 1L));
        this.usedBytesByKind.put(kind, checkedAdd(this.usedBytesByKind.get(kind), residentBytes));
        this.acceptedPageCount = checkedAdd(this.acceptedPageCount, 1L);
        this.acceptedBytes = checkedAdd(this.acceptedBytes, residentBytes);
        return true;
    }

    public synchronized void releasePage(GpuWorldPageKind pageKind, long residentBytes) {
        requireNonNegative(residentBytes, "residentBytes");
        GpuWorldPageKind kind = Objects.requireNonNull(pageKind, "pageKind");
        long livePageCount = this.livePageCountsByKind.get(kind);
        long usedBytes = this.usedBytesByKind.get(kind);
        if (livePageCount <= 0L) {
            throw new IllegalArgumentException("cannot release missing GPU world page kind: " + kind.diagnosticName());
        }
        if (residentBytes > usedBytes) {
            throw new IllegalArgumentException("cannot release more GPU world bytes than are resident: "
                    + residentBytes + " > " + usedBytes);
        }
        this.livePageCountsByKind.put(kind, livePageCount - 1L);
        this.usedBytesByKind.put(kind, usedBytes - residentBytes);
    }

    public synchronized void recordEviction(GpuWorldPageKind pageKind, long residentBytes) {
        releasePage(pageKind, residentBytes);
        this.evictedPageCount = checkedAdd(this.evictedPageCount, 1L);
        this.evictedBytes = checkedAdd(this.evictedBytes, residentBytes);
    }

    public synchronized void resetLivePages() {
        for (GpuWorldPageKind kind : GpuWorldPageKind.values()) {
            this.livePageCountsByKind.put(kind, 0L);
            this.usedBytesByKind.put(kind, 0L);
        }
    }

    synchronized void resetForTests() {
        resetLivePages();
        this.acceptedPageCount = 0L;
        this.acceptedBytes = 0L;
        this.evictedPageCount = 0L;
        this.evictedBytes = 0L;
        this.overBudgetRefusals = 0L;
        this.overBudgetRefusedBytes = 0L;
    }

    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reservedBytes", this.budget.reservedBytes());
        map.put("maxResidentBytes", this.budget.maxResidentBytes());
        map.put("livePageCount", livePageCount());
        map.put("usedResidentBytes", usedResidentBytes());
        map.put("availableResidentBytes", this.budget.maxResidentBytes() - usedResidentBytes());
        map.put("livePageCountsByKind", mapByKind(this.livePageCountsByKind));
        map.put("usedBytesByKind", mapByKind(this.usedBytesByKind));
        map.put("acceptedPageCount", this.acceptedPageCount);
        map.put("acceptedBytes", this.acceptedBytes);
        map.put("evictedPageCount", this.evictedPageCount);
        map.put("evictedBytes", this.evictedBytes);
        map.put("overBudgetRefusals", this.overBudgetRefusals);
        map.put("overBudgetRefusedBytes", this.overBudgetRefusedBytes);
        map.put("withinResidentBudget", usedResidentBytes() <= this.budget.maxResidentBytes());
        return map;
    }

    private boolean wouldExceedKindBudget(GpuWorldPageKind pageKind, long residentBytes) {
        return wouldExceed(this.usedBytesByKind.get(pageKind), residentBytes, this.budget.pageKindBudgetBytes(pageKind));
    }

    private boolean wouldExceedResidentBudget(long residentBytes) {
        return wouldExceed(usedResidentBytes(), residentBytes, this.budget.maxResidentBytes());
    }

    private static boolean wouldExceed(long current, long increment, long limit) {
        try {
            return Math.addExact(current, increment) > limit;
        } catch (ArithmeticException exception) {
            return true;
        }
    }

    private long livePageCount() {
        long count = 0L;
        for (long pageCount : this.livePageCountsByKind.values()) {
            count = checkedAdd(count, pageCount);
        }
        return count;
    }

    private long usedResidentBytes() {
        long bytes = 0L;
        for (long usedBytes : this.usedBytesByKind.values()) {
            bytes = checkedAdd(bytes, usedBytes);
        }
        return bytes;
    }

    private static Map<String, Object> mapByKind(EnumMap<GpuWorldPageKind, Long> source) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (GpuWorldPageKind kind : GpuWorldPageKind.values()) {
            map.put(kind.diagnosticName(), source.get(kind));
        }
        return map;
    }

    private static EnumMap<GpuWorldPageKind, Long> zeroedPageKindMap() {
        EnumMap<GpuWorldPageKind, Long> map = new EnumMap<>(GpuWorldPageKind.class);
        for (GpuWorldPageKind kind : GpuWorldPageKind.values()) {
            map.put(kind, 0L);
        }
        return map;
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative: " + value);
        }
    }

    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("GPU world residency stats overflow long", exception);
        }
    }
}
