package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.resources.model.geometry.BakedQuad;

import java.util.List;

/**
 * A baked item model's quad list as one cube store holds it, so that a submit of the list is
 * recorded without looking at its quads. Minecraft keeps the list for as long as the baked model,
 * and submits that same list every frame.
 *
 * @param store         the store the quads are in
 * @param quads         the list this is the layout of
 * @param runs          the list's quads, in order
 * @param directionMask every direction a quad points, one bit per {@code Direction} index
 */
public record ItemQuadLayout(int store, List<BakedQuad> quads, ItemQuadRun[] runs, int directionMask) {
}
