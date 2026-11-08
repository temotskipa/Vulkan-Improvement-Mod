package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuOutOfMemoryException;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommands;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompiler;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import net.minecraft.client.renderer.feature.TextFeatureRenderer.Content;
import net.minecraft.client.renderer.feature.TextFeatureRenderer.Content.Text;
import net.minecraft.client.renderer.feature.TextFeatureRenderer.Submit;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;
import org.slf4j.Logger;

import java.util.*;

/**
 * Text laid out once and written by the GPU. Minecraft
 * lays a string out and writes its glyphs every frame, though a sign's string never changes. A
 * string it draws again goes through Minecraft's own layout and glyphs once, into the glyph store,
 * and from then on a frame records its pose and light, and one compute pass writes its vertices
 * into a buffer Minecraft draws from with its own render types.
 *
 * <p>The path is switched with {@code vim.gpuTextVertices}, between frames. Text stays on
 * Minecraft's path when it could change between frames unseen (obfuscated text), has an outline,
 * or is written in a way the path cannot take; so does all of it while another mod changes a class
 * the path stands in for (see {@link #REPLACED_CLASSES}). Used from the render thread only.
 */
public final class TextGpuVertices {
	static final String SHADER = "assets/vulkanimprovement/shaders/model/text_vertices.comp.glsl";
	static final String SHADER_SPIRV = "assets/vulkanimprovement/shaders/model/spirv/text_vertices.comp.spv";
	/**
	 * The classes whose work the path does in their place.
	 */
	static final Set<String> REPLACED_CLASSES = Set.of("net.minecraft.client.renderer.feature.TextFeatureRenderer", "net.minecraft.client.gui.Font", "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph");
	/**
	 * What a text the path takes hands Minecraft to draw: nothing.
	 */
	private static final Text NOTHING = new Text(0.0F, 0.0F, FormattedCharSequence.EMPTY, false, 0, 0, 0);
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final TextGpuVertices INSTANCE = new TextGpuVertices();
	
	private final TextVertexFrame frame = new TextVertexFrame();
	private final TextQuadCapture offerBuilder = new TextQuadCapture(DisplayMode.NORMAL);
	private @Nullable VulkanDevice device;
	private @Nullable ComputePipeline pipeline;
	private @Nullable TextGlyphStore store;
	private @Nullable ModelBufferPool inputBuffers;
	private @Nullable ModelBufferPool vertexBuffers;
	private @Nullable List<String> otherModMixins;
	private @Nullable RenderType offeredRenderType;
	private @Nullable ModelDrawGroup offeredGroup;
	private TextRun @Nullable [] offeredRuns;
	private @Nullable Object stagedBufferUsed;
	private volatile boolean fontsChanged;
	private boolean preparingLevel;
	private boolean improvedTransparency;
	private long gpuTexts;
	private long cpuTexts;
	private long writtenFrames;
	private long lastFrameTexts;
	private long lastFrameVertices;
	private long storesRenewed;
	
	private TextGpuVertices() {
	}
	
	public static TextGpuVertices get() {
		return INSTANCE;
	}
	
	/**
	 * Takes Minecraft's new device. It is still being made here, so the vertex pass is built when
	 * the path first takes a frame.
	 */
	public synchronized void configure(VulkanDevice vulkanDevice) {
		shutdownNow();
		this.device = vulkanDevice;
	}
	
	public synchronized void shutdownNow() {
		this.preparingLevel = false;
		this.frame.reset();
		this.stagedBufferUsed = null;
		if (this.store != null) {
			this.store.close();
			this.store = null;
		}
		if (this.inputBuffers != null) {
			this.inputBuffers.close();
			this.inputBuffers = null;
		}
		if (this.vertexBuffers != null) {
			this.vertexBuffers.close();
			this.vertexBuffers = null;
		}
		if (this.pipeline != null && this.device != null) {
			this.pipeline.destroy(this.device);
		}
		this.pipeline = null;
		this.device = null;
	}
	
	/**
	 * Minecraft has replaced its fonts: what the store holds points at textures that are gone.
	 */
	public void fontsChanged() {
		this.fontsChanged = true;
	}
	
	/**
	 * Minecraft is about to prepare the level's features, outside any render pass.
	 */
	public void beginLevelPrepare() {
		this.preparingLevel = this.device != null && TerrainRendererDebugConfig.gpuTextVerticesEnabled() && !ComputeCommands.insideRenderPass(RenderSystem.getDevice().createCommandEncoder()) && untouchedByOtherMods() && ready();
		if (this.preparingLevel) {
			this.improvedTransparency = Minecraft.getInstance().gameRenderer.useImprovedTransparency();
			if (this.frame.isEmpty() && (this.fontsChanged || store().mostlyCollected())) {
				this.fontsChanged = false;
				store().close();
				this.store = new TextGlyphStore();
				this.storesRenewed++;
			}
			store().beginFrame();
		}
	}
	
	private boolean untouchedByOtherMods() {
		if (this.otherModMixins == null) {
			this.otherModMixins = OtherModMixins.changing(REPLACED_CLASSES);
			if (!this.otherModMixins.isEmpty()) {
				LOGGER.warn("[Vulkan Improvement] GPU text vertices stay off: other mods change what they replace: {}", this.otherModMixins);
			}
		}
		return this.otherModMixins.isEmpty();
	}
	
	private synchronized boolean ready() {
		if (this.pipeline != null) {
			return true;
		}
		VulkanDevice vulkanDevice = this.device;
		if (vulkanDevice == null) {
			return false;
		}
		try {
			build(vulkanDevice);
			return true;
		} catch (IllegalStateException | GpuOutOfMemoryException ex) {
			LOGGER.error("[Vulkan Improvement] GPU text vertices stay off: the vertex pass could not be built", ex);
			shutdownNow();
			return false;
		}
	}
	
	private TextGlyphStore store() {
		return Objects.requireNonNull(this.store, "GPU text vertices are not configured");
	}
	
	private void build(VulkanDevice vulkanDevice) {
		try (ShaderCompiler compiler = new ShaderCompiler()) {
			long module = compiler.compile(vulkanDevice, SHADER, SHADER_SPIRV, Shaderc.shaderc_compute_shader);
			try {
				this.pipeline = ComputePipeline.create(vulkanDevice, module, TextVertexFrame.PUSH_CONSTANT_BYTES, "VIM Text Vertices");
			} finally {
				VK10.vkDestroyShaderModule(vulkanDevice.vkDevice(), module, null);
			}
			LOGGER.info("[Vulkan Improvement] GPU text vertices ready ({})", compiler.lastCompileSource());
		}
		this.store = new TextGlyphStore();
		this.inputBuffers = new ModelBufferPool(() -> "VIM Text Records", GpuBuffer.USAGE_MAP_WRITE | ShaderStorage.USAGE_SHADER_STORAGE);
		this.vertexBuffers = new ModelBufferPool(() -> "VIM Text Vertices", GpuBuffer.USAGE_VERTEX | ShaderStorage.USAGE_SHADER_STORAGE);
	}
	
	public void endLevelPrepare() {
		this.preparingLevel = false;
		this.offeredRenderType = null;
		this.offeredRuns = null;
	}
	
	/**
	 * Offers a text Minecraft is about to lay out. Returns the render type of its first quads when
	 * the GPU path takes it, which the caller asks Minecraft's draw group for, and null when it
	 * stays on Minecraft's path.
	 */
	public @Nullable RenderType offer(Submit submit, Font font) {
		if (!this.preparingLevel || !(submit.content() instanceof Text text)) {
			return null;
		}
		if (text.outlineColor() != 0) {
			this.cpuTexts++;
			return null;
		}
		TextRun[] runs = store().runs(font, text, submit.displayMode());
		if (runs == null || runs.length == 0) {
			this.cpuTexts++;
			return null;
		}
		for (TextRun run : runs) {
			if (!writesExactly(run)) {
				this.cpuTexts++;
				return null;
			}
		}
		this.offeredRuns = runs;
		this.offeredRenderType = runs[0].renderType();
		return this.offeredRenderType;
	}
	
	private boolean writesExactly(TextRun run) {
		RenderType renderType = run.renderType();
		return renderType.format() == DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR && renderType.primitiveTopology() == PrimitiveTopology.QUADS && (!renderType.sortOnUpload() || this.improvedTransparency);
	}
	
	/**
	 * The builder Minecraft's draw group hands out for the text just offered, which is never
	 * written to, and how the path comes to hold the group; otherwise null.
	 */
	public @Nullable VertexConsumer takeOffer(ModelDrawGroup group, RenderType renderType) {
		if (this.offeredRenderType != renderType) {
			return null;
		}
		this.offeredRenderType = null;
		this.offeredGroup = group;
		return this.offerBuilder;
	}
	
	/**
	 * Records the text just offered, whose draw group has taken the offer, and gives Minecraft
	 * nothing to draw for it.
	 */
	public Content record(Submit submit) {
		TextRun[] runs = Objects.requireNonNull(this.offeredRuns, "no text was offered");
		ModelDrawGroup group = Objects.requireNonNull(this.offeredGroup, "no draw group took the text");
		this.offeredRuns = null;
		this.offeredGroup = null;
		int instance = this.frame.addInstance(submit.pose(), submit.lightCoords());
		for (TextRun run : runs) {
			Draw draw = group.vim$textDraw(run.renderType());
			ModelVertexDrawHolder holder = (ModelVertexDrawHolder) draw;
			this.frame.addRun(instance, run.firstQuad(), run.quads(), Objects.requireNonNull(holder.vim$textDraw()));
			holder.vim$addQuadVertices(run.quads() * TextQuadRecord.QUAD_VERTICES);
		}
		this.gpuTexts++;
		return NOTHING;
	}
	
	/**
	 * Makes a draw Minecraft's draw group just added a GPU text draw of the frame.
	 */
	public void adopt(Object stagedVertexBuffer, Draw draw) {
		((ModelVertexDrawHolder) draw).vim$setTextDraw(this.frame.addDraw(stagedVertexBuffer));
	}
	
	/**
	 * Minecraft has uploaded the staged vertex buffer's own vertices, if it has any.
	 */
	public void upload(Object stagedVertexBuffer) {
		if (this.frame.isEmpty() || !this.frame.belongsTo(stagedVertexBuffer)) {
			return;
		}
		this.frame.write(Objects.requireNonNull(this.inputBuffers), Objects.requireNonNull(this.vertexBuffers), Objects.requireNonNull(this.pipeline), store().address());
		this.stagedBufferUsed = stagedVertexBuffer;
		this.writtenFrames++;
		this.lastFrameTexts = this.frame.instanceCount();
		this.lastFrameVertices = this.frame.vertexCount();
	}
	
	/**
	 * What Minecraft draws a text draw with, or null to skip it when its vertices were never
	 * written.
	 */
	public @Nullable ExecuteInfo executeInfo(TextVertexDraw draw) {
		return this.frame.executeInfo(draw);
	}
	
	/**
	 * Minecraft has drawn everything the staged vertex buffer held.
	 */
	public void endDraw(Object stagedVertexBuffer) {
		if (this.frame.belongsTo(stagedVertexBuffer)) {
			this.frame.reset();
		}
	}
	
	/**
	 * The frame is over for a staged vertex buffer. When it is the one text went into, the buffers
	 * the frame used go back to their pools once the GPU is done with them.
	 */
	public void endFrame(Object stagedVertexBuffer) {
		if (stagedVertexBuffer != this.stagedBufferUsed) {
			return;
		}
		if (this.inputBuffers != null) {
			this.inputBuffers.endFrame();
		}
		if (this.vertexBuffers != null) {
			this.vertexBuffers.endFrame();
		}
	}
	
	public synchronized String describe() {
		String resident = this.store == null ? "" : ", residentStrings=" + this.store.residentStrings() + ", residentBytes=" + this.store.residentBytes();
		return "enabled=" + TerrainRendererDebugConfig.gpuTextVerticesEnabled() + ", ready=" + (this.pipeline != null) + ", otherModMixins=" + this.otherModMixins + ", gpuTexts=" + this.gpuTexts + ", cpuTexts=" + this.cpuTexts + ", writtenFrames=" + this.writtenFrames + ", lastFrameTexts=" + this.lastFrameTexts + ", lastFrameVertices=" + this.lastFrameVertices + resident + ", storesRenewed=" + this.storesRenewed;
	}
}