package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldPageKind;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldRevision;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldSectionId;
import java.util.LinkedHashMap;
import java.util.Map;

public record RtPtAccelerationPage(RtPtAccelerationDomain domain, GpuWorldSectionId sectionId,
                                   GpuWorldRevision sourceRevision, GpuWorldPageKind sourcePageKind,
                                   long blasHandle, long tlasInstanceHandle, String fallbackReason) {
    public RtPtAccelerationPage {
        if (blasHandle < 0L) {
            throw new IllegalArgumentException("BLAS handle must be opaque or zero, not negative: " + blasHandle);
        }
        if (tlasInstanceHandle < 0L) {
            throw new IllegalArgumentException("TLAS instance handle must be opaque or zero, not negative: " + tlasInstanceHandle);
        }
        fallbackReason = fallbackReason == null ? "" : fallbackReason;
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domain", this.domain.diagnosticName());
        map.put("sectionId", this.sectionId.asMap());
        map.put("sourceRevision", this.sourceRevision.value());
        map.put("sourcePageKind", this.sourcePageKind.diagnosticName());
        map.put("sourceGameplayAuthoritative", Boolean.FALSE);
        map.put("blasHandle", this.blasHandle);
        map.put("tlasInstanceHandle", this.tlasInstanceHandle);
        map.put("fallbackReason", this.fallbackReason);
        return map;
    }
}
