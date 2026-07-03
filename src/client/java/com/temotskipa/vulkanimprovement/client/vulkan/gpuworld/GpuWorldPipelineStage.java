package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;

public enum GpuWorldPipelineStage {
    AUTHORITATIVE_INGEST("authoritative-ingest", true, false,
            "CPU submits compact dirty-event updates from authoritative Minecraft chunk state."),
    GPU_PAGE_TRANSCODE("gpu-page-transcode", false, true,
            "GPU converts dirty chunk-section input into persistent render pages."),
    PERSISTENT_RESIDENCY("persistent-residency", false, true,
            "GPU-world pages remain resident across frames until evicted by budget or source invalidation."),
    VISIBILITY_COMPACTION("visibility-compaction", false, true,
            "GPU culling compacts visible section and meshlet work without per-draw CPU list rebuilds."),
    INDIRECT_COMMAND_GENERATION("indirect-command-generation", false, true,
            "GPU writes indirect mesh-task or draw command records from compacted visibility output."),
    STRICT_REPLACEMENT_VALIDATION("strict-replacement-validation", true, false,
            "CPU-controlled validation mode may cancel vanilla only after coverage checks prove parity.");

    private final String id;
    private final boolean cpuOwned;
    private final boolean gpuResidentOutput;
    private final String description;

    GpuWorldPipelineStage(String id, boolean cpuOwned, boolean gpuResidentOutput, String description) {
        this.id = id;
        this.cpuOwned = cpuOwned;
        this.gpuResidentOutput = gpuResidentOutput;
        this.description = description;
    }

    public String id() {
        return this.id;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", this.id);
        map.put("cpuOwned", this.cpuOwned);
        map.put("gpuResidentOutput", this.gpuResidentOutput);
        map.put("perDrawCpuUploadAllowed", false);
        map.put("description", this.description);
        return map;
    }

    public static Map<String, Object> asDiagnostics() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (GpuWorldPipelineStage stage : values()) {
            map.put(stage.id(), stage.asMap());
        }
        return map;
    }
}
