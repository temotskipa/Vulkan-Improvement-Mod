package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Implemented on the draw group of Minecraft's {@code RenderTypeFeatureRenderer} by a mixin. GPU
 * draws are draws of the group like Minecraft's own, kept in its lists in the order their geometry
 * came, so the group executes them in Minecraft's order with Minecraft's render types.
 */
public interface ModelDrawGroup {
    /**
     * The draw the next GPU-written cubes of the render type go into, chosen and consolidated as
     * Minecraft chooses its own draws.
     */
    StagedVertexBuffer.Draw vim$modelDraw(RenderType renderType);
    
    /**
     * Minecraft's own vertex builder for the render type, for geometry the GPU path does not write.
     */
    VertexConsumer vim$cpuVertexBuilder(RenderType renderType);
}
