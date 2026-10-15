package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.jspecify.annotations.Nullable;

/**
 * A draw of Minecraft's staged vertex buffer whose vertices the GPU writes: its index in the
 * frame, how many vertices it has, and where they start in the frame's vertex buffer once the
 * frame is written.
 */
public final class ModelVertexDraw {
    final int index;
    int vertexCount;
    int baseVertex;
    
    ModelVertexDraw(int index) {
        this.index = index;
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
