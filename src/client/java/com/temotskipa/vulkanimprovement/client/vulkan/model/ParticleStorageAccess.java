package com.temotskipa.vulkanimprovement.client.vulkan.model;

/**
 * The flat arrays Minecraft keeps a particle layer in. Implemented on its
 * {@code QuadParticleRenderState.Storage} by a mixin.
 */
public interface ParticleStorageAccess {
	float[] vim$floatValues();
	
	int[] vim$intValues();
	
	int vim$currentParticleIndex();
}