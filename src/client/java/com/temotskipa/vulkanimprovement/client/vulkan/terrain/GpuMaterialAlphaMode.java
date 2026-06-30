package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

enum GpuMaterialAlphaMode {
    OPAQUE(0),
    MASKED(1),
    BLENDED(2);

    private final int id;

    GpuMaterialAlphaMode(int id) {
        this.id = id;
    }

    int id() {
        return this.id;
    }
}
