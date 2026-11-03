package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

/**
 * How Minecraft samples terrain textures in the frame being drawn, which it tells its own shaders
 * in the global settings it uploads once a frame.
 */
public final class TerrainSamplingState {
	private static volatile boolean rotatedGridSampling;

	private TerrainSamplingState() {
	}

	/**
	 * @param rotatedGrid whether the frame uses Minecraft's RGSS texture filtering
	 */
	public static void record(boolean rotatedGrid) {
		rotatedGridSampling = rotatedGrid;
	}

	static boolean rotatedGridSampling() {
		return rotatedGridSampling;
	}
}
