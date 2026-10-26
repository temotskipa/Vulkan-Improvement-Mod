package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelVertexDraw;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelVertexDrawHolder;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Marks a draw of Minecraft's staged vertex buffer as a GPU model draw, and counts the vertices
 * the GPU writes into it, so that Minecraft sizes its index buffer for them and does not take the
 * draw for empty.
 */
@Mixin(StagedVertexBuffer.Draw.class)
public abstract class StagedVertexBufferDrawMixin implements ModelVertexDrawHolder {
    @Shadow
    private int vertexCount;
    @Shadow
    private int indexCount;
    @Unique
    private @Nullable ModelVertexDraw vim$modelDraw;
    
    @Override
    public @Nullable ModelVertexDraw vim$modelDraw() {
        return this.vim$modelDraw;
    }
    
    @Override
    public void vim$setModelDraw(ModelVertexDraw draw) {
        this.vim$modelDraw = draw;
    }
    
    @Override
    public void vim$addQuadVertices(int vertices) {
        this.vertexCount += vertices;
        this.indexCount += PrimitiveTopology.QUADS.indexCount(vertices);
    }
}