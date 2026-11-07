package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.EntityOcclusion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Shadow
	@Final
	protected EntityRenderDispatcher entityRenderDispatcher;
	@Shadow
	protected float shadowRadius;

	@Shadow
	protected abstract boolean shouldShowName(Entity entity, double distanceToCameraSq);

	/**
	 * Where Minecraft has found an entity's box in view, entity occlusion culling notes the box for
	 * the GPU's test and leaves out an entity a recent test found hidden. The leash test after this
	 * one still draws a leashed entity whose leash holder is in view.
	 */
	@WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z", ordinal = 0))
	private boolean vim$cullOccludedEntity(Frustum frustum, AABB bb, Operation<Boolean> original, @Local(argsOnly = true, name = "entity") Entity entity) {
		if (!original.call(frustum, bb)) {
			return false;
		}
		EntityOcclusion occlusion = EntityOcclusion.get();
		if (!occlusion.testingEntities() || !occlusion.noteEntity(entity, bb, this.shadowRadius) || vim$showsThroughTerrain(entity)) {
			return true;
		}
		occlusion.culledEntity();
		return false;
	}

	/**
	 * Whether what Minecraft draws for an entity may show where its box is hidden: the outline of a
	 * glowing entity and a name tag show through what is in front of them, and a fishing hook's
	 * line reaches back to the rod. The camera's own entity is never left out either.
	 */
	@Unique
	private boolean vim$showsThroughTerrain(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (entity == minecraft.getCameraEntity() || entity instanceof FishingHook || minecraft.shouldEntityAppearGlowing(entity) || this.entityRenderDispatcher.camera == null) {
			return true;
		}
		return shouldShowName(entity, this.entityRenderDispatcher.distanceToSqr(entity));
	}
}
