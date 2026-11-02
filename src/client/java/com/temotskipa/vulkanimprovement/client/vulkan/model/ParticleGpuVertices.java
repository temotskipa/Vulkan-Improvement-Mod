package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuOutOfMemoryException;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommands;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompiler;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;
import org.slf4j.Logger;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Particle vertices written by the GPU instead of the CPU (step 5 of the GPU-driven pipeline plan).
 * Minecraft extracts its particles into flat arrays every frame; where it would then write four
 * vertices for each, the arrays are copied to the GPU whole, and once Minecraft has uploaded its own
 * vertices one compute pass writes the particles' vertices into a buffer Minecraft draws from with
 * its own pipelines.
 *
 * <p>The path is switched with {@code vim.gpuParticleVertices}, between frames. Particles stay on
 * Minecraft's path while another mod changes a class the path stands in for (see
 * {@link #REPLACED_CLASSES}). Used from the render thread only.
 */
public final class ParticleGpuVertices {
	static final String SHADER = "assets/vulkanimprovement/shaders/model/particle_vertices.comp.glsl";
	static final String SHADER_SPIRV = "assets/vulkanimprovement/shaders/model/spirv/particle_vertices.comp.spv";
	static final int FLOATS_PER_PARTICLE = 12;
	static final int INTS_PER_PARTICLE = 2;
	static final int QUAD_VERTICES = 4;
	/**
	 * The size of {@code DefaultVertexFormat.PARTICLE}.
	 */
	static final int VERTEX_BYTES = 28;
	static final int WORKGROUP_SIZE = 64;
	/**
	 * Three addresses, then the first vertex and the particle count.
	 */
	static final int PUSH_CONSTANT_BYTES = 3 * Long.BYTES + 2 * Integer.BYTES;
	/**
	 * The classes whose work the path does in their place.
	 */
	static final Set<String> REPLACED_CLASSES = Set.of("net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer", "net.minecraft.client.renderer.state.level.QuadParticleRenderState", "net.minecraft.client.renderer.state.level.QuadParticleRenderState$Storage");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ParticleGpuVertices INSTANCE = new ParticleGpuVertices();
	
	private final List<ParticleBatch> batches = new ArrayList<>();
	private final List<ParticleVertexDraw> draws = new ArrayList<>();
	private @Nullable VulkanDevice device;
	private @Nullable ComputePipeline pipeline;
	private @Nullable ModelBufferPool inputBuffers;
	private @Nullable ModelBufferPool vertexBuffers;
	private @Nullable GpuBuffer vertices;
	private @Nullable List<String> otherModMixins;
	private @Nullable Object stagedBuffer;
	private @Nullable Object stagedBufferUsed;
	private boolean preparingLevel;
	private long gpuLayers;
	private long writtenFrames;
	private long lastFrameParticles;
	
	private ParticleGpuVertices() {
	}
	
	public static ParticleGpuVertices get() {
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
		this.batches.clear();
		this.draws.clear();
		this.vertices = null;
		this.stagedBuffer = null;
		this.stagedBufferUsed = null;
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
	 * Minecraft is about to prepare the level's features, outside any render pass.
	 */
	public void beginLevelPrepare() {
		this.preparingLevel = this.device != null && TerrainRendererDebugConfig.gpuParticleVerticesEnabled() && !ComputeCommands.insideRenderPass(RenderSystem.getDevice().createCommandEncoder()) && untouchedByOtherMods() && ready();
	}
	
	private boolean untouchedByOtherMods() {
		if (this.otherModMixins == null) {
			this.otherModMixins = OtherModMixins.changing(REPLACED_CLASSES);
			if (!this.otherModMixins.isEmpty()) {
				LOGGER.warn("[Vulkan Improvement] GPU particle vertices stay off: other mods change what they replace: {}", this.otherModMixins);
			}
		}
		return this.otherModMixins.isEmpty();
	}
	
	/**
	 * Whether the vertex pass is built, building it the first time the path is on. A device that
	 * cannot build it leaves particles on Minecraft's path until Minecraft makes a new device.
	 */
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
			LOGGER.error("[Vulkan Improvement] GPU particle vertices stay off: the vertex pass could not be built", ex);
			shutdownNow();
			return false;
		}
	}
	
	private void build(VulkanDevice vulkanDevice) {
		try (ShaderCompiler compiler = new ShaderCompiler()) {
			long module = compiler.compile(vulkanDevice, SHADER, SHADER_SPIRV, Shaderc.shaderc_compute_shader);
			try {
				this.pipeline = ComputePipeline.create(vulkanDevice, module, PUSH_CONSTANT_BYTES, "VIM Particle Vertices");
			} finally {
				VK10.vkDestroyShaderModule(vulkanDevice.vkDevice(), module, null);
			}
			LOGGER.info("[Vulkan Improvement] GPU particle vertices ready ({})", compiler.lastCompileSource());
		}
		this.inputBuffers = new ModelBufferPool(() -> "VIM Particle Records", GpuBuffer.USAGE_MAP_WRITE | ShaderStorage.USAGE_SHADER_STORAGE);
		this.vertexBuffers = new ModelBufferPool(() -> "VIM Particle Vertices", GpuBuffer.USAGE_VERTEX | ShaderStorage.USAGE_SHADER_STORAGE);
	}
	
	public void endLevelPrepare() {
		this.preparingLevel = false;
	}
	
	/**
	 * Takes a layer of particles where Minecraft would write its vertices into the draw; false
	 * leaves them to Minecraft. A subclass of Minecraft's state may write its quads its own way.
	 */
	public boolean take(Object stagedVertexBuffer, Draw draw, QuadParticleRenderState particles, Layer layer) {
		if (!this.preparingLevel || particles.getClass() != QuadParticleRenderState.class) {
			return false;
		}
		Object storage = ((ParticleLayerAccess) particles).vim$particles().get(layer);
		if (storage == null) {
			return true;
		}
		ParticleStorageAccess access = (ParticleStorageAccess) storage;
		int count = access.vim$currentParticleIndex();
		if (count == 0) {
			return true;
		}
		ModelVertexDrawHolder holder = (ModelVertexDrawHolder) draw;
		ParticleVertexDraw particleDraw = holder.vim$particleDraw();
		if (particleDraw == null) {
			particleDraw = new ParticleVertexDraw();
			holder.vim$setParticleDraw(particleDraw);
			this.draws.add(particleDraw);
			this.stagedBuffer = stagedVertexBuffer;
		}
		this.batches.add(new ParticleBatch(access.vim$floatValues(), access.vim$intValues(), count, particleDraw, particleDraw.vertexCount));
		particleDraw.vertexCount += count * QUAD_VERTICES;
		holder.vim$addQuadVertices(count * QUAD_VERTICES);
		this.gpuLayers++;
		return true;
	}
	
	/**
	 * Minecraft has uploaded the staged vertex buffer's own vertices, if it has any: copies the
	 * particles to the GPU and records the pass that writes their vertices, which runs before the
	 * draws as Minecraft's uploads do.
	 */
	public void upload(Object stagedVertexBuffer) {
		if (this.stagedBuffer != stagedVertexBuffer || this.batches.isEmpty()) {
			return;
		}
		long particles = 0L;
		for (ParticleBatch batch : this.batches) {
			particles += batch.particles();
		}
		long base = 0L;
		for (ParticleVertexDraw draw : this.draws) {
			draw.baseVertex = Math.toIntExact(base);
			base += draw.vertexCount;
		}
		long floatBytes = particles * FLOATS_PER_PARTICLE * Float.BYTES;
		GpuBuffer input = Objects.requireNonNull(this.inputBuffers).acquire(floatBytes + particles * INTS_PER_PARTICLE * Integer.BYTES);
		try (MappedView view = input.map(false, true)) {
			MemorySegment target = MemorySegment.ofBuffer(view.data());
			long floatOffset = 0L;
			long intOffset = floatBytes;
			for (ParticleBatch batch : this.batches) {
				int floats = batch.particles() * FLOATS_PER_PARTICLE;
				int ints = batch.particles() * INTS_PER_PARTICLE;
				MemorySegment.copy(batch.floats(), 0, target, ValueLayout.JAVA_FLOAT_UNALIGNED, floatOffset, floats);
				MemorySegment.copy(batch.ints(), 0, target, ValueLayout.JAVA_INT_UNALIGNED, intOffset, ints);
				floatOffset += (long) floats * Float.BYTES;
				intOffset += (long) ints * Integer.BYTES;
			}
		}
		GpuBuffer output = Objects.requireNonNull(this.vertexBuffers).acquire(base * VERTEX_BYTES);
		long inputAddress = ShaderStorage.address(input);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer pushConstants = stack.malloc(PUSH_CONSTANT_BYTES);
			pushConstants.putLong(16, ShaderStorage.address(output));
			long floatOffset = 0L;
			long intOffset = floatBytes;
			for (ParticleBatch batch : this.batches) {
				pushConstants.putLong(0, inputAddress + floatOffset);
				pushConstants.putLong(8, inputAddress + intOffset);
				pushConstants.putInt(24, batch.draw().baseVertex + batch.firstVertex());
				pushConstants.putInt(28, batch.particles());
				ComputeCommands.dispatch(encoder, () -> "VIM Particle Vertices", Objects.requireNonNull(this.pipeline), pushConstants, Math.ceilDiv(batch.particles(), WORKGROUP_SIZE), 1, 1);
				floatOffset += (long) batch.particles() * FLOATS_PER_PARTICLE * Float.BYTES;
				intOffset += (long) batch.particles() * INTS_PER_PARTICLE * Integer.BYTES;
			}
		}
		this.vertices = output;
		this.stagedBufferUsed = stagedVertexBuffer;
		this.writtenFrames++;
		this.lastFrameParticles = particles;
		this.batches.clear();
	}
	
	/**
	 * What Minecraft draws a particle draw with, or null to skip it when its vertices were never
	 * written.
	 */
	public StagedVertexBuffer.@Nullable ExecuteInfo executeInfo(ParticleVertexDraw draw) {
		if (this.vertices == null || draw.vertexCount == 0) {
			return null;
		}
		int indexCount = PrimitiveTopology.QUADS.indexCount(draw.vertexCount);
		AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		autoIndices.requestIndexCount(indexCount);
		return new ExecuteInfo(this.vertices, null, autoIndices.type(), draw.baseVertex, 0, indexCount, PrimitiveTopology.QUADS);
	}
	
	/**
	 * Minecraft has drawn everything the staged vertex buffer held.
	 */
	public void endDraw(Object stagedVertexBuffer) {
		if (this.stagedBuffer == stagedVertexBuffer) {
			this.batches.clear();
			this.draws.clear();
			this.vertices = null;
			this.stagedBuffer = null;
		}
	}
	
	/**
	 * The frame is over for a staged vertex buffer. When it is the one particles went into, the
	 * buffers the frame used go back to their pools once the GPU is done with them.
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
		return "enabled=" + TerrainRendererDebugConfig.gpuParticleVerticesEnabled() + ", ready=" + (this.pipeline != null) + ", otherModMixins=" + this.otherModMixins + ", gpuLayers=" + this.gpuLayers + ", writtenFrames=" + this.writtenFrames + ", lastFrameParticles=" + this.lastFrameParticles;
	}
}