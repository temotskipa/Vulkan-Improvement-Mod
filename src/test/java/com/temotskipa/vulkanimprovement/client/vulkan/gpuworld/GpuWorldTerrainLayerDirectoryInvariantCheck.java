package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import net.minecraft.core.SectionPos;

import java.util.Map;

public final class GpuWorldTerrainLayerDirectoryInvariantCheck {
    private GpuWorldTerrainLayerDirectoryInvariantCheck() {
    }
    
    
    static void main() {
        checkDirectoryPublishesResidentSectionLayerRecords();
        checkDirectoryReplacesLayerRecords();
        checkReleaseAndClearRemoveResidentRecords();
    }
    
    private static void checkDirectoryPublishesResidentSectionLayerRecords() {
        GpuWorldDatabase database = GpuWorldDatabase.get();
        GpuWorldTerrainLayerDirectory directory = GpuWorldTerrainLayerDirectory.get();
        database.resetForTests();
        directory.resetForTests();
        
        long sectionNode = SectionPos.asLong(3, 5, -7);
        GpuWorldSectionUpdate update = database.recordTerrainLayerCapture(sectionNode, 1, 4);
        GpuWorldTerrainLayerRecord record = directory.publish(update, 128, 16, 0.75F);
        
        require(record.section().sectionNode() == sectionNode, "resident layer record must preserve section node");
        require(record.revision().equals(update.revision()), "resident layer record must preserve GPU world revision");
        require(record.pageKind() == GpuWorldPageKind.CANONICAL_MIRROR, "resident terrain layers must be canonical mirrors");
        require(record.layerOrdinal() == 1, "resident layer record must preserve layer ordinal");
        require(record.materialId() == 4, "resident layer record must preserve material id");
        require(record.meshletOffset() == 128, "resident layer record must preserve meshlet offset");
        require(record.meshletCount() == 16, "resident layer record must preserve meshlet count");
        require(record.visibility() == 0.75F, "resident layer record must preserve visibility");
        require(record.sourceUpdateSequence() == update.sequence(), "resident layer record must preserve source update sequence");
        
        Map<String, Object> diagnostics = directory.asMap();
        require(diagnostics.get("residentLayerRecords").equals(1), "directory diagnostics must count resident records");
        require(diagnostics.get("publicationCount").equals(1L), "directory diagnostics must count publications");
        require(diagnostics.get("vanillaDrawKeyed").equals(Boolean.FALSE), "directory must not be keyed by vanilla draw objects");
        require(diagnostics.get("authority").equals("cpu-chunk-state"), "directory must preserve CPU chunk authority label");
        require(diagnostics.containsKey("sampleRecords"), "directory diagnostics must expose sample records");
        
        Map<String, Object> databaseDiagnostics = database.asMap();
        require(databaseDiagnostics.containsKey("terrainLayerDirectory"), "GPU world diagnostics must expose terrain layer directory");
        
        directory.resetForTests();
        database.resetForTests();
    }
    
    private static void checkDirectoryReplacesLayerRecords() {
        GpuWorldDatabase database = GpuWorldDatabase.get();
        GpuWorldTerrainLayerDirectory directory = GpuWorldTerrainLayerDirectory.get();
        database.resetForTests();
        directory.resetForTests();
        
        long sectionNode = SectionPos.asLong(0, 1, 2);
        GpuWorldSectionUpdate first = database.recordTerrainLayerCapture(sectionNode, 0, 0);
        directory.publish(first, 4, 8, 1.0F);
        GpuWorldSectionUpdate second = database.recordTerrainLayerCapture(sectionNode, 0, 3);
        GpuWorldTerrainLayerRecord replaced = directory.publish(second, 64, 12, 0.5F);
        
        require(replaced.revision().equals(second.revision()), "recapture must replace record with latest revision");
        require(replaced.materialId() == 3, "recapture must replace material id");
        require(replaced.meshletOffset() == 64, "recapture must replace meshlet offset");
        require(replaced.meshletCount() == 12, "recapture must replace meshlet count");
        require(directory.asMap().get("residentLayerRecords").equals(1), "recapture must not duplicate section-layer records");
        require(directory.recordFor(sectionNode, 0).orElseThrow().sourceUpdateSequence() == second.sequence(), "lookup must return latest record");
        
        directory.resetForTests();
        database.resetForTests();
    }
    
    private static void checkReleaseAndClearRemoveResidentRecords() {
        GpuWorldDatabase database = GpuWorldDatabase.get();
        GpuWorldTerrainLayerDirectory directory = GpuWorldTerrainLayerDirectory.get();
        database.resetForTests();
        directory.resetForTests();
        
        long releasedSection = SectionPos.asLong(9, 2, 1);
        long retainedSection = SectionPos.asLong(9, 2, 2);
        directory.publish(database.recordTerrainLayerCapture(releasedSection, 0, 0), 0, 4, 1.0F);
        directory.publish(database.recordTerrainLayerCapture(releasedSection, 1, 1), 4, 4, 1.0F);
        directory.publish(database.recordTerrainLayerCapture(retainedSection, 0, 0), 8, 4, 1.0F);
        
        database.recordSectionRelease(releasedSection);
        require(directory.asMap().get("residentLayerRecords").equals(1), "section release must remove all resident layer records for that section");
        require(directory.recordFor(retainedSection, 0).isPresent(), "section release must retain other sections");
        
        database.clear("directory invariant clear");
        require(directory.asMap().get("residentLayerRecords").equals(0), "database clear must remove all resident layer records");
        
        directory.resetForTests();
        database.resetForTests();
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}