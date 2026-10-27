package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPassLayout;

/**
 * The pipeline, by name, and the subpass a mesh stage is compiled for. Minecraft compiles a
 * pipeline once for each subpass it draws in, possibly at the same time, so a name alone could hand
 * one compilation's mesh stage to another's.
 */
public record ModelMeshStageKey(String pipelineName, RenderPassLayout.Subpass subpass) {
    public static ModelMeshStageKey of(CompiledRenderPipeline.CreateInfo createInfo) {
        return new ModelMeshStageKey(createInfo.name(), createInfo.subpass());
    }
}