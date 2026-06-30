package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public record GpuMaterialRecord(int materialId, int flags, int blockAtlasWidth, int blockAtlasHeight,
                                int blockAtlasBaseMip, int blockAtlasMipLevels, int lightmapWidth, int lightmapHeight,
                                int normalTextureIndex, int specularTextureIndex, int emissionTextureIndex,
                                int tintFlags, int alphaMode, int renderLayerOrdinal, int materialDomain,
                                int reserved0) {
    public static final int DEFAULT_TERRAIN_MATERIAL_ID = 0;
    static final int INT_COUNT = 16;
    public static final int BYTES = INT_COUNT * Integer.BYTES;
    static final int FLAG_BLOCK_ATLAS_READY = 1;
    static final int FLAG_LIGHTMAP_READY = 1 << 1;
    static final int FLAG_ALPHA_MASKED = 1 << 2;
    static final int FLAG_ALPHA_BLENDED = 1 << 3;
    static final int MISSING_TEXTURE_INDEX = -1;
    static final int TINT_FLAGS_NONE = 0;
    static final int MATERIAL_DOMAIN_TERRAIN = 1;
    
    static GpuMaterialRecord vanillaTerrain(TextureInfo blockAtlas, TextureInfo lightmap) {
        return terrainLayer(DEFAULT_TERRAIN_MATERIAL_ID, 0, GpuMaterialAlphaMode.OPAQUE, blockAtlas, lightmap);
    }
    
    static GpuMaterialRecord terrainLayer(int materialId, int renderLayerOrdinal, GpuMaterialAlphaMode alphaMode, TextureInfo blockAtlas, TextureInfo lightmap) {
        int flags = 0;
        if (blockAtlas.available()) {
            flags |= FLAG_BLOCK_ATLAS_READY;
        }
        if (lightmap.available()) {
            flags |= FLAG_LIGHTMAP_READY;
        }
        if (alphaMode == GpuMaterialAlphaMode.MASKED) {
            flags |= FLAG_ALPHA_MASKED;
        } else if (alphaMode == GpuMaterialAlphaMode.BLENDED) {
            flags |= FLAG_ALPHA_BLENDED;
        }
        return new GpuMaterialRecord(materialId, flags, blockAtlas.width(), blockAtlas.height(), blockAtlas.baseMipLevel(), blockAtlas.mipLevels(), lightmap.width(), lightmap.height(), MISSING_TEXTURE_INDEX, MISSING_TEXTURE_INDEX, MISSING_TEXTURE_INDEX, TINT_FLAGS_NONE, alphaMode.id(), renderLayerOrdinal, MATERIAL_DOMAIN_TERRAIN, 0);
    }
    
    static Map<String, Object> layoutMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("intCount", INT_COUNT);
        map.put("bytes", BYTES);
        map.put("defaultTerrainMaterialId", DEFAULT_TERRAIN_MATERIAL_ID);
        map.put("flagBlockAtlasReady", FLAG_BLOCK_ATLAS_READY);
        map.put("flagLightmapReady", FLAG_LIGHTMAP_READY);
        map.put("flagAlphaMasked", FLAG_ALPHA_MASKED);
        map.put("flagAlphaBlended", FLAG_ALPHA_BLENDED);
        map.put("missingTextureIndex", MISSING_TEXTURE_INDEX);
        map.put("tintFlagsNone", TINT_FLAGS_NONE);
        map.put("materialDomainTerrain", MATERIAL_DOMAIN_TERRAIN);
        return map;
    }
    
    void write(ByteBuffer target) {
        target.putInt(this.materialId);
        target.putInt(this.flags);
        target.putInt(this.blockAtlasWidth);
        target.putInt(this.blockAtlasHeight);
        target.putInt(this.blockAtlasBaseMip);
        target.putInt(this.blockAtlasMipLevels);
        target.putInt(this.lightmapWidth);
        target.putInt(this.lightmapHeight);
        target.putInt(this.normalTextureIndex);
        target.putInt(this.specularTextureIndex);
        target.putInt(this.emissionTextureIndex);
        target.putInt(this.tintFlags);
        target.putInt(this.alphaMode);
        target.putInt(this.renderLayerOrdinal);
        target.putInt(this.materialDomain);
        target.putInt(this.reserved0);
    }
    
    record TextureInfo(boolean available, int width, int height, int baseMipLevel, int mipLevels) {
        static TextureInfo unavailable() {
            return new TextureInfo(false, 0, 0, 0, 0);
        }
    }
}