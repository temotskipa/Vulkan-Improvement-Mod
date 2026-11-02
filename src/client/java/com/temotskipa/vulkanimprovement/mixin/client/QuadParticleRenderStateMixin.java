package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ParticleLayerAccess;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Function;

/**
 * Lets the GPU particle path reach a layer's storage, and spares Minecraft a map lookup per
 * particle: a run of particles nearly always shares a layer, and a layer's storage stays for good.
 */
@Mixin(QuadParticleRenderState.class)
public abstract class QuadParticleRenderStateMixin implements ParticleLayerAccess {
	@Shadow
	@Final
	private Map<Layer, ?> particles;
	@Unique
	private @Nullable Object vim$lastLayer;
	@Unique
	private @Nullable Object vim$lastStorage;
	
	@Override
	public Map<Layer, ?> vim$particles() {
		return this.particles;
	}
	
	@WrapOperation(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
	private Object vim$lastLayerStorage(Map<Object, Object> storages, Object layer, Function<Object, Object> create, Operation<Object> original) {
		if (layer != this.vim$lastLayer) {
			this.vim$lastLayer = layer;
			this.vim$lastStorage = original.call(storages, layer, create);
		}
		return this.vim$lastStorage;
	}
}