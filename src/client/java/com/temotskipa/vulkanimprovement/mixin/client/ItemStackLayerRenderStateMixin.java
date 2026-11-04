package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.model.ItemLayerExtents;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Supplier;

/**
 * Lets a layer's bounding box be shared ({@link ItemStackRenderStateMixin}): the model's corners and
 * the transforms the layer puts them through.
 */
@Mixin(LayerRenderState.class)
public final class ItemStackLayerRenderStateMixin implements ItemLayerExtents {
	@Shadow
	private Supplier<Vector3fc[]> extents;
	@Shadow
	private ItemTransform itemTransform;
	@Shadow
	@Final
	private Matrix4f localTransform;

	@Override
	public Supplier<Vector3fc[]> vim$extents() {
		return this.extents;
	}

	@Override
	public ItemTransform vim$itemTransform() {
		return this.itemTransform;
	}

	@Override
	public Matrix4fc vim$localTransform() {
		return this.localTransform;
	}
}
