package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanRuntimeProfile;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.*;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private SourceInvalidation lastSourceInvalidation;
    
    private RtPtAccelerationDataRegistry() {
    }
    
    public static RtPtAccelerationDataRegistry get() {
        return INSTANCE;
    }
    
    /**
     * Whether an allocation task may be scheduled on a device with this profile. The device has to
     * support allocation and allocation has to be switched on; it is not, because no allocation
     * plan exists yet, so the answer is no on every device and the gate records why.
     */
    public static AllocationGate allocationGate(VulkanRuntimeProfile profile) {
        return new AllocationGate(profile.rtPtAccelerationAllocationSupported(), ALLOCATION_ENABLED, profile.rtPtAccelerationAllocationDisabledReason());
    }
    
    private static boolean pageInvalidatedBy(GpuWorldSectionUpdate update, RtPtAccelerationPage page) {
        return page.sectionId().equals(update.section()) && page.sourcePageKind() == update.pageKind() && !page.sourceRevision().equals(update.revision());
    }
    
    private static String normalizedReason(String reason) {
        return reason == null ? "" : reason.strip();
    }
    
    private static String key(RtPtAccelerationPage page) {
        return page.domain().diagnosticName() + ":" + page.sectionId().sectionNode() + ":" + page.sourceRevision().value() + ":" + page.sourcePageKind().diagnosticName();
    }
    
    synchronized void reset() {
        resetState();
    }
    
    public synchronized void resetForTests() {
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
    
    public synchronized void registerPage(RtPtAccelerationPage page) {
        this.livePages.put(key(page), page);
        this.registeredPageCount++;
        countReason(page.fallbackReason());
    }
    
    public synchronized void invalidateSource(GpuWorldSectionUpdate update, String fallbackReason) {
        long invalidatedPages = 0L;
        Iterator<Map.Entry<String, RtPtAccelerationPage>> pages = this.livePages.entrySet().iterator();
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
        this.lastSourceInvalidation = new SourceInvalidation(update.sequence(), update.section(), update.revision(), update.pageKind(), update.reason(), invalidatedPages, normalizedReason(fallbackReason));
        countReason(fallbackReason);
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
        map.put("allocationEnabled", ALLOCATION_ENABLED);
        map.put("allocationGate", allocationGate(VulkanRuntimeProfile.from(VulkanImprovementCapabilities.snapshot())).asMap());
        map.put("registeredPageCount", this.registeredPageCount);
        map.put("livePageCount", (long) this.livePages.size());
        map.put("retiredPageCount", this.retiredPageCount);
        map.put("pendingRebuildCount", this.pendingRebuildCount);
        map.put("deviceLostClearCount", this.deviceLostClearCount);
        map.put("sourceInvalidationCount", this.sourceInvalidationCount);
        map.put("sourceInvalidatedPageCount", this.sourceInvalidatedPageCount);
        map.put("lastSourceInvalidation", this.lastSourceInvalidation == null ? Map.of("present", false) : this.lastSourceInvalidation.asMap());
        map.put("fallbackReasonCounts", new LinkedHashMap<>(this.fallbackReasonCounts));
        return map;
    }
    
    private void countReason(String reason) {
        reason = normalizedReason(reason);
        if (reason.isBlank()) {
            return;
        }
        this.fallbackReasonCounts.merge(reason, 1L, Long::sum);
    }
    
    public record AllocationGate(boolean deviceSupported, boolean allocationEnabled, String disabledReason) {
        public boolean canScheduleAllocation() {
            return this.deviceSupported && this.allocationEnabled;
        }
        
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("deviceSupported", this.deviceSupported);
            map.put("allocationEnabled", this.allocationEnabled);
            map.put("canScheduleAllocation", canScheduleAllocation());
            map.put("selectedPath", "disabled");
            map.put("disabledReason", this.disabledReason);
            return map;
        }
    }
    
    private record SourceInvalidation(long updateSequence, GpuWorldSectionId sectionId, GpuWorldRevision sourceRevision, GpuWorldPageKind pageKind, GpuWorldDirtyReason reason, long invalidatedPageCount, String fallbackReason) {
        private Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("present", true);
            map.put("updateSequence", this.updateSequence);
            map.put("sectionId", this.sectionId.asMap());
            map.put("sourceRevision", this.sourceRevision.value());
            map.put("pageKind", this.pageKind.diagnosticName());
            map.put("reason", this.reason.diagnosticName());
            map.put("invalidatedPageCount", this.invalidatedPageCount);
            map.put("fallbackReason", this.fallbackReason);
            return map;
        }
    }
}