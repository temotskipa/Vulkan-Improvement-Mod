package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainMaterialRecord;

import java.lang.foreign.MemorySegment;

public record GpuMaterialRecord(int materialId, int flags, int blockAtlasWidth, int blockAtlasHeight, int blockAtlasBaseMip, int blockAtlasMipLevels, int lightmapWidth, int lightmapHeight, int normalTextureIndex, int specularTextureIndex, int emissionTextureIndex, int tintFlags, int alphaMode, int renderLayerOrdinal, int materialDomain, int reserved0) {
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
		}
		else if (alphaMode == GpuMaterialAlphaMode.BLENDED) {
			flags |= FLAG_ALPHA_BLENDED;
		}
		return new GpuMaterialRecord(materialId, flags, blockAtlas.width(), blockAtlas.height(), blockAtlas.baseMipLevel(), blockAtlas.mipLevels(), lightmap.width(), lightmap.height(), MISSING_TEXTURE_INDEX, MISSING_TEXTURE_INDEX, MISSING_TEXTURE_INDEX, TINT_FLAGS_NONE, alphaMode.id(), renderLayerOrdinal, MATERIAL_DOMAIN_TERRAIN, 0);
	}
	
	/**
	 * Writes the record at the start of a segment, in the order of {@link TerrainMaterialRecord}.
	 */
	void write(MemorySegment target) {
		int[] values = {this.materialId, this.flags, this.blockAtlasWidth, this.blockAtlasHeight, this.blockAtlasBaseMip, this.blockAtlasMipLevels, this.lightmapWidth, this.lightmapHeight, this.normalTextureIndex, this.specularTextureIndex, this.emissionTextureIndex, this.tintFlags, this.alphaMode, this.renderLayerOrdinal, this.materialDomain, this.reserved0};
		MemorySegment.copy(values, 0, target, TerrainGpuRecords.INT, 0L, INT_COUNT);
	}
}