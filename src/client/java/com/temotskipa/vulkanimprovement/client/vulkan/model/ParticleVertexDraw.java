package com.temotskipa.vulkanimprovement.client.vulkan.model;

/**
 * A particle draw of Minecraft's staged vertex buffer whose vertices the GPU writes: how many it
 * has, and once the frame is written, where they start in the frame's vertex buffer.
 */
public final class ParticleVertexDraw {
	int vertexCount;
	int baseVertex;
}