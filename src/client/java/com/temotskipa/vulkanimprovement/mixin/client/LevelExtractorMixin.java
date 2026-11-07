package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.EntityOcclusion;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public final class LevelExtractorMixin {
	@Shadow
	private ClientLevel level;

	/**
	 * Entity occlusion culling takes the newest results the GPU has finished before Minecraft
	 * looks at any entity of the frame.
	 */
	@Inject(method = "extract", at = @At("HEAD"))
	private void vim$beginEntityOcclusion(DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci) {
		if (VulkanImprovementRuntime.isModAvailable()) {
			EntityOcclusion.get().beginFrame(camera, this.level);
		}
	}

	/**
	 * The entity renderers' view tests made here are the ones for the frame's entities.
	 */
	@WrapMethod(method = "extractVisibleEntities")
	private void vim$lookAtEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState output, Operation<Void> original) {
		EntityOcclusion occlusion = EntityOcclusion.get();
		occlusion.lookAtEntities(true);
		try {
			original.call(camera, frustum, deltaTracker, output);
		} finally {
			occlusion.lookAtEntities(false);
		}
	}

	/**
	 * Entity occlusion culling leaves out a block entity of a section in view that a recent test
	 * found hidden, and notes the others Minecraft draws for this frame's test. Block entities drawn
	 * wherever they are, such as beacons, are not looked at here.
	 */
	@WrapOperation(method = "extractVisibleBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;", ordinal = 0))
	private @Nullable BlockEntityRenderState vim$cullOccludedBlockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float partialTicks, @Nullable CrumblingOverlay breakProgress, boolean isGloballyRendered, Operation<BlockEntityRenderState> original) {
		EntityOcclusion occlusion = EntityOcclusion.get();
		if (!occlusion.testingBlockEntities()) {
			return original.call(dispatcher, blockEntity, partialTicks, breakProgress, isGloballyRendered);
		}
		BlockPos position = blockEntity.getBlockPos();
		if (occlusion.cullsBlockEntity(position)) {
			return null;
		}
		BlockEntityRenderState state = original.call(dispatcher, blockEntity, partialTicks, breakProgress, isGloballyRendered);
		if (state != null) {
			occlusion.noteBlockEntity(position);
		}
		return state;
	}
}
