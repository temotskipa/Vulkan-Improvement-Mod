package com.temotskipa.vulkanimprovement.client.vulkan.model;

/**
 * Where a model cube's faces are in the resident polygon store. Implemented on Minecraft's
 * {@code ModelPart.Cube} by a mixin, so a cube is looked at once per store, not once per frame.
 */
public interface ModelCubeResidency {
    /**
     * The store the rest was recorded for, or 0 for none.
     */
    int vim$residentStore();
    
    /**
     * The cube's first polygon in the store, or {@link ModelCubeStore#CPU_ONLY}.
     */
    int vim$firstPolygon();
    
    /**
     * The directions the cube's faces point, one bit per {@code Direction} index.
     */
    int vim$directionMask();
    
    void vim$makeResident(int store, int firstPolygon, int directionMask);
}
