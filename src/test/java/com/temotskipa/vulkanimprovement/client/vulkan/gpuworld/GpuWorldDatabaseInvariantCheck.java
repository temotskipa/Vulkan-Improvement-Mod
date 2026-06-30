package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.GpuMaterialRecord;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainGpuLayout;
import net.minecraft.core.SectionPos;

import java.util.Map;

public final class GpuWorldDatabaseInvariantCheck {
    private GpuWorldDatabaseInvariantCheck() {
    }
    
    @SuppressWarnings("unused")
    static void main(String[] args) {
        checkSectionIdentity();
        checkRevisionRollover();
        checkMaterialTableContract();
        checkPageKindAuthority();
        checkCaptureReleaseAndClearDiagnostics();
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
        Map<String, Object> canonical = GpuWorldPageKind.CANONICAL_MIRROR.asMap();
        Map<String, Object> lod = GpuWorldPageKind.RENDER_ONLY_LOD.asMap();
        Map<String, Object> predictive = GpuWorldPageKind.PREDICTIVE_DISTANT.asMap();
        require(Boolean.TRUE.equals(canonical.get("mirrorsCpuChunkState")), "canonical mirror pages must declare CPU chunk mirroring");
        require(Boolean.FALSE.equals(canonical.get("gameplayAuthoritative")), "canonical mirror pages must not claim gameplay authority");
        require(Boolean.FALSE.equals(lod.get("mirrorsCpuChunkState")), "LOD pages must not claim CPU chunk mirroring");
        require(Boolean.FALSE.equals(lod.get("gameplayAuthoritative")), "LOD pages must not claim gameplay authority");
        require(Boolean.FALSE.equals(predictive.get("mirrorsCpuChunkState")), "predictive pages must not claim CPU chunk mirroring");
        require(Boolean.FALSE.equals(predictive.get("gameplayAuthoritative")), "predictive pages must not claim gameplay authority");
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
        Map<String, Object> diagnostics = database.asMap();
        require("cpu-chunk-state".equals(diagnostics.get("authority")), "GPU world database must report CPU chunk authority");
        require(diagnostics.get("liveSections").equals(1), "one captured section must be live");
        require(diagnostics.get("updateSequence").equals(2L), "diagnostics must expose update sequence");
        Map<?, ?> samples = child(diagnostics, "sampleSections");
        Map<?, ?> section = child(samples, Long.toString(sectionNode));
        require(section.get("capturedLayerMask").equals(0b0101), "captured layer mask must accumulate captured terrain layers");
        require(section.get("materialId").equals(2), "section diagnostics must report latest material id");
        GpuWorldSectionUpdate release = database.recordSectionRelease(sectionNode);
        require(release.sequence() == 3L, "release must advance sequence");
        require(release.reason() == GpuWorldDirtyReason.SECTION_RELEASED, "release reason must be explicit");
        require(release.dirtyMask() == GpuWorldDatabase.DIRTY_REMOVED, "release must mark removal dirty");
        require(database.asMap().get("liveSections").equals(0), "release must remove the live section");
        database.clear("test clear");
        Map<String, Object> afterClear = database.asMap();
        require(afterClear.get("clearCount").equals(1L), "clear must increment clear count");
        require(afterClear.get("lastClearReason").equals("test clear"), "clear must preserve reason");
        require(afterClear.get("liveSections").equals(0), "clear must leave no live sections");
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
    
    private static Map<?, ?> child(Map<?, ?> map, String key) {
        Object value = map.get(key);
        require(value instanceof Map<?, ?>, key + " diagnostics must be a map");
        return (Map<?, ?>) value;
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