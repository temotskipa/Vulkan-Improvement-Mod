package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.feature.TextFeatureRenderer.Content.Text;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.*;

/**
 * The quads of text laid out once, kept in a buffer the text vertex pass reads, and the layouts by
 * the string object Minecraft draws them from. A string counts once it is drawn in a second frame:
 * a sign keeps its strings, and text made anew every frame, which a name tag draws twice within
 * one, would only fill the store. A string that goes away is counted, as a cube is in
 * {@link ModelCubeStore}, and a store of mostly such text is started over. Used from the render
 * thread only.
 */
final class TextGlyphStore {
	private static final int INITIAL_QUADS = 16_384;
	private static final long QUAD_BYTES = (long) TextQuadRecord.WORDS * Integer.BYTES;
	private static final TextRun[] NO_RUNS = new TextRun[0];
	
	private final WeakHashMap<FormattedCharSequence, List<TextLayout>> layouts = new WeakHashMap<>();
	private final Set<FormattedCharSequence> newThisFrame = new HashSet<>();
	private final Set<ResidentCube> resident = new HashSet<>();
	private final ReferenceQueue<Object> collected = new ReferenceQueue<>();
	private final GrowingSegment copy = new GrowingSegment(INITIAL_QUADS * QUAD_BYTES);
	private int quadCount;
	private int collectedQuads;
	private @Nullable GpuBuffer buffer;
	private @Nullable MappedView mapping;
	private @Nullable MemorySegment mapped;
	private long capacity;
	
	/**
	 * What Minecraft's glyphs write for the text, or null when it stays on Minecraft's path, as
	 * it does for now in the frame a string is first seen.
	 */
	TextRun @Nullable [] runs(Font font, Text text, DisplayMode displayMode) {
		List<TextLayout> known = this.layouts.get(text.string());
		if (known == null) {
			this.layouts.put(text.string(), new ArrayList<>(1));
			this.newThisFrame.add(text.string());
			return null;
		}
		if (this.newThisFrame.contains(text.string())) {
			return null;
		}
		for (TextLayout layout : known) {
			if (layout.describes(text, displayMode)) {
				return layout.runs();
			}
		}
		TextRun[] runs = layOut(font, text, displayMode);
		known.add(new TextLayout(text.x(), text.y(), text.color(), text.dropShadow(), text.backgroundColor(), displayMode, runs));
		return runs;
	}
	
	/**
	 * Starts a frame, after which the strings of the last one count as seen before.
	 */
	void beginFrame() {
		this.newThisFrame.clear();
	}
	
	private TextRun @Nullable [] layOut(Font font, Text text, DisplayMode displayMode) {
		if (obfuscated(text.string())) {
			return null;
		}
		TextQuadCapture capture = new TextQuadCapture(displayMode);
		font.prepareText(text.string(), text.x(), text.y(), text.color(), text.dropShadow(), false, text.backgroundColor()).visit(capture);
		if (capture.failed()) {
			return null;
		}
		int quads = capture.quads();
		if (quads == 0) {
			return NO_RUNS;
		}
		int first = this.quadCount;
		long start = first * QUAD_BYTES;
		long end = (long) (first + quads) * QUAD_BYTES;
		ensureCapacity(first + quads);
		MemorySegment records = this.copy.reserve(start, end);
		MemorySegment.copy(capture.words(), 0, records, ModelGpuRecords.INT, start, quads * TextQuadRecord.WORDS);
		MemorySegment.copy(records, start, Objects.requireNonNull(this.mapped), start, end - start);
		this.quadCount += quads;
		this.resident.add(new ResidentCube(text.string(), quads, this.collected));
		List<RenderType> renderTypes = capture.renderTypes();
		TextRun[] runs = new TextRun[renderTypes.size()];
		int quad = first;
		for (int i = 0; i < runs.length; i++) {
			int runQuads = capture.runQuads().get(i);
			runs[i] = new TextRun(renderTypes.get(i), quad, runQuads);
			quad += runQuads;
		}
		return runs;
	}
	
	private static boolean obfuscated(FormattedCharSequence string) {
		boolean[] found = new boolean[1];
		string.accept((_, style, _) -> {
			found[0] |= style.isObfuscated();
			return !found[0];
		});
		return found[0];
	}
	
	private void ensureCapacity(int quads) {
		if (this.buffer != null && quads <= this.capacity) {
			return;
		}
		long grown = Math.max(Math.max(quads, INITIAL_QUADS), this.capacity * 2L);
		GpuBuffer next = RenderSystem.getDevice().createBuffer(() -> "VIM Text Quads", GpuBuffer.USAGE_MAP_WRITE | ShaderStorage.USAGE_SHADER_STORAGE, grown * QUAD_BYTES);
		MappedView nextMapping = next.map(false, true);
		MemorySegment nextMapped = MemorySegment.ofBuffer(nextMapping.data());
		long used = this.quadCount * QUAD_BYTES;
		if (used > 0L) {
			MemorySegment.copy(this.copy.segment(), 0L, nextMapped, 0L, used);
		}
		releaseBuffer();
		this.buffer = next;
		this.mapping = nextMapping;
		this.mapped = nextMapped;
		this.capacity = grown;
	}
	
	private void releaseBuffer() {
		if (this.mapping != null) {
			this.mapping.close();
			this.mapping = null;
		}
		this.mapped = null;
		if (this.buffer != null) {
			this.buffer.close();
			this.buffer = null;
		}
	}
	
	/**
	 * The device address of the quads, valid until the next text is added.
	 */
	long address() {
		ensureCapacity(this.quadCount);
		return ShaderStorage.address(Objects.requireNonNull(this.buffer));
	}
	
	/**
	 * Whether most of the quads belong to strings Minecraft no longer has.
	 */
	boolean mostlyCollected() {
		for (Reference<?> reference = this.collected.poll(); reference != null; reference = this.collected.poll()) {
			if (reference instanceof ResidentCube text && this.resident.remove(text)) {
				this.collectedQuads += text.polygons();
			}
		}
		return this.collectedQuads * 2 > this.quadCount;
	}
	
	int residentStrings() {
		return this.resident.size();
	}
	
	long residentBytes() {
		return this.quadCount * QUAD_BYTES;
	}
	
	void close() {
		releaseBuffer();
		this.copy.close();
	}
}