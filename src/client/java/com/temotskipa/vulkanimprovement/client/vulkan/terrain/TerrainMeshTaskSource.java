package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public enum TerrainMeshTaskSource {
    UNAVAILABLE("unavailable", false, false), DIRECT_LAYER("direct-layer", false, false), CPU_VISIBLE_LIST("cpu-visible-list", true, false), CPU_WORK_QUEUE("cpu-work-queue", false, true), GPU_VISIBLE_PAGES("gpu-visible-pages", true, false), LISTED_PAGES("listed-pages", true, false), GPU_DRIVEN_PAGES("gpu-driven-pages", true, false);
    
    private final String id;
    private final boolean requiresVisibleMeshletList;
    private final boolean requiresWorkQueue;
    
    TerrainMeshTaskSource(String id, boolean requiresVisibleMeshletList, boolean requiresWorkQueue) {
        this.id = id;
        this.requiresVisibleMeshletList = requiresVisibleMeshletList;
        this.requiresWorkQueue = requiresWorkQueue;
    }
    
    String id() {
        return this.id;
    }
    
    boolean requiresVisibleMeshletList() {
        return this.requiresVisibleMeshletList;
    }
    
    boolean requiresWorkQueue() {
        return this.requiresWorkQueue;
    }
}