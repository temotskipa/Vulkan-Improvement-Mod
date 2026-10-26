package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record TerrainDescriptorWrite(TerrainGpuResources resources, TerrainTextureDescriptorLayout layout, long atlasView, long atlasSampler, long lightmapView, long lightmapSampler, boolean improvedFog, long skyView, long cloudsView, long cloudsDepthView) {
}