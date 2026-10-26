package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.jspecify.annotations.Nullable;

/**
 * Implemented on Minecraft's Vulkan pipeline by a mixin.
 */
public interface ModelMeshPipelineHolder {
    @Nullable ModelMeshPipeline vim$meshPipeline();
    
    void vim$setMeshPipeline(ModelMeshPipeline meshPipeline);
    
    /**
     * The shader module of the pipeline's stage at this index of the stages it was built with.
     */
    long vim$shaderModule(int stage);
}