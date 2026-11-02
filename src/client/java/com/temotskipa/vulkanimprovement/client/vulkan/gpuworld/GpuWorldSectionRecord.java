package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.Map;

record GpuWorldSectionRecord(GpuWorldSectionId section, GpuWorldRevision revision, GpuWorldPageKind pageKind, int dirtyMask, int materialTableRevision, int capturedLayerMask, int latestMaterialId, Map<Integer, Integer> materialIdsByLayer, GpuWorldDirtyReason lastReason, long lastUpdateSequence) {
	GpuWorldSectionUpdate asUpdate(long sequence, int layerOrdinal) {
		int materialId = this.materialIdsByLayer.getOrDefault(layerOrdinal, this.latestMaterialId);
		return new GpuWorldSectionUpdate(sequence, this.section, this.revision, this.pageKind, this.lastReason, this.dirtyMask, this.materialTableRevision, materialId, layerOrdinal);
	}
}