package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public final class GpuMaterialRecordInvariantCheck {
    private GpuMaterialRecordInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) {
        checkRecordSizeMatchesTerrainLayout();
        checkLayoutDiagnostics();
        checkUnavailableTerrainMaterialEncoding();
        checkReadyTerrainMaterialEncoding();
    }

    @SuppressWarnings("ConstantValue")
    private static void checkRecordSizeMatchesTerrainLayout() {
        require(GpuMaterialRecord.BYTES == TerrainGpuLayout.MATERIAL_RECORD_STRIDE, "material record byte size must match terrain GPU layout");
        require(GpuMaterialRecord.INT_COUNT == 16, "material record must remain a 16-int shader record");
    }

    private static void checkLayoutDiagnostics() {
        Map<String, Object> layout = GpuMaterialRecord.layoutMap();
        require(layout.get("intCount").equals(GpuMaterialRecord.INT_COUNT), "material layout diagnostics must include int count");
        require(layout.get("bytes").equals(GpuMaterialRecord.BYTES), "material layout diagnostics must include byte size");
        require(layout.get("flagBlockAtlasReady").equals(GpuMaterialRecord.FLAG_BLOCK_ATLAS_READY), "material layout diagnostics must include atlas flag");
        require(layout.get("flagLightmapReady").equals(GpuMaterialRecord.FLAG_LIGHTMAP_READY), "material layout diagnostics must include lightmap flag");
        require(layout.get("flagAlphaMasked").equals(GpuMaterialRecord.FLAG_ALPHA_MASKED), "material layout diagnostics must include masked alpha flag");
        require(layout.get("flagAlphaBlended").equals(GpuMaterialRecord.FLAG_ALPHA_BLENDED), "material layout diagnostics must include blended alpha flag");
        require(layout.get("materialDomainTerrain").equals(GpuMaterialRecord.MATERIAL_DOMAIN_TERRAIN), "material layout diagnostics must include terrain domain");
    }

    private static void checkUnavailableTerrainMaterialEncoding() {
        GpuMaterialRecord record = GpuMaterialRecord.vanillaTerrain(
                GpuMaterialRecord.TextureInfo.unavailable(),
                GpuMaterialRecord.TextureInfo.unavailable()
        );
        int[] encoded = encode(record);

        require(encoded[0] == GpuMaterialRecord.DEFAULT_TERRAIN_MATERIAL_ID, "default material id must be encoded first");
        require(encoded[1] == 0, "unavailable default material must not claim texture readiness flags");
        require(encoded[8] == GpuMaterialRecord.MISSING_TEXTURE_INDEX, "normal texture index must default to missing");
        require(encoded[9] == GpuMaterialRecord.MISSING_TEXTURE_INDEX, "specular texture index must default to missing");
        require(encoded[10] == GpuMaterialRecord.MISSING_TEXTURE_INDEX, "emission texture index must default to missing");
        require(encoded[11] == GpuMaterialRecord.TINT_FLAGS_NONE, "default tint flags must be clear");
        require(encoded[12] == GpuMaterialAlphaMode.OPAQUE.id(), "default alpha mode must be opaque");
        require(encoded[14] == GpuMaterialRecord.MATERIAL_DOMAIN_TERRAIN, "default material domain must be terrain");
    }

    private static void checkReadyTerrainMaterialEncoding() {
        GpuMaterialRecord record = GpuMaterialRecord.vanillaTerrain(
                new GpuMaterialRecord.TextureInfo(true, 2048, 1024, 1, 5),
                new GpuMaterialRecord.TextureInfo(true, 16, 16, 0, 1)
        );
        int[] encoded = encode(record);

        require(encoded[1] == (GpuMaterialRecord.FLAG_BLOCK_ATLAS_READY | GpuMaterialRecord.FLAG_LIGHTMAP_READY), "ready terrain material must encode atlas and lightmap flags");
        require(encoded[2] == 2048, "block atlas width must be encoded");
        require(encoded[3] == 1024, "block atlas height must be encoded");
        require(encoded[4] == 1, "block atlas base mip must be encoded");
        require(encoded[5] == 5, "block atlas mip count must be encoded");
        require(encoded[6] == 16, "lightmap width must be encoded");
        require(encoded[7] == 16, "lightmap height must be encoded");
    }

    private static int[] encode(GpuMaterialRecord record) {
        ByteBuffer buffer = ByteBuffer.allocate(GpuMaterialRecord.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        record.write(buffer);
        require(buffer.position() == GpuMaterialRecord.BYTES, "material record writer must write exactly one record");
        buffer.flip();
        int[] encoded = new int[GpuMaterialRecord.INT_COUNT];
        for (int i = 0; i < encoded.length; i++) {
            encoded[i] = buffer.getInt();
        }
        return encoded;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
