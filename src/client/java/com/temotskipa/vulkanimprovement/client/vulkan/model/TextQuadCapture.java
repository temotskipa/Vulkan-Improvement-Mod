package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.Font.GlyphVisitor;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Takes down what Minecraft's own glyphs write for a text, under no pose, so that everything a
 * font does (shadows, bold, italics, underlines, backgrounds, sprites) comes out as Minecraft does
 * it, as quads kept in the glyph store. A glyph that writes something else than a quad of position,
 * colour, texture coordinate and light leaves the text on Minecraft's path.
 */
@NullMarked
final class TextQuadCapture implements VertexConsumer, GlyphVisitor {
	private static final Matrix4f NO_POSE = new Matrix4f();
	private final DisplayMode displayMode;
	private final List<RenderType> renderTypes = new ArrayList<>();
	private final List<Integer> runQuads = new ArrayList<>();
	private int[] words = new int[TextQuadRecord.WORDS * 64];
	private int vertex = -1;
	private boolean failed;
	
	TextQuadCapture(DisplayMode displayMode) {
		this.displayMode = displayMode;
	}
	
	@Override
	public void acceptRenderable(TextRenderable renderable) {
		int before = this.vertex;
		add(renderable);
		int quads = (this.vertex - before) / TextQuadRecord.QUAD_VERTICES;
		if (quads > 0) {
			RenderType renderType = renderable.renderType(this.displayMode);
			int last = this.renderTypes.size() - 1;
			if (last >= 0 && this.renderTypes.get(last) == renderType) {
				this.runQuads.set(last, this.runQuads.get(last) + quads);
			}
			else {
				this.renderTypes.add(renderType);
				this.runQuads.add(quads);
			}
		}
	}
	
	/**
	 * Has a renderable write itself into the capture, as {@code TextFeatureRenderer} has it write
	 * into a builder.
	 */
	void add(TextRenderable renderable) {
		renderable.render(NO_POSE, this, 0, false);
	}
	
	boolean failed() {
		return this.failed || (this.vertex + 1) % TextQuadRecord.QUAD_VERTICES != 0;
	}
	
	int quads() {
		return (this.vertex + 1) / TextQuadRecord.QUAD_VERTICES;
	}
	
	int[] words() {
		return this.words;
	}
	
	List<RenderType> renderTypes() {
		return this.renderTypes;
	}
	
	List<Integer> runQuads() {
		return this.runQuads;
	}
	
	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		this.vertex++;
		int end = (this.vertex + 1) * TextQuadRecord.CORNER_WORDS;
		if (end > this.words.length) {
			this.words = Arrays.copyOf(this.words, this.words.length * 2);
		}
		this.words[corner(TextQuadRecord.X_WORD)] = Float.floatToRawIntBits(x);
		this.words[corner(TextQuadRecord.Y_WORD)] = Float.floatToRawIntBits(y);
		this.words[corner(TextQuadRecord.Z_WORD)] = Float.floatToRawIntBits(z);
		return this;
	}
	
	private int corner(int word) {
		return this.vertex * TextQuadRecord.CORNER_WORDS + word;
	}
	
	@Override
	public VertexConsumer setColor(int r, int g, int b, int a) {
		unsupported();
		return this;
	}
	
	private void unsupported() {
		this.failed = true;
	}
	
	@Override
	public VertexConsumer setColor(int color) {
		this.words[corner(TextQuadRecord.COLOR_WORD)] = ARGB.toABGR(color);
		return this;
	}
	
	@Override
	public VertexConsumer setUv(float u, float v) {
		this.words[corner(TextQuadRecord.U_WORD)] = Float.floatToRawIntBits(u);
		this.words[corner(TextQuadRecord.V_WORD)] = Float.floatToRawIntBits(v);
		return this;
	}
	
	@Override
	public VertexConsumer setUv1(int u, int v) {
		unsupported();
		return this;
	}
	
	@Override
	public VertexConsumer setUv2(int u, int v) {
		unsupported();
		return this;
	}
	
	@Override
	public VertexConsumer setUv3(float u, float v) {
		unsupported();
		return this;
	}
	
	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		unsupported();
		return this;
	}
	
	@Override
	public VertexConsumer setLineWidth(float width) {
		unsupported();
		return this;
	}
	
	@Override
	public VertexConsumer setLight(int packedLightCoords) {
		return this;
	}
	
	@Override
	public VertexConsumer setOverlay(int packedOverlayCoords) {
		unsupported();
		return this;
	}
}