package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.renderer.rendertype.PreparedRenderType;

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
}