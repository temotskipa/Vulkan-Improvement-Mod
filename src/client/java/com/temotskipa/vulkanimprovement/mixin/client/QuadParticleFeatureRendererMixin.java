package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ParticleGpuVertices;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Offers each layer of particles to the GPU path where Minecraft would write its vertices.
 * Minecraft still extracts the particles, groups them by layer and draws them with its pipelines.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public final class QuadParticleFeatureRendererMixin {
	@WrapOperation(method = "prepareGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;buildLayer(Lnet/minecraft/client/particle/SingleQuadParticle$Layer;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
	private void vim$buildOnGpu(QuadParticleRenderState particles, Layer layer, VertexConsumer builder, Operation<Void> original, @Local(name = "stagedVertexBuffer") StagedVertexBuffer stagedVertexBuffer, @Local(name = "draw") Draw draw) {
		if (!ParticleGpuVertices.get().take(stagedVertexBuffer, draw, particles, layer)) {
			original.call(particles, layer, builder);
		}
	}
}