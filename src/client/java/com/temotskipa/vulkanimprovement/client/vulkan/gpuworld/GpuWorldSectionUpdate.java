package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;

public record GpuWorldSectionUpdate(long sequence, GpuWorldSectionId section, GpuWorldRevision revision,
                                    GpuWorldPageKind pageKind, GpuWorldDirtyReason reason, int dirtyMask,
                                    int materialTableRevision, int materialId, int layerOrdinal) {
    public GpuWorldSectionUpdate {
        if (sequence <= 0L) {
            throw new IllegalArgumentException("GPU world update sequence must be positive: " + sequence);
        }
        if (revision.uninitialized()) {
            throw new IllegalArgumentException("GPU world update revision must be initialized.");
        }
        if (dirtyMask == 0) {
            throw new IllegalArgumentException("GPU world update dirty mask must not be empty.");
        }
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sequence", this.sequence);
        map.put("section", this.section.asMap());
        map.put("revision", this.revision.value());
        map.put("pageKind", this.pageKind.diagnosticName());
        map.put("reason", this.reason.diagnosticName());
        map.put("dirtyMask", this.dirtyMask);
        map.put("materialTableRevision", this.materialTableRevision);
        map.put("materialId", this.materialId);
        map.put("layerOrdinal", this.layerOrdinal);
        return map;
    }
}
