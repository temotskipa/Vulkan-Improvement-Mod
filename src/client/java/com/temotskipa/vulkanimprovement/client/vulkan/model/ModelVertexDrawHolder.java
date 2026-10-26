package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.jspecify.annotations.Nullable;

/**
 * Implemented on Minecraft's {@code StagedVertexBuffer.Draw} by a mixin: the GPU draw a draw
 * is, if any, and the vertex count Minecraft sizes its index buffer and draw by.
 */
public interface ModelVertexDrawHolder {
    @Nullable ModelVertexDraw vim$modelDraw();
    
    void vim$setModelDraw(ModelVertexDraw draw);
    
    /**
     * Counts vertices the GPU writes into the draw, and the indices Minecraft draws them with.
     */
    void vim$addQuadVertices(int vertices);
}