package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class GpuWorldDatabase {
	static final int DIRTY_GEOMETRY = 1;
	static final int DIRTY_MATERIALS = 1 << 1;
	static final int DIRTY_REMOVED = 1 << 3;
	
	private static final GpuWorldResidencyBudget DEFAULT_RESIDENCY_BUDGET = GpuWorldResidencyBudget.conservativeDefault();
	private static final long SECTION_PAGE_BYTES_PENDING_REAL_ALLOCATION = 0L;
	private static final GpuWorldDatabase INSTANCE = new GpuWorldDatabase();
	
	private final Map<Long, GpuWorldSectionRecord> sections = new LinkedHashMap<>();
	private final AtomicLong updateSequence = new AtomicLong();
	private final AtomicLong clearCount = new AtomicLong();
	private final GpuWorldResidencyStats residencyStats = GpuWorldResidencyStats.empty(DEFAULT_RESIDENCY_BUDGET);
	private final GpuWorldVisibilityCommandStats visibilityCommandStats = GpuWorldVisibilityCommandStats.empty();
	private GpuWorldMaterialTableContract materialTable = GpuWorldMaterialTableContract.terrainDefault();
	private GpuWorldSectionUpdate lastUpdate;
	private String lastClearReason = "not cleared";
	private GpuWorldResidentTerrainUsage residentTerrain = new GpuWorldResidentTerrainUsage(0L, 0L, 0L, 0L);
	
	private GpuWorldDatabase() {
	}
	
	public static GpuWorldDatabase get() {
		return INSTANCE;
	}
	
	synchronized void resetForTests() {
		this.sections.clear();
		this.updateSequence.set(0L);
		this.clearCount.set(0L);
		this.materialTable = GpuWorldMaterialTableContract.terrainDefault();
		this.lastUpdate = null;
		this.lastClearReason = "not cleared";
		this.residencyStats.resetForTests();
		this.visibilityCommandStats.resetForTests();
		GpuWorldTerrainLayerDirectory.get().resetForTests();
	}
	
	public synchronized GpuWorldSectionUpdate recordTerrainLayerCapture(long sectionNode, int layerOrdinal, int materialId) {
		if (!this.materialTable.containsMaterialId(materialId)) {
			throw new IllegalArgumentException("Terrain material id is outside the GPU material table: " + materialId);
		}
		GpuWorldSectionId id = GpuWorldSectionId.fromSectionNode(sectionNode);
		GpuWorldSectionRecord previous = this.sections.get(sectionNode);
		if (previous == null && !this.residencyStats.tryReservePage(GpuWorldPageKind.CANONICAL_MIRROR, SECTION_PAGE_BYTES_PENDING_REAL_ALLOCATION)) {
			throw new IllegalStateException("GPU world residency budget refused canonical section page");
		}
		GpuWorldRevision revision = previous == null ? GpuWorldRevision.initial().next() : previous.revision().next();
		int capturedLayerMask = previous == null ? 0 : previous.capturedLayerMask();
		if (layerOrdinal >= 0 && layerOrdinal < Integer.SIZE) {
			capturedLayerMask |= 1 << layerOrdinal;
		}
		int dirtyMask = DIRTY_GEOMETRY | DIRTY_MATERIALS;
		long sequence = this.updateSequence.incrementAndGet();
		Map<Integer, Integer> materialIdsByLayer = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous.materialIdsByLayer());
		if (layerOrdinal >= 0) {
			materialIdsByLayer.put(layerOrdinal, materialId);
		}
		GpuWorldSectionRecord next = new GpuWorldSectionRecord(id, revision, GpuWorldPageKind.CANONICAL_MIRROR, dirtyMask, this.materialTable.revision(), capturedLayerMask, materialId, Map.copyOf(materialIdsByLayer), GpuWorldDirtyReason.TERRAIN_LAYER_CAPTURED, sequence);
		this.sections.put(sectionNode, next);
		this.lastUpdate = next.asUpdate(sequence, layerOrdinal);
		return this.lastUpdate;
	}
	
	public synchronized GpuWorldSectionUpdate recordSectionRelease(long sectionNode) {
		GpuWorldSectionRecord previous = this.sections.remove(sectionNode);
		if (previous != null) {
			this.residencyStats.releasePage(previous.pageKind(), SECTION_PAGE_BYTES_PENDING_REAL_ALLOCATION);
		}
		GpuWorldSectionId id = previous == null ? GpuWorldSectionId.fromSectionNode(sectionNode) : previous.section();
		GpuWorldRevision revision = previous == null ? GpuWorldRevision.initial().next() : previous.revision().next();
		long sequence = this.updateSequence.incrementAndGet();
		GpuWorldTerrainLayerDirectory.get().removeSection(sectionNode);
		this.lastUpdate = new GpuWorldSectionUpdate(sequence, id, revision, GpuWorldPageKind.CANONICAL_MIRROR, GpuWorldDirtyReason.SECTION_RELEASED, DIRTY_REMOVED, this.materialTable.revision(), previous == null ? this.materialTable.defaultMaterialId() : previous.latestMaterialId(), -1);
		return this.lastUpdate;
	}
	
	public synchronized void clear(String reason) {
		this.sections.clear();
		this.residencyStats.resetLivePages();
		this.clearCount.incrementAndGet();
		this.updateSequence.incrementAndGet();
		this.lastClearReason = reason == null || reason.isBlank() ? "unspecified" : reason;
		GpuWorldTerrainLayerDirectory.get().clear(this.lastClearReason);
	}
	
	public synchronized Optional<GpuWorldSectionUpdate> currentTerrainLayerUpdate(long sectionNode, int layerOrdinal) {
		GpuWorldSectionRecord current = this.sections.get(sectionNode);
		if (current == null || layerOrdinal < 0 || layerOrdinal >= Integer.SIZE || (current.capturedLayerMask() & (1 << layerOrdinal)) == 0) {
			return Optional.empty();
		}
		return Optional.of(current.asUpdate(current.lastUpdateSequence(), layerOrdinal));
	}
	
	public synchronized void recordVisibilityCommandPass(long inputSectionCount, long culledSectionCount, long visibleSectionCount, long emittedCommandCount, long overflowedCommandCount, long fallbackCommandCount) {
		this.visibilityCommandStats.recordPass(inputSectionCount, culledSectionCount, visibleSectionCount, emittedCommandCount, overflowedCommandCount, fallbackCommandCount);
	}
	
	/**
	 * What the resident terrain pages occupy on the GPU, as their allocators count it.
	 *
	 * @param allocationRefusals pages that found no room in a buffer; the buffers are then grown
	 */
	public synchronized void recordResidentTerrainUsage(long livePages, long usedBytes, long capacityBytes, long allocationRefusals) {
		this.residentTerrain = new GpuWorldResidentTerrainUsage(livePages, usedBytes, capacityBytes, allocationRefusals);
	}
	
	public synchronized String describe() {
		return "liveSections=" + this.sections.size() + ", updateSequence=" + this.updateSequence.get() + ", clearCount=" + this.clearCount.get() + ", lastClearReason=" + this.lastClearReason + ", residency=[" + this.residencyStats.describe() + "], residentTerrain=" + this.residentTerrain + ", visibility=[" + this.visibilityCommandStats.describe() + "], layerDirectory=[" + GpuWorldTerrainLayerDirectory.get().describe() + "], lastUpdate=" + this.lastUpdate;
	}
	
	synchronized int liveSections() {
		return this.sections.size();
	}
	
	long updateSequence() {
		return this.updateSequence.get();
	}
	
	long clearCount() {
		return this.clearCount.get();
	}
	
	synchronized String lastClearReason() {
		return this.lastClearReason;
	}
	
	GpuWorldResidencyStats residencyStats() {
		return this.residencyStats;
	}
	
	GpuWorldVisibilityCommandStats visibilityCommandStats() {
		return this.visibilityCommandStats;
	}
	
	synchronized GpuWorldSectionRecord section(long sectionNode) {
		return this.sections.get(sectionNode);
	}
}