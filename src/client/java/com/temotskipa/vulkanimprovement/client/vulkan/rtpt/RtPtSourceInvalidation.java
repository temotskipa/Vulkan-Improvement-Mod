package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDirtyReason;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldPageKind;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldRevision;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldSectionId;

import java.util.LinkedHashMap;
import java.util.Map;

record RtPtSourceInvalidation(long updateSequence, GpuWorldSectionId sectionId, GpuWorldRevision sourceRevision, GpuWorldPageKind pageKind, GpuWorldDirtyReason reason, long invalidatedPageCount, String fallbackReason) {
    Map<String, Object> asMap() {
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