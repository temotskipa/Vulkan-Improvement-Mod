package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Arrays;

/**
 * Everything an item's model bounding box is worked out from: for each layer its model's corners
 * and item transform, which belong to the baked model and are told apart by identity, and its local
 * transform, which Minecraft copies into the layer and is told apart by value; and whether the item
 * is held in the left hand.
 */
public final class ItemBoundingBoxKey {
	private final boolean leftHand;
	private final Object[] extents;
	private final Object[] itemTransforms;
	private final Matrix4f[] localTransforms;
	private final int hash;

	public ItemBoundingBoxKey(boolean leftHand, Object[] layers, int layerCount) {
		this.leftHand = leftHand;
		this.extents = new Object[layerCount];
		this.itemTransforms = new Object[layerCount];
		this.localTransforms = new Matrix4f[layerCount];
		int hash = Boolean.hashCode(leftHand);
		for (int i = 0; i < layerCount; i++) {
			ItemLayerExtents layer = (ItemLayerExtents) layers[i];
			this.extents[i] = layer.vim$extents();
			this.itemTransforms[i] = layer.vim$itemTransform();
			Matrix4fc localTransform = layer.vim$localTransform();
			this.localTransforms[i] = new Matrix4f(localTransform);
			hash = 31 * (31 * (31 * hash + System.identityHashCode(this.extents[i])) + System.identityHashCode(this.itemTransforms[i])) + this.localTransforms[i].hashCode();
		}
		this.hash = hash;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof ItemBoundingBoxKey key) || key.hash != this.hash || key.leftHand != this.leftHand || key.extents.length != this.extents.length) {
			return false;
		}
		for (int i = 0; i < this.extents.length; i++) {
			if (key.extents[i] != this.extents[i] || key.itemTransforms[i] != this.itemTransforms[i]) {
				return false;
			}
		}
		return Arrays.equals(key.localTransforms, this.localTransforms);
	}

	@Override
	public int hashCode() {
		return this.hash;
	}
}
