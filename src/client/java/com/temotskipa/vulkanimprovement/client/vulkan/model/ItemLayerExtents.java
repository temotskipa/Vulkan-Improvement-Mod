package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

import java.util.function.Supplier;

/**
 * What a layer of an item's render state takes its bounding box from: its model's corners and the
 * two transforms Minecraft puts them through.
 */
public interface ItemLayerExtents {
	Supplier<Vector3fc[]> vim$extents();

	ItemTransform vim$itemTransform();

	Matrix4fc vim$localTransform();
}
