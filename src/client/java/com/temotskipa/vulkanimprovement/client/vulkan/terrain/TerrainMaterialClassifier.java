package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class TerrainMaterialClassifier {
    private TerrainMaterialClassifier() {
    }

    @SuppressWarnings("ConstantValue")
    static int materialIdForLayer(ChunkSectionLayer layer) {
        if (layer == null) {
            return GpuMaterialRecord.DEFAULT_TERRAIN_MATERIAL_ID;
        }
        if (layer.ordinal() >= TerrainGpuLayout.MATERIAL_TABLE_CAPACITY) {
            throw new IllegalStateException("ChunkSectionLayer ordinal exceeds terrain material table capacity: " + layer);
        }
        return layer.ordinal();
    }

    static GpuMaterialAlphaMode alphaModeForLayer(ChunkSectionLayer layer) {
        if (layer == null) {
            return GpuMaterialAlphaMode.OPAQUE;
        }
        String label = layer.label().toLowerCase(Locale.ROOT);
        if (label.contains("translucent")) {
            return GpuMaterialAlphaMode.BLENDED;
        }
        if (label.contains("cutout") || label.contains("tripwire")) {
            return GpuMaterialAlphaMode.MASKED;
        }
        return GpuMaterialAlphaMode.OPAQUE;
    }

    static GpuMaterialRecord recordForLayer(ChunkSectionLayer layer, GpuMaterialRecord.TextureInfo blockAtlas, GpuMaterialRecord.TextureInfo lightmap) {
        return GpuMaterialRecord.terrainLayer(
                materialIdForLayer(layer),
                layer == null ? -1 : layer.ordinal(),
                alphaModeForLayer(layer),
                blockAtlas,
                lightmap
        );
    }

    static void writeTerrainLayerRecords(ByteBuffer target, GpuMaterialRecord.TextureInfo blockAtlas, GpuMaterialRecord.TextureInfo lightmap) {
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            int materialId = materialIdForLayer(layer);
            int position = materialId * TerrainGpuLayout.MATERIAL_RECORD_STRIDE;
            if (position + TerrainGpuLayout.MATERIAL_RECORD_STRIDE > target.capacity()) {
                throw new IllegalArgumentException("Terrain material table is too small for " + layer.label());
            }
            ByteBuffer duplicate = target.duplicate().order(target.order());
            duplicate.position(position);
            recordForLayer(layer, blockAtlas, lightmap).write(duplicate);
        }
    }

    static Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            Map<String, Object> layerMap = new LinkedHashMap<>();
            layerMap.put("materialId", materialIdForLayer(layer));
            layerMap.put("alphaMode", alphaModeForLayer(layer).name());
            layerMap.put("alphaModeId", alphaModeForLayer(layer).id());
            layerMap.put("renderLayerOrdinal", layer.ordinal());
            map.put(layer.label(), layerMap);
        }
        return map;
    }
}
