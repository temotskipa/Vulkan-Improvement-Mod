package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

record TerrainTextureBinding(String name, long imageView, long sampler, int width, int height, int baseMipLevel, int mipLevels, int maxAnisotropy, String minFilter, String magFilter, String maxLod, boolean closed, boolean unavailable) {
	static TerrainTextureBinding unavailable(String name, GpuTextureView view, GpuSampler sampler) {
		return new TerrainTextureBinding(name, 0L, 0L, view == null ? 0 : view.getWidth(0), view == null ? 0 : view.getHeight(0), view == null ? 0 : view.baseMipLevel(), view == null ? 0 : view.mipLevels(), sampler == null ? 0 : sampler.getMaxAnisotropy(), sampler == null ? "unavailable" : String.valueOf(sampler.getMinFilter()), sampler == null ? "unavailable" : String.valueOf(sampler.getMagFilter()), sampler == null ? "unavailable" : String.valueOf(sampler.getMaxLod()), view == null || view.isClosed(), true);
	}
}