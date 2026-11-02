package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanRuntimeProfile;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldSectionUpdate;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class RtPtAccelerationDataRegistry {
	private static final RtPtAccelerationDataRegistry INSTANCE = new RtPtAccelerationDataRegistry();
	/**
	 * Stays off until an allocation plan with runtime validation is active.
	 */
	private static final boolean ALLOCATION_ENABLED = false;
	
	private final Map<String, RtPtAccelerationPage> livePages = new LinkedHashMap<>();
	private final Map<String, Long> fallbackReasonCounts = new LinkedHashMap<>();
	private long registeredPageCount;
	private long retiredPageCount;
	private long pendingRebuildCount;
	private long deviceLostClearCount;
	private long sourceInvalidationCount;
	private long sourceInvalidatedPageCount;
	private RtPtSourceInvalidation lastSourceInvalidation;
	
	private RtPtAccelerationDataRegistry() {
	}
	
	public static RtPtAccelerationDataRegistry get() {
		return INSTANCE;
	}
	
	synchronized void reset() {
		resetState();
	}
	
	private void resetState() {
		this.livePages.clear();
		this.fallbackReasonCounts.clear();
		this.registeredPageCount = 0L;
		this.retiredPageCount = 0L;
		this.pendingRebuildCount = 0L;
		this.deviceLostClearCount = 0L;
		this.sourceInvalidationCount = 0L;
		this.sourceInvalidatedPageCount = 0L;
		this.lastSourceInvalidation = null;
	}
	
	public synchronized void resetForTests() {
		resetState();
	}
	
	public synchronized void registerPage(RtPtAccelerationPage page) {
		this.livePages.put(key(page), page);
		this.registeredPageCount++;
		countReason(page.fallbackReason());
	}
	
	private static String key(RtPtAccelerationPage page) {
		return page.domain().diagnosticName() + ":" + page.sectionId().sectionNode() + ":" + page.sourceRevision().value() + ":" + page.sourcePageKind().diagnosticName();
	}
	
	private void countReason(String reason) {
		reason = normalizedReason(reason);
		if (reason.isBlank()) {
			return;
		}
		this.fallbackReasonCounts.merge(reason, 1L, Long::sum);
	}
	
	private static String normalizedReason(String reason) {
		return reason == null ? "" : reason.strip();
	}
	
	public synchronized void invalidateSource(GpuWorldSectionUpdate update, String fallbackReason) {
		long invalidatedPages = 0L;
		Iterator<Entry<String, RtPtAccelerationPage>> pages = this.livePages.entrySet().iterator();
		while (pages.hasNext()) {
			RtPtAccelerationPage page = pages.next().getValue();
			if (pageInvalidatedBy(update, page)) {
				pages.remove();
				invalidatedPages++;
			}
		}
		this.sourceInvalidationCount++;
		this.sourceInvalidatedPageCount += invalidatedPages;
		this.pendingRebuildCount += invalidatedPages;
		this.lastSourceInvalidation = new RtPtSourceInvalidation(update.sequence(), update.section(), update.revision(), update.pageKind(), update.reason(), invalidatedPages, normalizedReason(fallbackReason));
		countReason(fallbackReason);
	}
	
	private static boolean pageInvalidatedBy(GpuWorldSectionUpdate update, RtPtAccelerationPage page) {
		return page.sectionId().equals(update.section()) && page.sourcePageKind() == update.pageKind() && !page.sourceRevision().equals(update.revision());
	}
	
	synchronized void markPendingRebuild(RtPtAccelerationPage page, String fallbackReason) {
		this.livePages.remove(key(page));
		this.pendingRebuildCount++;
		countReason(fallbackReason);
	}
	
	synchronized void retirePage(RtPtAccelerationPage page, String fallbackReason) {
		this.livePages.remove(key(page));
		this.retiredPageCount++;
		countReason(fallbackReason);
	}
	
	synchronized void clearForDeviceLost(String fallbackReason) {
		this.livePages.clear();
		this.deviceLostClearCount++;
		countReason(fallbackReason);
	}
	
	public synchronized String describe() {
		return "allocationGate=" + allocationGate(VulkanRuntimeProfile.from(VulkanImprovementCapabilities.snapshot())) + ", registeredPages=" + this.registeredPageCount + ", livePages=" + this.livePages.size() + ", retiredPages=" + this.retiredPageCount + ", pendingRebuilds=" + this.pendingRebuildCount + ", deviceLostClears=" + this.deviceLostClearCount + ", sourceInvalidations=" + this.sourceInvalidationCount + ", sourceInvalidatedPages=" + this.sourceInvalidatedPageCount + ", lastSourceInvalidation=" + this.lastSourceInvalidation + ", fallbackReasons=" + this.fallbackReasonCounts;
	}
	
	public synchronized long registeredPageCount() {
		return this.registeredPageCount;
	}
	
	public synchronized long livePageCount() {
		return this.livePages.size();
	}
	
	public synchronized long retiredPageCount() {
		return this.retiredPageCount;
	}
	
	public synchronized long pendingRebuildCount() {
		return this.pendingRebuildCount;
	}
	
	public synchronized long deviceLostClearCount() {
		return this.deviceLostClearCount;
	}
	
	public synchronized long sourceInvalidationCount() {
		return this.sourceInvalidationCount;
	}
	
	public synchronized long sourceInvalidatedPageCount() {
		return this.sourceInvalidatedPageCount;
	}
	
	public synchronized RtPtSourceInvalidation lastSourceInvalidation() {
		return this.lastSourceInvalidation;
	}
	
	public synchronized long fallbackReasonCount(String reason) {
		return this.fallbackReasonCounts.getOrDefault(reason, 0L);
	}
	
	/**
	 * Whether an allocation task may be scheduled on a device with this profile. The device has to
	 * support allocation and allocation has to be switched on; it is not, because no allocation
	 * plan exists yet, so the answer is no on every device and the gate records why.
	 */
	public static RtPtAllocationGate allocationGate(VulkanRuntimeProfile profile) {
		return new RtPtAllocationGate(profile.rtPtAccelerationAllocationSupported(), ALLOCATION_ENABLED, profile.rtPtAccelerationAllocationDisabledReason());
	}
}