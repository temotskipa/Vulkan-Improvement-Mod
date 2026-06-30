package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

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
    POST_PROCESSING("postProcessing"),
    DIAGNOSTICS("diagnostics");

    private final String diagnosticName;

    RendererDomain(String diagnosticName) {
        this.diagnosticName = diagnosticName;
    }

    String diagnosticName() {
        return this.diagnosticName;
    }
}
