package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public record TerrainTextureDescriptorLayout(long layoutBytes, long blockAtlasImageOffset, long blockAtlasSamplerOffset, long lightmapImageOffset, long lightmapSamplerOffset, long skyImageOffset, long cloudsImageOffset, long cloudsDepthImageOffset, long sampledImageDescriptorBytes, long samplerDescriptorBytes) {
}