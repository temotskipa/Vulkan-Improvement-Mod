package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The fixed-function state Minecraft compiled a pipeline with. A Vulkan pipeline no longer carries
 * it, so it is remembered when the pipeline is compiled; the mesh terrain pipeline that stands in
 * for a vanilla terrain pipeline is built from it and for the same subpass.
 */
public record TerrainPipelineState(String name, PolygonMode polygonMode, boolean cull, @Nullable DepthStencilState depthStencilState, RenderPassLayout.Subpass subpass, List<@Nullable ColorTargetState> colorTargetStates) {
    public TerrainPipelineState {
        colorTargetStates = Collections.unmodifiableList(new ArrayList<>(colorTargetStates));
    }
    
    public static TerrainPipelineState from(CompiledRenderPipeline.CreateInfo info) {
        return new TerrainPipelineState(info.name(), info.polygonMode(), info.cull(), info.depthStencilState(), info.subpass(), info.colorTargetStates());
    }
    
    /**
     * The state a pipeline was compiled with, if it is a Vulkan pipeline, on which a mixin keeps it.
     */
    public static @Nullable TerrainPipelineState of(@Nullable BackendRenderPipeline pipeline) {
        return pipeline instanceof TerrainPipelineStateHolder holder ? holder.vim$terrainPipelineState() : null;
    }
    
    /**
     * Keeps the state a pipeline was compiled with on the pipeline.
     */
    public static void remember(BackendRenderPipeline pipeline, TerrainPipelineState state) {
        if (pipeline instanceof TerrainPipelineStateHolder holder) {
            holder.vim$rememberTerrainPipelineState(state);
        }
    }
}