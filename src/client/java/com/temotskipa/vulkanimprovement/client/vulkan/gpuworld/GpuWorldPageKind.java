package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.util.LinkedHashMap;
import java.util.Map;

public enum GpuWorldPageKind {
    CANONICAL_MIRROR("canonical-mirror", true, false),
    RENDER_ONLY_LOD("render-only-lod", false, false),
    PREDICTIVE_DISTANT("predictive-distant", false, false);

    private final String diagnosticName;
    private final boolean mirrorsCpuChunkState;
    private final boolean gameplayAuthoritative;

    GpuWorldPageKind(String diagnosticName, boolean mirrorsCpuChunkState, boolean gameplayAuthoritative) {
        this.diagnosticName = diagnosticName;
        this.mirrorsCpuChunkState = mirrorsCpuChunkState;
        this.gameplayAuthoritative = gameplayAuthoritative;
    }

    public String diagnosticName() {
        return this.diagnosticName;
    }

    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", this.diagnosticName);
        map.put("mirrorsCpuChunkState", this.mirrorsCpuChunkState);
        map.put("gameplayAuthoritative", this.gameplayAuthoritative);
        return map;
    }
}
