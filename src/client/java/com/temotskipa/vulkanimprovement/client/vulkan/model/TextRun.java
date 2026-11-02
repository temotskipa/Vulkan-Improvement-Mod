package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Consecutive quads of a text drawn with one render type, next to each other in the glyph store.
 *
 * @param renderType what Minecraft draws them with
 * @param firstQuad  the first quad in the store
 * @param quads      how many quads there are
 */
record TextRun(RenderType renderType, int firstQuad, int quads) {
}
