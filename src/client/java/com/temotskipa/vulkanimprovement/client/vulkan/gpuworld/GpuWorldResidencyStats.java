package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.EnumMap;
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
	
	private static EnumMap<GpuWorldPageKind, Long> zeroedPageKindMap() {
		EnumMap<GpuWorldPageKind, Long> map = new EnumMap<>(GpuWorldPageKind.class);
		for (GpuWorldPageKind kind : GpuWorldPageKind.values()) {
			map.put(kind, 0L);
		}
		return map;
	}
	
	public synchronized boolean tryReservePage(GpuWorldPageKind pageKind, long residentBytes) {
		requireNonNegativeResidentBytes(residentBytes);
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
	
	private static void requireNonNegativeResidentBytes(long residentBytes) {
		if (residentBytes < 0L) {
			throw new IllegalArgumentException("residentBytes must be non-negative: " + residentBytes);
		}
	}
	
	private boolean wouldExceedKindBudget(GpuWorldPageKind pageKind, long residentBytes) {
		return wouldExceed(this.usedBytesByKind.get(pageKind), residentBytes, this.budget.pageKindBudgetBytes(pageKind));
	}
	
	private boolean wouldExceedResidentBudget(long residentBytes) {
		return wouldExceed(usedResidentBytes(), residentBytes, this.budget.maxResidentBytes());
	}
	
	private static long checkedAdd(long left, long right) {
		try {
			return Math.addExact(left, right);
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("GPU world residency stats overflow long", exception);
		}
	}
	
	private static boolean wouldExceed(long current, long increment, long limit) {
		try {
			return Math.addExact(current, increment) > limit;
		} catch (ArithmeticException exception) {
			return true;
		}
	}
	
	synchronized long usedResidentBytes() {
		long bytes = 0L;
		for (long usedBytes : this.usedBytesByKind.values()) {
			bytes = checkedAdd(bytes, usedBytes);
		}
		return bytes;
	}
	
	public synchronized void recordEviction(GpuWorldPageKind pageKind, long residentBytes) {
		releasePage(pageKind, residentBytes);
		this.evictedPageCount = checkedAdd(this.evictedPageCount, 1L);
		this.evictedBytes = checkedAdd(this.evictedBytes, residentBytes);
	}
	
	public synchronized void releasePage(GpuWorldPageKind pageKind, long residentBytes) {
		requireNonNegativeResidentBytes(residentBytes);
		GpuWorldPageKind kind = Objects.requireNonNull(pageKind, "pageKind");
		long livePageCount = this.livePageCountsByKind.get(kind);
		long usedBytes = this.usedBytesByKind.get(kind);
		if (livePageCount <= 0L) {
			throw new IllegalArgumentException("cannot release missing GPU world page kind: " + kind.diagnosticName());
		}
		if (residentBytes > usedBytes) {
			throw new IllegalArgumentException("cannot release more GPU world bytes than are resident: " + residentBytes + " > " + usedBytes);
		}
		this.livePageCountsByKind.put(kind, livePageCount - 1L);
		this.usedBytesByKind.put(kind, usedBytes - residentBytes);
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
	
	public synchronized void resetLivePages() {
		for (GpuWorldPageKind kind : GpuWorldPageKind.values()) {
			this.livePageCountsByKind.put(kind, 0L);
			this.usedBytesByKind.put(kind, 0L);
		}
	}
	
	public synchronized String describe() {
		return "livePages=" + livePageCount() + ", usedBytes=" + usedResidentBytes() + ", maxBytes=" + this.budget.maxResidentBytes() + ", acceptedPages=" + this.acceptedPageCount + ", acceptedBytes=" + this.acceptedBytes + ", evictedPages=" + this.evictedPageCount + ", evictedBytes=" + this.evictedBytes + ", overBudgetRefusals=" + this.overBudgetRefusals + ", overBudgetRefusedBytes=" + this.overBudgetRefusedBytes;
	}
	
	synchronized long livePageCount(GpuWorldPageKind pageKind) {
		return this.livePageCountsByKind.get(pageKind);
	}
	
	synchronized long usedBytes(GpuWorldPageKind pageKind) {
		return this.usedBytesByKind.get(pageKind);
	}
	
	synchronized long evictedPageCount() {
		return this.evictedPageCount;
	}
	
	synchronized long evictedBytes() {
		return this.evictedBytes;
	}
	
	synchronized long overBudgetRefusals() {
		return this.overBudgetRefusals;
	}
	
	synchronized long overBudgetRefusedBytes() {
		return this.overBudgetRefusedBytes;
	}
	
	synchronized long livePageCount() {
		long count = 0L;
		for (long pageCount : this.livePageCountsByKind.values()) {
			count = checkedAdd(count, pageCount);
		}
		return count;
	}
}