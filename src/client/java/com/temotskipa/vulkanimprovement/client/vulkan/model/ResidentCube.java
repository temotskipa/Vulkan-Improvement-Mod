package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.model.geom.ModelPart.Cube;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * A cube whose faces are in a {@link ModelCubeStore}, held weakly, and how many faces it has.
 */
final class ResidentCube extends WeakReference<Cube> {
    private final int polygons;
    
    ResidentCube(Cube cube, int polygons, ReferenceQueue<Cube> queue) {
        super(cube, queue);
        this.polygons = polygons;
    }
    
    int polygons() {
        return this.polygons;
    }
}