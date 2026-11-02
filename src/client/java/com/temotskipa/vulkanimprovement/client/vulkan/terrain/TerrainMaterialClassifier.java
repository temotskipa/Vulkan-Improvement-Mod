package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.lang.foreign.MemorySegment;
import java.util.Locale;

final class TerrainMaterialClassifier {
	private TerrainMaterialClassifier() {
	}
	
	static void writeTerrainLayerRecords(MemorySegment target, TextureInfo blockAtlas, TextureInfo lightmap) {
		for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
			long position = (long) materialIdForLayer(layer) * TerrainGpuLayout.MATERIAL_RECORD_STRIDE;
			if (position + TerrainGpuLayout.MATERIAL_RECORD_STRIDE > target.byteSize()) {
				throw new IllegalArgumentException("Terrain material table is too small for " + layer.label());
			}
			recordForLayer(layer, blockAtlas, lightmap).write(target.asSlice(position, TerrainGpuLayout.MATERIAL_RECORD_STRIDE));
		}
	}
	
	static int materialIdForLayer(ChunkSectionLayer layer) {
		if (layer == null) {
			return GpuMaterialRecord.DEFAULT_TERRAIN_MATERIAL_ID;
		}
		return layer.ordinal();
	}
	
	static GpuMaterialRecord recordForLayer(ChunkSectionLayer layer, TextureInfo blockAtlas, TextureInfo lightmap) {
		return GpuMaterialRecord.terrainLayer(materialIdForLayer(layer), layer == null ? -1 : layer.ordinal(), alphaModeForLayer(layer), blockAtlas, lightmap);
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
}