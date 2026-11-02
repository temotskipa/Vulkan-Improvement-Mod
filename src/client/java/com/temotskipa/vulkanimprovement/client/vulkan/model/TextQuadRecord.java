package com.temotskipa.vulkanimprovement.client.vulkan.model;

/**
 * A quad of laid-out text in the glyph store, and the records a frame adds for it: words, which the
 * text vertex pass reads by these offsets. A corner is a position, a colour as Minecraft's builder
 * stores it, and a texture coordinate. An instance is a text's pose and light. A job is a run of up
 * to {@link #JOB_QUADS} quads of an instance.
 */
final class TextQuadRecord {
	static final int QUAD_VERTICES = 4;
	static final int CORNER_WORDS = 6;
	static final int WORDS = QUAD_VERTICES * CORNER_WORDS;
	static final int X_WORD = 0;
	static final int Y_WORD = 1;
	static final int Z_WORD = 2;
	static final int COLOR_WORD = 3;
	static final int U_WORD = 4;
	static final int V_WORD = 5;
	
	static final int INSTANCE_WORDS = 13;
	static final int INSTANCE_LIGHT_WORD = 12;
	
	static final int JOB_WORDS = 4;
	static final int JOB_QUADS = 16;
	static final int DRAW_BITS = 24;
	static final int MAX_DRAWS = 1 << DRAW_BITS;
	
	/**
	 * A vertex of {@code DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR}.
	 */
	static final int VERTEX_WORDS = 7;
	
	private TextQuadRecord() {
	}
}