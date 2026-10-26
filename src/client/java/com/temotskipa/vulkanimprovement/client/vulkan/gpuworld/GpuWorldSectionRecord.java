package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;

record GpuWorldSectionRecord(GpuWorldSectionId section, GpuWorldRevision revision, GpuWorldPageKind pageKind, int dirtyMask, int materialTableRevision, int capturedLayerMask, int latestMaterialId, Map<Integer, Integer> materialIdsByLayer, GpuWorldDirtyReason lastReason, long lastUpdateSequence) {
    GpuWorldSectionUpdate asUpdate(long sequence, int layerOrdinal) {
        int materialId = this.materialIdsByLayer.getOrDefault(layerOrdinal, this.latestMaterialId);
        return new GpuWorldSectionUpdate(sequence, this.section, this.revision, this.pageKind, this.lastReason, this.dirtyMask, this.materialTableRevision, materialId, layerOrdinal);
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("section", this.section.asMap());
        map.put("revision", this.revision.value());
        map.put("pageKind", this.pageKind.diagnosticName());
        map.put("dirtyMask", this.dirtyMask);
        map.put("materialTableRevision", this.materialTableRevision);
        map.put("capturedLayerMask", this.capturedLayerMask);
        map.put("materialId", this.latestMaterialId);
        map.put("materialIdsByLayer", this.materialIdsByLayer);
        map.put("lastReason", this.lastReason.diagnosticName());
        map.put("lastUpdateSequence", this.lastUpdateSequence);
        return map;
    }
}