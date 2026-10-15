package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelCubeResidency;
import net.minecraft.client.model.geom.ModelPart.Cube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Lets a model cube remember where the GPU model path keeps its faces.
 */
@Mixin(Cube.class)
public abstract class ModelPartCubeMixin implements ModelCubeResidency {
    @Unique
    private int vim$residentStore;
    @Unique
    private int vim$firstPolygon;
    @Unique
    private int vim$directionMask;
    
    @Override
    public int vim$residentStore() {
        return this.vim$residentStore;
    }
    
    @Override
    public int vim$firstPolygon() {
        return this.vim$firstPolygon;
    }
    
    @Override
    public int vim$directionMask() {
        return this.vim$directionMask;
    }
    
    @Override
    public void vim$makeResident(int store, int firstPolygon, int directionMask) {
        this.vim$residentStore = store;
        this.vim$firstPolygon = firstPolygon;
        this.vim$directionMask = directionMask;
    }
}