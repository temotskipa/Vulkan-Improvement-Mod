package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public enum TerrainDescriptorSlot {
    BLOCK_ATLAS("blockAtlas", "sampledImage"), LIGHTMAP("lightmap", "sampledImage"), GLOBAL_UNIFORMS("globalUniforms", "uniformBuffer"), SECTION_METADATA("sectionMetadata", "storageBuffer"), MESHLET_HEADERS("meshletHeaders", "storageBuffer"), MESHLET_VERTICES("meshletVertices", "storageBuffer"), MESHLET_INDICES("meshletIndices", "storageBuffer"), VISIBLE_MESHLETS("visibleMeshlets", "storageBuffer"), TERRAIN_WORK_QUEUE("terrainWorkQueue", "storageBuffer"), MESH_TASK_COMMANDS("meshTaskCommands", "storageBuffer"), MATERIAL_TABLE("materialTable", "storageBuffer"), DEBUG_COUNTERS("debugCounters", "storageBuffer");
    
    final String id;
    final String descriptorType;
    
    TerrainDescriptorSlot(String id, String descriptorType) {
        this.id = id;
        this.descriptorType = descriptorType;
    }
}