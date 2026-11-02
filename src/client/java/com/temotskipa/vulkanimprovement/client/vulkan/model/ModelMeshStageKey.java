package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline.CreateInfo;
import com.mojang.renderpearl.api.pipeline.RenderPassLayout;
import com.mojang.renderpearl.api.pipeline.RenderPassLayout.Subpass;

/**
 * The pipeline, by name, and the subpass a mesh stage is compiled for. Minecraft compiles a
 * pipeline once for each subpass it draws in, possibly at the same time, so a name alone could hand
 * one compilation's mesh stage to another's.
 */
public record ModelMeshStageKey(String pipelineName, Subpass subpass) {
	public static ModelMeshStageKey of(CreateInfo createInfo) {
		return new ModelMeshStageKey(createInfo.name(), createInfo.subpass());
	}
}