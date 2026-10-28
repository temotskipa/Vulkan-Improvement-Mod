package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.model.geom.ModelPart.Cube;

import java.util.Arrays;
import java.util.List;

/**
 * A model part's cubes as one cube store holds them: each cube's first face and face count, and
 * every direction a face of the part points, so that a posed part is recorded without looking at
 * its cubes. A part with a cube the GPU path leaves to Minecraft is not laid out; its cubes are
 * looked at one by one, as Minecraft's must be compiled between the GPU draws.
 */
public final class ModelPartCubes {
    final int store;
    final boolean onGpu;
    final int[] firstPolygons;
    final int[] polygons;
    final int directionMask;
    final int vertices;
    
    private ModelPartCubes(int store, boolean onGpu, int[] firstPolygons, int[] polygons, int directionMask) {
        this.store = store;
        this.onGpu = onGpu;
        this.firstPolygons = firstPolygons;
        this.polygons = polygons;
        this.directionMask = directionMask;
        int polygonCount = 0;
        for (int cubePolygons : polygons) {
            polygonCount += cubePolygons;
        }
        this.vertices = polygonCount * ModelGpuRecords.POLYGON_VERTICES;
    }
    
    /**
     * A part whose cubes are all in the store: each one's first face and face count, none of them
     * without faces, and the directions their faces point, one bit per {@code Direction} index.
     */
    static ModelPartCubes laidOut(int store, int[] firstPolygons, int[] polygons, int directionMask) {
        return new ModelPartCubes(store, true, firstPolygons, polygons, directionMask);
    }
    
    /**
     * Lays out the part's cubes in the store, uploading the faces of those not in it yet.
     */
    static ModelPartCubes of(List<Cube> cubes, ModelCubeStore store) {
        int[] firstPolygons = new int[cubes.size()];
        int[] polygons = new int[cubes.size()];
        int count = 0;
        int directionMask = 0;
        for (Cube cube : cubes) {
            int firstPolygon = store.firstPolygon(cube);
            if (firstPolygon == ModelCubeStore.CPU_ONLY) {
                return new ModelPartCubes(store.id(), false, new int[0], new int[0], 0);
            }
            int cubePolygons = cube.polygons.length;
            if (cubePolygons == 0) {
                continue;
            }
            firstPolygons[count] = firstPolygon;
            polygons[count] = cubePolygons;
            count++;
            directionMask |= ((ModelCubeResidency) cube).vim$directionMask();
        }
        return laidOut(store.id(), Arrays.copyOf(firstPolygons, count), Arrays.copyOf(polygons, count), directionMask);
    }
    
    boolean residentIn(ModelCubeStore cubeStore) {
        return this.store == cubeStore.id();
    }
    
    int count() {
        return this.polygons.length;
    }
}