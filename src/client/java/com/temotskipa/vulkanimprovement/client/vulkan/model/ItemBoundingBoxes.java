package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The model bounding boxes of items, shared between items and frames. Minecraft clears an item's
 * render state every frame and works its box out again from every corner of its model, which is a
 * matrix product per corner and per item: hundreds for a flat item's extruded sides. The box
 * depends only on the model's corners, its transforms and the hand, so items with the same model
 * share one box.
 */
public final class ItemBoundingBoxes {
	/**
	 * More than any set of resources has models. Resources reloaded with new models leave the old
	 * boxes behind, which are dropped all at once when this many have gathered.
	 */
	private static final int MAX_BOXES = 4096;
	private static final Map<ItemBoundingBoxKey, AABB> BOXES = new ConcurrentHashMap<>();
	private static final AtomicLong SHARED = new AtomicLong();
	private static final AtomicLong WORKED = new AtomicLong();

	private ItemBoundingBoxes() {
	}

	public static @Nullable AABB find(ItemBoundingBoxKey key) {
		AABB box = BOXES.get(key);
		if (box != null) {
			SHARED.incrementAndGet();
		}
		return box;
	}

	public static void remember(ItemBoundingBoxKey key, AABB box) {
		WORKED.incrementAndGet();
		if (BOXES.size() >= MAX_BOXES) {
			BOXES.clear();
		}
		BOXES.put(key, box);
	}

	public static String describe() {
		return "boxes=" + BOXES.size() + ", shared=" + SHARED.get() + ", worked=" + WORKED.get();
	}
}
