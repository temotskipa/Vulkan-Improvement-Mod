package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.Objects;

public record GpuWorldTerrainLayerRecord(GpuWorldSectionId section, GpuWorldRevision revision, GpuWorldPageKind pageKind, int layerOrdinal, int materialId, int meshletOffset, int meshletCount, float visibility, long sourceUpdateSequence) {
	public GpuWorldTerrainLayerRecord {
		Objects.requireNonNull(section, "section");
		Objects.requireNonNull(revision, "revision");
		Objects.requireNonNull(pageKind, "pageKind");
		if (revision.uninitialized()) {
			throw new IllegalArgumentException("resident terrain layer revision must be initialized");
		}
		if (pageKind != GpuWorldPageKind.CANONICAL_MIRROR) {
			throw new IllegalArgumentException("resident terrain layers must mirror canonical CPU chunk state");
		}
		if (layerOrdinal < 0) {
			throw new IllegalArgumentException("layerOrdinal must be non-negative: " + layerOrdinal);
		}
		if (materialId < 0) {
			throw new IllegalArgumentException("materialId must be non-negative: " + materialId);
		}
		if (meshletOffset < 0) {
			throw new IllegalArgumentException("meshletOffset must be non-negative: " + meshletOffset);
		}
		if (meshletCount <= 0) {
			throw new IllegalArgumentException("meshletCount must be positive: " + meshletCount);
		}
		if (!Float.isFinite(visibility) || visibility < 0.0F || visibility > 1.0F) {
			throw new IllegalArgumentException("visibility must be finite and normalized: " + visibility);
		}
		if (sourceUpdateSequence <= 0L) {
			throw new IllegalArgumentException("sourceUpdateSequence must be positive: " + sourceUpdateSequence);
		}
	}
	
	public static GpuWorldTerrainLayerRecord from(GpuWorldSectionUpdate update, int meshletOffset, int meshletCount, float visibility) {
		return new GpuWorldTerrainLayerRecord(update.section(), update.revision(), update.pageKind(), update.layerOrdinal(), update.materialId(), meshletOffset, meshletCount, visibility, update.sequence());
	}
}