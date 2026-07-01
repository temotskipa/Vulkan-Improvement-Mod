package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public record TextureInfo(boolean available, int width, int height, int baseMipLevel, int mipLevels) {
    public static TextureInfo unavailable() {
        return new TextureInfo(false, 0, 0, 0, 0);
    }
}
