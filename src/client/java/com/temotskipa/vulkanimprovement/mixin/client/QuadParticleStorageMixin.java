package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.model.ParticleStorageAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Lets the GPU particle path read the flat arrays Minecraft keeps a layer in.
 */
@Mixin(targets = "net.minecraft.client.renderer.state.level.QuadParticleRenderState$Storage")
public class QuadParticleStorageMixin implements ParticleStorageAccess {
	@Shadow
	private float[] floatValues;
	@Shadow
	private int[] intValues;
	@Shadow
	private int currentParticleIndex;
	
	@Override
	public float[] vim$floatValues() {
		return this.floatValues;
	}
	
	@Override
	public int[] vim$intValues() {
		return this.intValues;
	}
	
	@Override
	public int vim$currentParticleIndex() {
		return this.currentParticleIndex;
	}
}