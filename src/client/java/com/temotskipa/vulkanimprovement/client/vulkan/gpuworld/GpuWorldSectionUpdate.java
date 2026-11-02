package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

public record GpuWorldSectionUpdate(long sequence, GpuWorldSectionId section, GpuWorldRevision revision, GpuWorldPageKind pageKind, GpuWorldDirtyReason reason, int dirtyMask, int materialTableRevision, int materialId, int layerOrdinal) {
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
}