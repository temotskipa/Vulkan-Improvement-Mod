package com.temotskipa.vulkanimprovement.client.vulkan;

enum RendererDomain {
    TERRAIN("terrain"),
    ENTITIES("entities"),
    BLOCK_ENTITIES("blockEntities"),
    ITEMS("items"),
    PARTICLES("particles"),
    SKY("sky"),
    CLOUDS("clouds"),
    WEATHER("weather"),
    TRANSLUCENT_EFFECTS("translucentEffects"),
    DIAGNOSTICS("diagnostics");

    private final String diagnosticName;

    RendererDomain(String diagnosticName) {
        this.diagnosticName = diagnosticName;
    }

    String diagnosticName() {
        return this.diagnosticName;
    }
}
