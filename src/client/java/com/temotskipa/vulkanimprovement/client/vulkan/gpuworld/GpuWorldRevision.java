package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

public record GpuWorldRevision(long value) {
    public GpuWorldRevision {
        if (value < 0L) {
            throw new IllegalArgumentException("GPU world revisions must be non-negative: " + value);
        }
    }
    
    public static GpuWorldRevision initial() {
        return new GpuWorldRevision(0L);
    }
    
    public static GpuWorldRevision of(long value) {
        return new GpuWorldRevision(value);
    }
    
    public GpuWorldRevision next() {
        return new GpuWorldRevision(this.value == Long.MAX_VALUE ? 1L : this.value + 1L);
    }
    
    public boolean uninitialized() {
        return this.value == 0L;
    }
}