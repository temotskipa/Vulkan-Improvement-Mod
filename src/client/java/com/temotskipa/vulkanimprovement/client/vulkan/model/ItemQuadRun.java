package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Consecutive quads of an item that Minecraft treats alike: the same render types, tint layer and
 * light emission, and next to each other in the polygon store.
 *
 * @param renderType      the render type without a glint
 * @param glintRenderType the render type with the standard glint
 * @param tintIndex       the tint layer, or -1 for none
 * @param lightEmission   the light the quads give off themselves
 * @param firstPolygon    the first quad in the store
 * @param polygons        how many quads there are
 */
record ItemQuadRun(RenderType renderType, RenderType glintRenderType, int tintIndex, int lightEmission, int firstPolygon, int polygons) {
}
