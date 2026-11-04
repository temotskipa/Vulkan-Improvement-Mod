package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ItemBoundingBoxKey;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ItemBoundingBoxes;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Shares an item's model bounding box between items and frames ({@link ItemBoundingBoxes}).
 * Minecraft keeps the box only until it clears the render state, which it does every frame, and
 * works it out again from every corner of the model; the box Minecraft works out for one item is
 * the box of every item with the same model, transforms and hand.
 */
@Mixin(ItemStackRenderState.class)
public final class ItemStackRenderStateMixin {
	@Shadow
	ItemDisplayContext displayContext;
	@Shadow
	private int activeLayerCount;
	@Shadow
	private @Nullable AABB cachedModelBoundingBox;
	@Shadow
	private LayerRenderState[] layers;

	@WrapMethod(method = "getModelBoundingBox")
	private AABB vim$shareModelBoundingBox(Operation<AABB> original) {
		if (this.cachedModelBoundingBox != null || this.activeLayerCount == 0 || !TerrainRendererDebugConfig.shareItemBoundingBoxes() || !VulkanImprovementRuntime.isModAvailable()) {
			return original.call();
		}
		ItemBoundingBoxKey key = new ItemBoundingBoxKey(this.displayContext.leftHand(), this.layers, this.activeLayerCount);
		AABB box = ItemBoundingBoxes.find(key);
		if (box != null) {
			this.cachedModelBoundingBox = box;
			return box;
		}
		box = original.call();
		ItemBoundingBoxes.remember(key, box);
		return box;
	}
}
