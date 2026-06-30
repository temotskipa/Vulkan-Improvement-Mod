package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RtPtAccelerationDataRegistry {
    private static final RtPtAccelerationDataRegistry INSTANCE = new RtPtAccelerationDataRegistry();
    private final Map<String, RtPtAccelerationPage> livePages = new LinkedHashMap<>();
    private final Map<String, Long> fallbackReasonCounts = new LinkedHashMap<>();
    private long registeredPageCount;
    private long retiredPageCount;
    private long pendingRebuildCount;
    private long deviceLostClearCount;
    
    private RtPtAccelerationDataRegistry() {
    }
    
    public static RtPtAccelerationDataRegistry get() {
        return INSTANCE;
    }
    
    private static String key(RtPtAccelerationPage page) {
        return page.domain().diagnosticName() + ":" + page.sectionId().sectionNode() + ":" + page.sourceRevision().value() + ":" + page.sourcePageKind().diagnosticName();
    }
    
    synchronized void reset() {
        this.livePages.clear();
        this.fallbackReasonCounts.clear();
        this.registeredPageCount = 0L;
        this.retiredPageCount = 0L;
        this.pendingRebuildCount = 0L;
        this.deviceLostClearCount = 0L;
    }
    
    synchronized void registerPage(RtPtAccelerationPage page) {
        this.livePages.put(key(page), page);
        this.registeredPageCount++;
        countReason(page.fallbackReason());
    }
    
    @SuppressWarnings("SameParameterValue")
    synchronized void markPendingRebuild(RtPtAccelerationPage page, String fallbackReason) {
        this.livePages.remove(key(page));
        this.pendingRebuildCount++;
        countReason(fallbackReason);
    }
    
    @SuppressWarnings("SameParameterValue")
    synchronized void retirePage(RtPtAccelerationPage page, String fallbackReason) {
        this.livePages.remove(key(page));
        this.retiredPageCount++;
        countReason(fallbackReason);
    }
    
    @SuppressWarnings("SameParameterValue")
    synchronized void clearForDeviceLost(String fallbackReason) {
        this.livePages.clear();
        this.deviceLostClearCount++;
        countReason(fallbackReason);
    }
    
    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("allocationEnabled", false);
        map.put("registeredPageCount", this.registeredPageCount);
        map.put("livePageCount", (long) this.livePages.size());
        map.put("retiredPageCount", this.retiredPageCount);
        map.put("pendingRebuildCount", this.pendingRebuildCount);
        map.put("deviceLostClearCount", this.deviceLostClearCount);
        map.put("fallbackReasonCounts", new LinkedHashMap<>(this.fallbackReasonCounts));
        return map;
    }
    
    private void countReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        this.fallbackReasonCounts.merge(reason, 1L, Long::sum);
    }
}