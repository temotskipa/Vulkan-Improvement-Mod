package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SingleQuadParticle.Layer;

import java.util.Map;

/**
 * Minecraft's particles by layer. Implemented on its {@code QuadParticleRenderState} by a mixin.
 */
public interface ParticleLayerAccess {
	Map<Layer, ?> vim$particles();
}