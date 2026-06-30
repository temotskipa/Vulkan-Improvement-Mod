package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record TerrainMeshTaskDispatch(int meshletOffset, int layerOrdinal, int requestedMeshlets, int taskCount,
                                      long visibleMeshletListAddress, long workQueueAddress, long indirectCommandBuffer,
                                      long indirectCommandOffset, int indirectCommandStride,
                                      long indirectCommandAddress, boolean indirectCommandGpuGenerated, Source source,
                                      String reason) {
    static final int MAX_DIRECT_TASKS = 65_535;
    
    public TerrainMeshTaskDispatch {
        if (meshletOffset < 0) {
            throw new IllegalArgumentException("meshletOffset must be non-negative");
        }
        if (requestedMeshlets < 0) {
            throw new IllegalArgumentException("requestedMeshlets must be non-negative");
        }
        if (taskCount < 0 || taskCount > MAX_DIRECT_TASKS) {
            throw new IllegalArgumentException("taskCount must be between 0 and " + MAX_DIRECT_TASKS);
        }
        if (taskCount > requestedMeshlets) {
            throw new IllegalArgumentException("taskCount must not exceed requestedMeshlets");
        }
        if (visibleMeshletListAddress < 0L) {
            throw new IllegalArgumentException("visibleMeshletListAddress must be non-negative");
        }
        if (workQueueAddress < 0L) {
            throw new IllegalArgumentException("workQueueAddress must be non-negative");
        }
        if (indirectCommandBuffer < 0L) {
            throw new IllegalArgumentException("indirectCommandBuffer must be non-negative");
        }
        if (indirectCommandOffset < 0L) {
            throw new IllegalArgumentException("indirectCommandOffset must be non-negative");
        }
        if (indirectCommandStride < 0) {
            throw new IllegalArgumentException("indirectCommandStride must be non-negative");
        }
        if (indirectCommandAddress < 0L) {
            throw new IllegalArgumentException("indirectCommandAddress must be non-negative");
        }
        if (indirectCommandBuffer == 0L && (indirectCommandOffset != 0L || indirectCommandStride != 0 || indirectCommandAddress != 0L)) {
            throw new IllegalArgumentException("indirect command metadata requires an indirect command buffer");
        }
        if (indirectCommandBuffer != 0L && indirectCommandStride < TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE) {
            throw new IllegalArgumentException("indirectCommandStride must cover a mesh-task command record");
        }
        Objects.requireNonNull(source, "source");
        reason = reason == null ? "" : reason;
        if (source.requiresVisibleMeshletList() && taskCount > 0 && visibleMeshletListAddress == 0L) {
            throw new IllegalArgumentException(source.id() + " requires a visible meshlet list address");
        }
        if (source.requiresWorkQueue() && taskCount > 0 && workQueueAddress == 0L) {
            throw new IllegalArgumentException(source.id() + " requires a work queue address");
        }
    }
    
    static TerrainMeshTaskDispatch unavailable(String reason) {
        return new TerrainMeshTaskDispatch(0, -1, 0, 0, 0L, 0L, 0L, 0L, 0, 0L, false, Source.UNAVAILABLE, reason);
    }
    
    static TerrainMeshTaskDispatch fullLayer(int meshletOffset, int layerOrdinal, int meshletCount) {
        return new TerrainMeshTaskDispatch(meshletOffset, layerOrdinal, Math.max(meshletCount, 0), Math.clamp(meshletCount, 0, MAX_DIRECT_TASKS), 0L, 0L, 0L, 0L, 0, 0L, false, Source.DIRECT_LAYER, "");
    }
    
    static TerrainMeshTaskDispatch cpuVisibleList(DescriptorHeapTerrainResources.VisibleMeshletUpload upload, int layerOrdinal) {
        if (upload == null || !upload.ready()) {
            return unavailable("visible meshlet upload unavailable");
        }
        return new TerrainMeshTaskDispatch(upload.offset(), layerOrdinal, Math.max(upload.count(), 0), Math.clamp(upload.count(), 0, MAX_DIRECT_TASKS), upload.address(), 0L, 0L, 0L, 0, 0L, false, Source.CPU_VISIBLE_LIST, "");
    }
    
    static TerrainMeshTaskDispatch cpuWorkQueue(DescriptorHeapTerrainResources.TerrainWorkQueueUpload upload, int layerOrdinal, int requestedMeshlets) {
        if (upload == null || !upload.ready()) {
            return unavailable("terrain work queue upload unavailable");
        }
        int requested = Math.max(requestedMeshlets, upload.count());
        return new TerrainMeshTaskDispatch(upload.offset(), layerOrdinal, requested, Math.min(upload.count(), MAX_DIRECT_TASKS), 0L, upload.address(), 0L, 0L, 0, 0L, false, Source.CPU_WORK_QUEUE, "");
    }
    
    TerrainMeshTaskDispatch withIndirectCommand(DescriptorHeapTerrainResources.TerrainMeshTaskCommandUpload upload) {
        if (upload == null || !upload.ready()) {
            return this;
        }
        return new TerrainMeshTaskDispatch(this.meshletOffset, this.layerOrdinal, this.requestedMeshlets, this.taskCount, this.visibleMeshletListAddress, this.workQueueAddress, upload.vkBuffer(), upload.offsetBytes(), upload.strideBytes(), upload.address(), upload.gpuGenerated(), this.source, this.reason);
    }
    
    boolean ready() {
        return this.source != Source.UNAVAILABLE && this.taskCount > 0;
    }
    
    boolean usesVisibleMeshletList() {
        return this.visibleMeshletListAddress != 0L;
    }
    
    boolean usesWorkQueue() {
        return this.workQueueAddress != 0L;
    }
    
    boolean usesIndirectCommand() {
        return this.indirectCommandBuffer != 0L;
    }
    
    boolean usesGpuGeneratedIndirectCommand() {
        return usesIndirectCommand() && this.indirectCommandGpuGenerated;
    }
    
    boolean truncated() {
        return this.requestedMeshlets > this.taskCount;
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source", this.source.id());
        map.put("meshletOffset", this.meshletOffset);
        map.put("layerOrdinal", this.layerOrdinal);
        map.put("requestedMeshlets", this.requestedMeshlets);
        map.put("taskCount", this.taskCount);
        map.put("visibleMeshletListAddress", this.visibleMeshletListAddress);
        map.put("workQueueAddress", this.workQueueAddress);
        map.put("indirectCommandBuffer", this.indirectCommandBuffer);
        map.put("indirectCommandOffset", this.indirectCommandOffset);
        map.put("indirectCommandStride", this.indirectCommandStride);
        map.put("indirectCommandAddress", this.indirectCommandAddress);
        map.put("indirectCommandGpuGenerated", this.indirectCommandGpuGenerated);
        map.put("usesVisibleMeshletList", usesVisibleMeshletList());
        map.put("usesWorkQueue", usesWorkQueue());
        map.put("usesIndirectCommand", usesIndirectCommand());
        map.put("usesGpuGeneratedIndirectCommand", usesGpuGeneratedIndirectCommand());
        map.put("truncated", truncated());
        map.put("directMeshTaskLimit", MAX_DIRECT_TASKS);
        map.put("reason", this.reason);
        return map;
    }
    
    public enum Source {
        UNAVAILABLE("unavailable", false, false), DIRECT_LAYER("direct-layer", false, false), CPU_VISIBLE_LIST("cpu-visible-list", true, false), CPU_WORK_QUEUE("cpu-work-queue", false, true);
        private final String id;
        private final boolean requiresVisibleMeshletList;
        private final boolean requiresWorkQueue;
        
        Source(String id, boolean requiresVisibleMeshletList, boolean requiresWorkQueue) {
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
}