package com.temotskipa.vulkanimprovement.client.vulkan.model;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer.Content.Text;
import org.jspecify.annotations.Nullable;

/**
 * A text as Minecraft lays it out, for everything but its string, whose object it is kept under.
 *
 * @param runs what the glyph store holds of it, or null when it stays on Minecraft's path
 */
record TextLayout(float x, float y, int color, boolean dropShadow, int backgroundColor, DisplayMode displayMode, TextRun @Nullable [] runs) {
	boolean describes(Text text, DisplayMode mode) {
		return this.x == text.x() && this.y == text.y() && this.color == text.color() && this.dropShadow == text.dropShadow() && this.backgroundColor == text.backgroundColor() && this.displayMode == mode;
	}
}