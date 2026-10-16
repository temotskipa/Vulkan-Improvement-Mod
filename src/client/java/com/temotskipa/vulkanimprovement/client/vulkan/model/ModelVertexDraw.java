package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.jspecify.annotations.Nullable;

/**
 * A draw of Minecraft's staged vertex buffer whose vertices the GPU makes: its index in the frame,
 * the render type Minecraft draws it with, how many vertices and cubes it has, and once the frame
 * is written, whether the mesh stage draws it, where its cubes start in the frame's sorted cubes
 * and, for the vertex pass, where its vertices start in the frame's vertex buffer.
 */
public final class ModelVertexDraw {
    final int index;
    final PreparedRenderType renderType;
    int vertexCount;
    int jobCount;
    boolean mesh;
    int firstJob;
    int baseVertex;
    
    ModelVertexDraw(int index, PreparedRenderType renderType) {
        this.index = index;
        this.renderType = renderType;
    }
    
    /**
     * Implemented on Minecraft's {@code StagedVertexBuffer.Draw} by a mixin: the GPU draw a draw
     * is, if any, and the vertex count Minecraft sizes its index buffer and draw by.
     */
    public interface Holder {
        @Nullable ModelVertexDraw vim$modelDraw();
        
        void vim$setModelDraw(ModelVertexDraw draw);
        
        /**
         * Counts vertices the GPU writes into the draw, and the indices Minecraft draws them with.
         */
        void vim$addQuadVertices(int vertices);
    }
}