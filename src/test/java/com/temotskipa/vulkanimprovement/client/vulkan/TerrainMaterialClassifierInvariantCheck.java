package com.temotskipa.vulkanimprovement.client.vulkan;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TerrainMaterialClassifierInvariantCheck {
    private TerrainMaterialClassifierInvariantCheck() {
    }
    
    public static void main(String[] args) {
        checkMaterialIdsFitTableAndAreStable();
        checkLayerAlphaClassification();
        checkTerrainLayerRecords();
        checkDiagnostics();
    }
    
    private static void checkMaterialIdsFitTableAndAreStable() {
        Set<Integer> materialIds = new HashSet<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            int materialId = TerrainMaterialClassifier.materialIdForLayer(layer);
            require(materialId == layer.ordinal(), layer.label() + " material ID must match render-layer ordinal");
            require(materialId >= 0 && materialId < TerrainGpuLayout.MATERIAL_TABLE_CAPACITY, layer.label() + " material ID must fit the terrain material table");
            require(materialIds.add(materialId), layer.label() + " material ID must be unique");
        }
    }
    
    private static void checkLayerAlphaClassification() {
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            GpuMaterialAlphaMode alphaMode = TerrainMaterialClassifier.alphaModeForLayer(layer);
            String label = layer.label();
            if (label.contains("translucent")) {
                require(alphaMode == GpuMaterialAlphaMode.BLENDED, "translucent layer must classify as blended");
            } else if (label.contains("cutout") || label.contains("tripwire")) {
                require(alphaMode == GpuMaterialAlphaMode.MASKED, label + " layer must classify as masked");
            } else {
                require(alphaMode == GpuMaterialAlphaMode.OPAQUE, label + " layer must classify as opaque");
            }
        }
    }
    
    private static void checkTerrainLayerRecords() {
        GpuMaterialRecord.TextureInfo blockAtlas = new GpuMaterialRecord.TextureInfo(true, 1024, 512, 0, 5);
        GpuMaterialRecord.TextureInfo lightmap = new GpuMaterialRecord.TextureInfo(true, 16, 16, 0, 1);
        ByteBuffer table = ByteBuffer.allocate(TerrainGpuLayout.MATERIAL_TABLE_CAPACITY * TerrainGpuLayout.MATERIAL_RECORD_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        TerrainMaterialClassifier.writeTerrainLayerRecords(table, blockAtlas, lightmap);
        
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            int materialId = TerrainMaterialClassifier.materialIdForLayer(layer);
            int[] encoded = readRecord(table, materialId);
            GpuMaterialAlphaMode alphaMode = TerrainMaterialClassifier.alphaModeForLayer(layer);
            require(encoded[0] == materialId, layer.label() + " material record must encode material ID");
            require(encoded[1] == expectedFlags(alphaMode), layer.label() + " material record must encode readiness and alpha flags");
            require(encoded[12] == alphaMode.id(), layer.label() + " material record must encode alpha mode");
            require(encoded[13] == layer.ordinal(), layer.label() + " material record must encode render-layer ordinal");
            require(encoded[14] == GpuMaterialRecord.MATERIAL_DOMAIN_TERRAIN, layer.label() + " material record must encode terrain domain");
        }
    }
    
    private static void checkDiagnostics() {
        Map<String, Object> diagnostics = TerrainMaterialClassifier.asMap();
        require(diagnostics.size() == ChunkSectionLayer.values().length, "material diagnostics must include every chunk section layer");
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            Object value = diagnostics.get(layer.label());
            require(value instanceof Map<?, ?>, layer.label() + " material diagnostics must be a map");
            Map<?, ?> layerMap = (Map<?, ?>) value;
            require(layerMap.get("materialId").equals(layer.ordinal()), layer.label() + " diagnostics must include material ID");
            require(layerMap.get("alphaMode").equals(TerrainMaterialClassifier.alphaModeForLayer(layer).name()), layer.label() + " diagnostics must include alpha mode");
            require(layerMap.get("renderLayerOrdinal").equals(layer.ordinal()), layer.label() + " diagnostics must include render-layer ordinal");
        }
    }
    
    private static int expectedFlags(GpuMaterialAlphaMode alphaMode) {
        int flags = GpuMaterialRecord.FLAG_BLOCK_ATLAS_READY | GpuMaterialRecord.FLAG_LIGHTMAP_READY;
        if (alphaMode == GpuMaterialAlphaMode.MASKED) {
            flags |= GpuMaterialRecord.FLAG_ALPHA_MASKED;
        } else if (alphaMode == GpuMaterialAlphaMode.BLENDED) {
            flags |= GpuMaterialRecord.FLAG_ALPHA_BLENDED;
        }
        return flags;
    }
    
    private static int[] readRecord(ByteBuffer table, int materialId) {
        ByteBuffer duplicate = table.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        duplicate.position(materialId * TerrainGpuLayout.MATERIAL_RECORD_STRIDE);
        int[] encoded = new int[GpuMaterialRecord.INT_COUNT];
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = duplicate.getInt();
        }
        return encoded;
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
