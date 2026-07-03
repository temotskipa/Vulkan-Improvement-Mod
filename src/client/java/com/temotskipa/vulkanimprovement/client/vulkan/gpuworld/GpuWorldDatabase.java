package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class GpuWorldDatabase {
    static final int DIRTY_GEOMETRY = 1;
    static final int DIRTY_MATERIALS = 1 << 1;
    static final int DIRTY_VISIBILITY = 1 << 2;
    static final int DIRTY_REMOVED = 1 << 3;
    
    private static final GpuWorldDatabase INSTANCE = new GpuWorldDatabase();
    private static final GpuWorldResidencyBudget DEFAULT_RESIDENCY_BUDGET = GpuWorldResidencyBudget.conservativeDefault();
    
    private final Map<Long, SectionRecord> sections = new LinkedHashMap<>();
    private final AtomicLong updateSequence = new AtomicLong();
    private final AtomicLong clearCount = new AtomicLong();
    private GpuWorldMaterialTableContract materialTable = GpuWorldMaterialTableContract.terrainDefault();
    private GpuWorldSectionUpdate lastUpdate;
    private String lastClearReason = "not cleared";
    
    private GpuWorldDatabase() {
    }
    
    public static GpuWorldDatabase get() {
        return INSTANCE;
    }
    
    private static Map<String, Object> pageKindsMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (GpuWorldPageKind kind : GpuWorldPageKind.values()) {
            map.put(kind.diagnosticName(), kind.asMap());
        }
        return map;
    }
    
    synchronized void resetForTests() {
        this.sections.clear();
        this.updateSequence.set(0L);
        this.clearCount.set(0L);
        this.materialTable = GpuWorldMaterialTableContract.terrainDefault();
        this.lastUpdate = null;
        this.lastClearReason = "not cleared";
        GpuWorldTerrainLayerDirectory.get().resetForTests();
    }
    
    public synchronized GpuWorldSectionUpdate recordTerrainLayerCapture(long sectionNode, int layerOrdinal, int materialId) {
        if (!this.materialTable.containsMaterialId(materialId)) {
            throw new IllegalArgumentException("Terrain material id is outside the GPU material table: " + materialId);
        }
        GpuWorldSectionId id = GpuWorldSectionId.fromSectionNode(sectionNode);
        SectionRecord previous = this.sections.get(sectionNode);
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
        SectionRecord next = new SectionRecord(id, revision, GpuWorldPageKind.CANONICAL_MIRROR, dirtyMask,
                this.materialTable.revision(), capturedLayerMask, materialId, Map.copyOf(materialIdsByLayer),
                GpuWorldDirtyReason.TERRAIN_LAYER_CAPTURED, sequence);
        this.sections.put(sectionNode, next);
        this.lastUpdate = next.asUpdate(sequence, layerOrdinal);
        return this.lastUpdate;
    }
    
    public synchronized GpuWorldSectionUpdate recordSectionRelease(long sectionNode) {
        SectionRecord previous = this.sections.remove(sectionNode);
        GpuWorldSectionId id = previous == null ? GpuWorldSectionId.fromSectionNode(sectionNode) : previous.section();
        GpuWorldRevision revision = previous == null ? GpuWorldRevision.initial().next() : previous.revision().next();
        long sequence = this.updateSequence.incrementAndGet();
        GpuWorldTerrainLayerDirectory.get().removeSection(sectionNode);
        this.lastUpdate = new GpuWorldSectionUpdate(sequence, id, revision, GpuWorldPageKind.CANONICAL_MIRROR,
                GpuWorldDirtyReason.SECTION_RELEASED, DIRTY_REMOVED, this.materialTable.revision(),
                previous == null ? this.materialTable.defaultMaterialId() : previous.latestMaterialId(), -1);
        return this.lastUpdate;
    }
    
    public synchronized void clear(String reason) {
        this.sections.clear();
        this.clearCount.incrementAndGet();
        this.updateSequence.incrementAndGet();
        this.lastClearReason = reason == null || reason.isBlank() ? "unspecified" : reason;
        GpuWorldTerrainLayerDirectory.get().clear(this.lastClearReason);
    }

    public synchronized Optional<GpuWorldSectionUpdate> currentTerrainLayerUpdate(long sectionNode, int layerOrdinal) {
        SectionRecord current = this.sections.get(sectionNode);
        if (current == null || layerOrdinal < 0 || layerOrdinal >= Integer.SIZE
                || (current.capturedLayerMask() & (1 << layerOrdinal)) == 0) {
            return Optional.empty();
        }
        return Optional.of(current.asUpdate(current.lastUpdateSequence(), layerOrdinal));
    }
    
    @SuppressWarnings("unused")
    synchronized GpuWorldMaterialTableContract advanceMaterialTableRevision() {
        this.materialTable = this.materialTable.nextRevision();
        this.updateSequence.incrementAndGet();
        return this.materialTable;
    }
    
    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("authority", "cpu-chunk-state");
        map.put("liveSections", this.sections.size());
        map.put("updateSequence", this.updateSequence.get());
        map.put("clearCount", this.clearCount.get());
        map.put("lastClearReason", this.lastClearReason);
        map.put("dirtyGeometryBit", DIRTY_GEOMETRY);
        map.put("dirtyMaterialsBit", DIRTY_MATERIALS);
        map.put("dirtyVisibilityBit", DIRTY_VISIBILITY);
        map.put("dirtyRemovedBit", DIRTY_REMOVED);
        map.put("materialTable", this.materialTable.asMap());
        map.put("residencyBudget", DEFAULT_RESIDENCY_BUDGET.asMap());
        map.put("pipelineStages", GpuWorldPipelineStage.asDiagnostics());
        map.put("terrainLayerDirectory", GpuWorldTerrainLayerDirectory.get().asMap());
        map.put("pageKinds", pageKindsMap());
        map.put("lastUpdate", this.lastUpdate == null ? Map.of("present", false) : this.lastUpdate.asMap());
        map.put("sampleSections", sampleSections());
        return map;
    }
    
    private Map<String, Object> sampleSections() {
        Map<String, Object> map = new LinkedHashMap<>();
        int count = 0;
        for (SectionRecord section : this.sections.values()) {
            map.put(Long.toString(section.section().sectionNode()), section.asMap());
            count++;
            if (count >= 8) {
                break;
            }
        }
        return map;
    }
    
    private record SectionRecord(GpuWorldSectionId section, GpuWorldRevision revision, GpuWorldPageKind pageKind,
                                 int dirtyMask, int materialTableRevision, int capturedLayerMask, int latestMaterialId,
                                 Map<Integer, Integer> materialIdsByLayer, GpuWorldDirtyReason lastReason,
                                 long lastUpdateSequence) {
        private GpuWorldSectionUpdate asUpdate(long sequence, int layerOrdinal) {
            int materialId = this.materialIdsByLayer.getOrDefault(layerOrdinal, this.latestMaterialId);
            return new GpuWorldSectionUpdate(sequence, this.section, this.revision, this.pageKind, this.lastReason,
                    this.dirtyMask, this.materialTableRevision, materialId, layerOrdinal);
        }
        
        private Map<String, Object> asMap() {
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
}
