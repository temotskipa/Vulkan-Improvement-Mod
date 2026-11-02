package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import com.temotskipa.vulkanimprovement.client.vulkan.rtpt.RtPtAccelerationDataRegistry;
import com.temotskipa.vulkanimprovement.client.vulkan.rtpt.RtPtAccelerationDomain;
import com.temotskipa.vulkanimprovement.client.vulkan.rtpt.RtPtAccelerationPage;
import com.temotskipa.vulkanimprovement.client.vulkan.rtpt.RtPtSourceInvalidation;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.GpuMaterialRecord;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.core.SectionPos;

public final class GpuWorldDatabaseInvariantCheck {
	private GpuWorldDatabaseInvariantCheck() {
	}
	
	
	static void main() {
		checkSectionIdentity();
		checkRevisionRollover();
		checkMaterialTableContract();
		checkPageKindAuthority();
		checkResidencyBudget();
		checkResidencyStatsContract();
		checkVisibilityCommandStatsContract();
		checkCaptureReleaseAndClearDiagnostics();
		checkCanonicalUpdatesInvalidateAccelerationPages();
		checkInvalidUpdatesRejected();
	}
	
	private static void checkSectionIdentity() {
		long sectionNode = SectionPos.asLong(3, 4, -5);
		GpuWorldSectionId id = GpuWorldSectionId.fromSectionNode(sectionNode);
		
		require(id.sectionNode() == sectionNode, "section id must preserve section node");
		require(id.sectionX() == 3 && id.sectionY() == 4 && id.sectionZ() == -5, "section id must decode coordinates");
		requireThrows(() -> new GpuWorldSectionId(sectionNode, 3, 4, -4), "mismatched section coordinates must be rejected");
		
		GpuWorldSectionId fromBlock = GpuWorldSectionId.fromBlockPosition(48, 79, -65);
		require(fromBlock.sectionX() == 3 && fromBlock.sectionY() == 4 && fromBlock.sectionZ() == -5, "block positions must normalize to section identity");
	}
	
	private static void checkRevisionRollover() {
		require(GpuWorldRevision.initial().uninitialized(), "initial revision must be the uninitialized sentinel");
		require(GpuWorldRevision.initial().next().value() == 1L, "first initialized revision must be 1");
		require(GpuWorldRevision.of(Long.MAX_VALUE).next().value() == 1L, "revision rollover must skip the uninitialized sentinel");
		requireThrows(() -> GpuWorldRevision.of(-1L), "negative revisions must be rejected");
	}
	
	private static void checkMaterialTableContract() {
		GpuWorldMaterialTableContract materialTable = GpuWorldMaterialTableContract.terrainDefault();
		
		require(materialTable.revision() == 1, "default terrain material table revision must be initialized");
		require(materialTable.capacity() == TerrainGpuLayout.MATERIAL_TABLE_CAPACITY, "material table capacity must come from terrain layout");
		require(materialTable.recordStride() == TerrainGpuLayout.MATERIAL_RECORD_STRIDE, "material table stride must come from terrain layout");
		require(materialTable.containsMaterialId(GpuMaterialRecord.DEFAULT_TERRAIN_MATERIAL_ID), "default material must fit material table");
		require(!materialTable.containsMaterialId(materialTable.capacity()), "material id at capacity must be outside table");
		require(materialTable.nextRevision().revision() == 2, "material table revision must advance");
		
		requireThrows(() -> new GpuWorldMaterialTableContract(0, 1, GpuMaterialRecord.BYTES, 0), "zero material table revision must be rejected");
		requireThrows(() -> new GpuWorldMaterialTableContract(1, 0, GpuMaterialRecord.BYTES, 0), "empty material table must be rejected");
		requireThrows(() -> new GpuWorldMaterialTableContract(1, 1, GpuMaterialRecord.BYTES + Integer.BYTES, 0), "wrong material stride must be rejected");
		requireThrows(() -> new GpuWorldMaterialTableContract(1, 1, GpuMaterialRecord.BYTES, 1), "default material outside table must be rejected");
	}
	
	private static void checkPageKindAuthority() {
		require(GpuWorldPageKind.CANONICAL_MIRROR.mirrorsCpuChunkState(), "canonical mirror pages must declare CPU chunk mirroring");
		require(!GpuWorldPageKind.CANONICAL_MIRROR.gameplayAuthoritative(), "canonical mirror pages must not claim gameplay authority");
		require(!GpuWorldPageKind.RENDER_ONLY_LOD.mirrorsCpuChunkState(), "LOD pages must not claim CPU chunk mirroring");
		require(!GpuWorldPageKind.RENDER_ONLY_LOD.gameplayAuthoritative(), "LOD pages must not claim gameplay authority");
		require(!GpuWorldPageKind.PREDICTIVE_DISTANT.mirrorsCpuChunkState(), "predictive pages must not claim CPU chunk mirroring");
		require(!GpuWorldPageKind.PREDICTIVE_DISTANT.gameplayAuthoritative(), "predictive pages must not claim gameplay authority");
	}
	
	private static void checkResidencyBudget() {
		GpuWorldResidencyBudget budget = GpuWorldResidencyBudget.conservativeDefault();
		
		require(budget.fitsWithinMaxResidentBytes(), "conservative GPU world residency budget must fit inside its cap");
		require(budget.pageKindBudgetBytes(GpuWorldPageKind.CANONICAL_MIRROR) > budget.pageKindBudgetBytes(GpuWorldPageKind.RENDER_ONLY_LOD), "canonical mirror pages must receive the primary residency budget");
		require(budget.derivedWorkBudgetBytes() > 0L, "derived visibility and command work must have a budget");
	}
	
	private static void checkResidencyStatsContract() {
		GpuWorldResidencyBudget budget = new GpuWorldResidencyBudget(64L, 16L, 16L, 8L, 8L, 112L);
		GpuWorldResidencyStats stats = GpuWorldResidencyStats.empty(budget);
		
		require(stats.tryReservePage(GpuWorldPageKind.CANONICAL_MIRROR, 16L), "first canonical page must fit");
		require(stats.tryReservePage(GpuWorldPageKind.CANONICAL_MIRROR, 48L), "second canonical page must fill the budget");
		require(!stats.tryReservePage(GpuWorldPageKind.CANONICAL_MIRROR, 1L), "over-budget canonical page must be refused");
		stats.recordEviction(GpuWorldPageKind.CANONICAL_MIRROR, 16L);
		
		require(stats.livePageCount() == 1L, "eviction must reduce live page count");
		require(stats.usedResidentBytes() == 48L, "eviction must reduce used resident bytes");
		require(stats.evictedPageCount() == 1L, "eviction count must be reported");
		require(stats.evictedBytes() == 16L, "evicted bytes must be reported");
		require(stats.overBudgetRefusals() == 1L, "over-budget refusals must be reported");
		require(stats.overBudgetRefusedBytes() == 1L, "over-budget refused bytes must be reported");
		require(stats.livePageCount(GpuWorldPageKind.CANONICAL_MIRROR) == 1L, "canonical live page count must be reported by kind");
		require(stats.usedBytes(GpuWorldPageKind.CANONICAL_MIRROR) == 48L, "canonical used bytes must be reported by kind");
		
		requireThrows(() -> stats.tryReservePage(GpuWorldPageKind.RENDER_ONLY_LOD, -1L), "negative page bytes must be rejected");
		requireThrows(() -> stats.releasePage(GpuWorldPageKind.RENDER_ONLY_LOD, 1L), "releasing a missing page must be rejected");
	}
	
	private static void checkVisibilityCommandStatsContract() {
		GpuWorldVisibilityCommandStats stats = GpuWorldVisibilityCommandStats.empty();
		stats.recordPass(12L, 5L, 7L, 6L, 1L, 1L);
		stats.recordPass(3L, 1L, 2L, 2L, 0L, 0L);
		
		require(stats.compactionPassCount() == 2L, "compaction pass count must be reported");
		require(stats.inputSectionCount() == 15L, "input section count must accumulate");
		require(stats.culledSectionCount() == 6L, "culled section count must accumulate");
		require(stats.visibleSectionCount() == 9L, "visible section count must accumulate");
		require(stats.emittedCommandCount() == 8L, "emitted command count must accumulate");
		require(stats.overflowedCommandCount() == 1L, "overflowed command count must accumulate");
		require(stats.fallbackCommandCount() == 1L, "fallback command count must accumulate");
		
		GpuWorldVisibilityCommandPass lastPass = stats.lastPass();
		require(lastPass.inputSectionCount() == 3L, "last pass must expose input sections");
		require(lastPass.visibleSectionCount() == 2L, "last pass must expose visible sections");
		require(lastPass.emittedCommandCount() == 2L, "last pass must expose emitted commands");
		
		GpuWorldDatabase database = GpuWorldDatabase.get();
		database.resetForTests();
		database.recordVisibilityCommandPass(4L, 1L, 3L, 2L, 1L, 0L);
		require(database.visibilityCommandStats().inputSectionCount() == 4L, "the database must count visibility inputs");
		require(database.visibilityCommandStats().overflowedCommandCount() == 1L, "the database must count command overflows");
		database.resetForTests();
		require(database.visibilityCommandStats().compactionPassCount() == 0L, "test reset must clear visibility command stats");
		
		requireThrows(() -> stats.recordPass(-1L, 0L, 0L, 0L, 0L, 0L), "negative visibility counts must be rejected");
		requireThrows(() -> stats.recordPass(1L, 1L, 1L, 0L, 0L, 0L), "culled plus visible sections cannot exceed inputs");
	}
	
	private static void checkCaptureReleaseAndClearDiagnostics() {
		GpuWorldDatabase database = GpuWorldDatabase.get();
		database.resetForTests();
		
		long sectionNode = SectionPos.asLong(1, 2, 3);
		GpuWorldSectionUpdate first = database.recordTerrainLayerCapture(sectionNode, 0, 0);
		require(first.sequence() == 1L, "first capture must have sequence 1");
		require(first.revision().value() == 1L, "first capture must initialize section revision");
		require(first.pageKind() == GpuWorldPageKind.CANONICAL_MIRROR, "terrain captures must create canonical mirror pages");
		require(first.reason() == GpuWorldDirtyReason.TERRAIN_LAYER_CAPTURED, "terrain capture update reason must be explicit");
		require(first.dirtyMask() == (GpuWorldDatabase.DIRTY_GEOMETRY | GpuWorldDatabase.DIRTY_MATERIALS), "terrain capture must mark geometry and material dirty");
		
		GpuWorldSectionUpdate second = database.recordTerrainLayerCapture(sectionNode, 2, 2);
		require(second.sequence() == 2L, "second capture must advance sequence");
		require(second.revision().value() == 2L, "second capture must advance section revision");
		
		require(database.liveSections() == 1, "one captured section must be live");
		require(database.updateSequence() == 2L, "the database must count updates");
		GpuWorldResidencyStats residencyStats = database.residencyStats();
		require(residencyStats.livePageCount() == 1L, "recapturing a section must not duplicate resident pages");
		require(residencyStats.livePageCount(GpuWorldPageKind.CANONICAL_MIRROR) == 1L, "database residency stats must count canonical mirror pages");
		
		GpuWorldSectionRecord section = database.section(sectionNode);
		require(section.capturedLayerMask() == 0b0101, "captured layer mask must accumulate captured terrain layers");
		require(section.latestMaterialId() == 2, "a section must keep its latest material id");
		
		GpuWorldSectionUpdate release = database.recordSectionRelease(sectionNode);
		require(release.sequence() == 3L, "release must advance sequence");
		require(release.reason() == GpuWorldDirtyReason.SECTION_RELEASED, "release reason must be explicit");
		require(release.dirtyMask() == GpuWorldDatabase.DIRTY_REMOVED, "release must mark removal dirty");
		require(database.liveSections() == 0, "release must remove the live section");
		require(database.residencyStats().livePageCount() == 0L, "release must remove the resident page");
		
		database.clear("test clear");
		require(database.clearCount() == 1L, "clear must increment clear count");
		require(database.lastClearReason().equals("test clear"), "clear must preserve reason");
		require(database.liveSections() == 0, "clear must leave no live sections");
		
		database.resetForTests();
	}
	
	private static void checkCanonicalUpdatesInvalidateAccelerationPages() {
		GpuWorldDatabase database = GpuWorldDatabase.get();
		RtPtAccelerationDataRegistry accelerationData = RtPtAccelerationDataRegistry.get();
		database.resetForTests();
		accelerationData.resetForTests();
		long sectionNode = SectionPos.asLong(6, 7, 8);
		long otherSectionNode = SectionPos.asLong(6, 7, 9);
		GpuWorldSectionUpdate first = database.recordTerrainLayerCapture(sectionNode, 0, 0);
		GpuWorldSectionUpdate second = database.recordTerrainLayerCapture(sectionNode, 1, 1);
		GpuWorldSectionUpdate other = database.recordTerrainLayerCapture(otherSectionNode, 0, 0);
		accelerationData.registerPage(accelerationPage(first, 101L, "old canonical page"));
		accelerationData.registerPage(accelerationPage(second, 102L, "current canonical page"));
		accelerationData.registerPage(accelerationPage(other, 103L, "other section canonical page"));
		accelerationData.invalidateSource(second, "canonical source revision advanced");
		require(accelerationData.registeredPageCount() == 3L, "registry must count pages registered before invalidation");
		require(accelerationData.livePageCount() == 2L, "source invalidation must leave current-revision and other-section pages live");
		require(accelerationData.pendingRebuildCount() == 1L, "source invalidation must mark stale pages pending rebuild");
		require(accelerationData.sourceInvalidationCount() == 1L, "registry must count source invalidation events");
		require(accelerationData.sourceInvalidatedPageCount() == 1L, "registry must count stale pages invalidated by source updates");
		RtPtSourceInvalidation lastInvalidation = accelerationData.lastSourceInvalidation();
		require(lastInvalidation.updateSequence() == 2L, "source invalidation must preserve the GPU world update sequence");
		require(lastInvalidation.sourceRevision().value() == 2L, "source invalidation must preserve the canonical source revision");
		require(lastInvalidation.pageKind() == GpuWorldPageKind.CANONICAL_MIRROR, "source invalidation must preserve canonical page kind");
		require(lastInvalidation.invalidatedPageCount() == 1L, "last source invalidation must expose affected page count");
		require(accelerationData.fallbackReasonCount("canonical source revision advanced") == 1L, "source invalidation reason must be counted");
		accelerationData.resetForTests();
		database.resetForTests();
	}
	
	private static void checkInvalidUpdatesRejected() {
		long sectionNode = SectionPos.asLong(0, 0, 0);
		GpuWorldSectionId section = GpuWorldSectionId.fromSectionNode(sectionNode);
		
		requireThrows(() -> new GpuWorldSectionUpdate(0L, section, GpuWorldRevision.of(1L), GpuWorldPageKind.CANONICAL_MIRROR, GpuWorldDirtyReason.TERRAIN_LAYER_CAPTURED, GpuWorldDatabase.DIRTY_GEOMETRY, 1, 0, 0), "zero update sequence must be rejected");
		requireThrows(() -> new GpuWorldSectionUpdate(1L, section, GpuWorldRevision.initial(), GpuWorldPageKind.CANONICAL_MIRROR, GpuWorldDirtyReason.TERRAIN_LAYER_CAPTURED, GpuWorldDatabase.DIRTY_GEOMETRY, 1, 0, 0), "uninitialized update revision must be rejected");
		requireThrows(() -> new GpuWorldSectionUpdate(1L, section, GpuWorldRevision.of(1L), GpuWorldPageKind.CANONICAL_MIRROR, GpuWorldDirtyReason.TERRAIN_LAYER_CAPTURED, 0, 1, 0, 0), "empty dirty mask must be rejected");
		
		GpuWorldDatabase database = GpuWorldDatabase.get();
		database.resetForTests();
		requireThrows(() -> database.recordTerrainLayerCapture(sectionNode, 0, TerrainGpuLayout.MATERIAL_TABLE_CAPACITY), "material ids outside table must be rejected");
	}
	
	private static RtPtAccelerationPage accelerationPage(GpuWorldSectionUpdate update, long handle, String fallbackReason) {
		return new RtPtAccelerationPage(RtPtAccelerationDomain.TERRAIN, update.section(), update.revision(), update.pageKind(), handle, handle + 1000L, fallbackReason);
	}
	
	private static void requireThrows(Runnable action, String message) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(message);
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}