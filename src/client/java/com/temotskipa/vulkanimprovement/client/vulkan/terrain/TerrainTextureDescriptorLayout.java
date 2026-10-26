package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

public record TerrainTextureDescriptorLayout(long layoutBytes, long blockAtlasImageOffset, long blockAtlasSamplerOffset, long lightmapImageOffset, long lightmapSamplerOffset, long skyImageOffset, long cloudsImageOffset, long cloudsDepthImageOffset, long sampledImageDescriptorBytes, long samplerDescriptorBytes) {
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("layoutBytes", this.layoutBytes);
        map.put("blockAtlasImageOffset", this.blockAtlasImageOffset);
        map.put("blockAtlasSamplerOffset", this.blockAtlasSamplerOffset);
        map.put("lightmapImageOffset", this.lightmapImageOffset);
        map.put("lightmapSamplerOffset", this.lightmapSamplerOffset);
        map.put("skyImageOffset", this.skyImageOffset);
        map.put("cloudsImageOffset", this.cloudsImageOffset);
        map.put("cloudsDepthImageOffset", this.cloudsDepthImageOffset);
        map.put("sampledImageDescriptorBytes", this.sampledImageDescriptorBytes);
        map.put("samplerDescriptorBytes", this.samplerDescriptorBytes);
        return map;
    }
}