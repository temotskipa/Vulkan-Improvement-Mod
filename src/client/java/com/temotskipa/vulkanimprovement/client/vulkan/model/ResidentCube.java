package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * What holds polygons in a {@link ModelCubeStore}, a cube or the first quad of an item's list, held
 * weakly, and how many polygons it holds.
 */
final class ResidentCube extends WeakReference<Object> {
	private final int polygons;
	
	ResidentCube(Object owner, int polygons, ReferenceQueue<Object> queue) {
		super(owner, queue);
		this.polygons = polygons;
	}
	
	int polygons() {
		return this.polygons;
	}
}