package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class GpuWorldTerrainLayerDirectory {
    private static final GpuWorldTerrainLayerDirectory INSTANCE = new GpuWorldTerrainLayerDirectory();
    
    private final Map<GpuWorldTerrainLayerKey, GpuWorldTerrainLayerRecord> records = new LinkedHashMap<>();
    private final AtomicLong publicationCount = new AtomicLong();
    private final AtomicLong replacementCount = new AtomicLong();
    private final AtomicLong removedSectionCount = new AtomicLong();
    private final AtomicLong clearCount = new AtomicLong();
    private String lastClearReason = "not cleared";
    
    private GpuWorldTerrainLayerDirectory() {
    }
    
    public static GpuWorldTerrainLayerDirectory get() {
        return INSTANCE;
    }
    
    private static String normalizedReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason;
    }
    
    public synchronized GpuWorldTerrainLayerRecord publish(GpuWorldSectionUpdate update, int meshletOffset, int meshletCount, float visibility) {
        GpuWorldTerrainLayerRecord record = GpuWorldTerrainLayerRecord.from(update, meshletOffset, meshletCount, visibility);
        publish(record);
        return record;
    }
    
    public synchronized Optional<GpuWorldTerrainLayerRecord> recordFor(long sectionNode, int layerOrdinal) {
        return Optional.ofNullable(this.records.get(new GpuWorldTerrainLayerKey(sectionNode, layerOrdinal)));
    }
    
    public synchronized void removeLayer(long sectionNode, int layerOrdinal) {
        this.records.remove(new GpuWorldTerrainLayerKey(sectionNode, layerOrdinal));
    }
    
    public synchronized void removeSection(long sectionNode) {
        boolean removed = this.records.keySet().removeIf(key -> key.sectionNode() == sectionNode);
        if (removed) {
            this.removedSectionCount.incrementAndGet();
        }
    }
    
    public synchronized void clear(String reason) {
        this.records.clear();
        this.clearCount.incrementAndGet();
        this.lastClearReason = normalizedReason(reason);
    }
    
    synchronized void resetForTests() {
        this.records.clear();
        this.publicationCount.set(0L);
        this.replacementCount.set(0L);
        this.removedSectionCount.set(0L);
        this.clearCount.set(0L);
        this.lastClearReason = "not cleared";
    }
    
    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("authority", "cpu-chunk-state");
        map.put("keyType", "section-node-layer-ordinal");
        map.put("vanillaDrawKeyed", false);
        map.put("residentLayerRecords", this.records.size());
        map.put("publicationCount", this.publicationCount.get());
        map.put("replacementCount", this.replacementCount.get());
        map.put("removedSectionCount", this.removedSectionCount.get());
        map.put("clearCount", this.clearCount.get());
        map.put("lastClearReason", this.lastClearReason);
        map.put("sampleRecords", sampleRecords());
        return map;
    }
    
    private void publish(GpuWorldTerrainLayerRecord record) {
        GpuWorldTerrainLayerKey key = new GpuWorldTerrainLayerKey(record.section().sectionNode(), record.layerOrdinal());
        GpuWorldTerrainLayerRecord previous = this.records.put(key, record);
        this.publicationCount.incrementAndGet();
        if (previous != null) {
            this.replacementCount.incrementAndGet();
        }
    }
    
    private Map<String, Object> sampleRecords() {
        Map<String, Object> map = new LinkedHashMap<>();
        int count = 0;
        for (GpuWorldTerrainLayerRecord record : this.records.values()) {
            map.put(record.section().sectionNode() + ":" + record.layerOrdinal(), record.asMap());
            count++;
            if (count >= 8) {
                break;
            }
        }
        return map;
    }
}