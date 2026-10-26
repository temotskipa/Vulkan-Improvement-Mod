package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.util.LinkedHashMap;
import java.util.Map;

record TerrainTextureBinding(String name, long imageView, long sampler, int width, int height, int baseMipLevel, int mipLevels, int maxAnisotropy, String minFilter, String magFilter, String maxLod, boolean closed, boolean unavailable) {
    static TerrainTextureBinding unavailable(String name, GpuTextureView view, GpuSampler sampler) {
        return new TerrainTextureBinding(name, 0L, 0L, view == null ? 0 : view.getWidth(0), view == null ? 0 : view.getHeight(0), view == null ? 0 : view.baseMipLevel(), view == null ? 0 : view.mipLevels(), sampler == null ? 0 : sampler.getMaxAnisotropy(), sampler == null ? "unavailable" : String.valueOf(sampler.getMinFilter()), sampler == null ? "unavailable" : String.valueOf(sampler.getMagFilter()), sampler == null ? "unavailable" : String.valueOf(sampler.getMaxLod()), view == null || view.isClosed(), true);
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", this.name);
        map.put("imageView", this.imageView);
        map.put("sampler", this.sampler);
        map.put("width", this.width);
        map.put("height", this.height);
        map.put("baseMipLevel", this.baseMipLevel);
        map.put("mipLevels", this.mipLevels);
        map.put("maxAnisotropy", this.maxAnisotropy);
        map.put("minFilter", this.minFilter);
        map.put("magFilter", this.magFilter);
        map.put("maxLod", this.maxLod);
        map.put("closed", this.closed);
        map.put("unavailable", this.unavailable);
        return map;
    }
}