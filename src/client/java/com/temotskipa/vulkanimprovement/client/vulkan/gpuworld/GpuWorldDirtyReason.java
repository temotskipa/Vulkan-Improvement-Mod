package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

public enum GpuWorldDirtyReason {
    TERRAIN_LAYER_CAPTURED("terrain-layer-captured"),
    SECTION_RELEASED("section-released"),
    WORLD_CLEARED("world-cleared"),
    MATERIAL_TABLE_CHANGED("material-table-changed"),
    VISIBILITY_CHANGED("visibility-changed");

    private final String diagnosticName;

    GpuWorldDirtyReason(String diagnosticName) {
        this.diagnosticName = diagnosticName;
    }

    public String diagnosticName() {
        return this.diagnosticName;
    }
}
