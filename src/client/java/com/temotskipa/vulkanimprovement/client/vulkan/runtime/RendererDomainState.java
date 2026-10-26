package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

record RendererDomainState(String path, String reason, boolean cpuDrawLists, boolean gpuWorkQueues, boolean gpuMeshGeneration, boolean gpuVisibility) {
    static RendererDomainState vanilla(String reason) {
        return new RendererDomainState("vanilla", reason, true, false, false, false);
    }
    
    static RendererDomainState meshTerrainGpu(String reason) {
        return new RendererDomainState("mesh-shader-terrain", reason, true, true, false, true);
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", this.path);
        map.put("reason", this.reason);
        map.put("cpuDrawLists", this.cpuDrawLists);
        map.put("gpuWorkQueues", this.gpuWorkQueues);
        map.put("gpuMeshGeneration", this.gpuMeshGeneration);
        map.put("gpuVisibility", this.gpuVisibility);
        return map;
    }
}