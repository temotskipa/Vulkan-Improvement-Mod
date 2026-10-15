package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The fixed-function state Minecraft compiled a pipeline with. A Vulkan pipeline no longer carries
 * it, so it is remembered when the pipeline is compiled; the mesh terrain pipeline that stands in
 * for a vanilla terrain pipeline is built from it and for the same render pass.
 */
public record TerrainPipelineState(String name, PolygonMode polygonMode, boolean cull, @Nullable DepthStencilState depthStencilState, GpuFormat depthStencilFormat, List<@Nullable ColorTargetState> colorTargetStates) {
    public TerrainPipelineState {
        colorTargetStates = Collections.unmodifiableList(new ArrayList<>(colorTargetStates));
    }
    
    public static TerrainPipelineState from(CompiledRenderPipeline.CreateInfo info) {
        return new TerrainPipelineState(info.name(), info.polygonMode(), info.cull(), info.depthStencilState(), info.depthStencilFormat(), info.colorTargetStates());
    }
    
    /**
     * The state a pipeline was compiled with, if it is a Vulkan pipeline, on which a mixin keeps it.
     */
    public static @Nullable TerrainPipelineState of(@Nullable BackendRenderPipeline pipeline) {
        return pipeline instanceof Holder holder ? holder.vim$terrainPipelineState() : null;
    }
    
    /**
     * Keeps the state a pipeline was compiled with on the pipeline.
     */
    public static void remember(BackendRenderPipeline pipeline, TerrainPipelineState state) {
        if (pipeline instanceof Holder holder) {
            holder.vim$rememberTerrainPipelineState(state);
        }
    }
    
    public boolean hasDepth() {
        return this.depthStencilState != null;
    }
    
    /**
     * Implemented on Minecraft's Vulkan pipeline by a mixin.
     */
    public interface Holder {
        @Nullable TerrainPipelineState vim$terrainPipelineState();
        
        void vim$rememberTerrainPipelineState(TerrainPipelineState state);
    }
}