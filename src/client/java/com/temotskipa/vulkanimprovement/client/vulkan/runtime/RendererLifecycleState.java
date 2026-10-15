package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

public enum RendererLifecycleState {
    UNCONFIGURED(false), CONFIGURING(false), READY(true), FAILED(false), SHUTTING_DOWN(false);
    
    private final boolean drawable;
    
    RendererLifecycleState(boolean drawable) {
        this.drawable = drawable;
    }
    
    public boolean drawable() {
        return this.drawable;
    }
}