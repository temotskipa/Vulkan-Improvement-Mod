package com.temotskipa.vulkanimprovement.client.vulkan.model;

/**
 * The particles of one layer as Minecraft stores them for a frame, which hold until the frame is
 * uploaded.
 *
 * @param floats      twelve values per particle
 * @param ints        two values per particle
 * @param particles   how many of them are in use
 * @param draw        the draw they go into
 * @param firstVertex where they start in the draw
 */
record ParticleBatch(float[] floats, int[] ints, int particles, ParticleVertexDraw draw, int firstVertex) {
}