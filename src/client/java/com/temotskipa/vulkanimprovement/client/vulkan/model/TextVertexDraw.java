package com.temotskipa.vulkanimprovement.client.vulkan.model;

/**
 * A text draw of Minecraft's staged vertex buffer whose vertices the GPU writes: its index in the
 * frame, how many vertices it has, and once the frame is written, where they start in the frame's
 * vertex buffer.
 */
public final class TextVertexDraw {
	final int index;
	int vertexCount;
	int baseVertex;
	
	TextVertexDraw(int index) {
		this.index = index;
	}
}