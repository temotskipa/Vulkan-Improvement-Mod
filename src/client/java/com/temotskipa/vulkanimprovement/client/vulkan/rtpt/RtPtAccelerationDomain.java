package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

public enum RtPtAccelerationDomain {
    TERRAIN("terrain"),
    BLOCK_ENTITIES("blockEntities"),
    ENTITIES("entities"),
    RENDER_ONLY_LOD("renderOnlyLod");

    private final String diagnosticName;

    RtPtAccelerationDomain(String diagnosticName) {
        this.diagnosticName = diagnosticName;
    }

    public String diagnosticName() {
        return this.diagnosticName;
    }
}
