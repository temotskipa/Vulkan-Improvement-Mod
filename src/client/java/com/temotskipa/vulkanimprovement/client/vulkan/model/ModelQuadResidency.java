package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.jspecify.annotations.Nullable;

/**
 * The item layout of the quad list a baked quad comes first in. Implemented on Minecraft's
 * {@code BakedQuad} by a mixin, so an item's quads are looked at once per store, not once per frame.
 */
public interface ModelQuadResidency {
    /**
     * The layout last made for a list that starts with this quad, or null for none.
     */
    @Nullable ItemQuadLayout vim$itemLayout();

    void vim$setItemLayout(ItemQuadLayout layout);
}
