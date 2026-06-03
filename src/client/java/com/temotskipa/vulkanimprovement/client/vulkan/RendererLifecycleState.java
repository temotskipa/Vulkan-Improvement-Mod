package com.temotskipa.vulkanimprovement.client.vulkan;

enum RendererLifecycleState {
    UNCONFIGURED(false), CONFIGURING(false), READY(true), FAILED(false), SHUTTING_DOWN(false), DEVICE_LOST(false);

    private final boolean drawable;

    RendererLifecycleState(boolean drawable) {
        this.drawable = drawable;
    }

    boolean drawable() {
        return this.drawable;
    }
}
