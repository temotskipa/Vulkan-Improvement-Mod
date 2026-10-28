package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.jspecify.annotations.Nullable;

/**
 * How a model part's cubes are laid out in the resident polygon store. Implemented on Minecraft's
 * {@code ModelPart} by a mixin, so a part's cubes are looked at once per store, not once per frame.
 */
public interface ModelPartResidency {
    /**
     * The layout last made for the part, in whichever store was current then, or null for none.
     */
    @Nullable ModelPartCubes vim$residentCubes();

    void vim$setResidentCubes(ModelPartCubes residentCubes);
}
