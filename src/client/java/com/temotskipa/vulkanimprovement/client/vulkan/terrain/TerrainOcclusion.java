package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPassLayouts;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.concurrent.atomic.LongAdder;

/**
 * Culls GPU-driven terrain by occlusion, in two phases, so that terrain coming into sight is never
 * missing for a frame. In the first phase, inside Minecraft's render pass for opaque terrain, the
 * GPU draws only the pages that were seen in the frame before. Once Minecraft closes that pass,
 * the depth it holds becomes a depth pyramid, every page in view is tested against it, and the
 * pages seen now that were left out are drawn in a render pass of the mod's on the same target,
 * before Minecraft's transparency passes read its depth. Each page's result is kept on the GPU for
 * the next frame's first phase; the CPU never reads it.
 * <p>
 * The second phase runs where Minecraft draws with improved transparency, which is when it closes
 * the opaque pass before anything else is drawn into the target, so only frames with improved
 * transparency cull. Render thread only.
 */
public final class TerrainOcclusion {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final TerrainOcclusion INSTANCE = new TerrainOcclusion();
	private final TerrainDepthPyramid pyramid = new TerrainDepthPyramid();
	private final int[] lateOutputs = new int[TerrainGpuLayout.SECTION_PAGES_LAYERS];
	private final int[] lateCommands = new int[TerrainGpuLayout.SECTION_PAGES_LAYERS];
	// Per layer: the second phase's draw, and the pipeline state and fog the first phase drew with.
	private final TerrainMeshTaskDispatch[] lateDispatches = new TerrainMeshTaskDispatch[ChunkSectionLayer.values().length];
	private final TerrainPipelineState[] firstPhasePipelines = new TerrainPipelineState[ChunkSectionLayer.values().length];
	private final boolean[] firstPhaseImprovedFog = new boolean[ChunkSectionLayer.values().length];
	private final LongAdder occlusionFrames = new LongAdder();
	private final LongAdder secondPhaseDraws = new LongAdder();
	private final LongAdder secondPhaseRefusals = new LongAdder();
	private final LongAdder untestedFrames = new LongAdder();
	private @Nullable TerrainGpuDrivenFrame frame;
	private @Nullable VisibleSectionGrid sections;
	private int slotCount;
	private int fadeMillis;
	// Whether the frame being recorded left pages to its second phase, which has not run yet.
	private boolean armed;
	private @Nullable String offReason;

	private TerrainOcclusion() {
	}

	public static TerrainOcclusion get() {
		return INSTANCE;
	}

	/**
	 * Called every frame as Minecraft begins collecting the frame's sections, so that no frame's
	 * second phase is taken for a later one. A frame that left pages to a second phase that never
	 * ran drew without them, so occlusion culling is then off until the renderer is configured again.
	 */
	public void beginFrame() {
		if (this.armed) {
			this.armed = false;
			turnOff("a frame's second phase did not run");
		}
		this.frame = null;
		this.sections = null;
		Arrays.fill(this.lateOutputs, 0);
		Arrays.fill(this.lateCommands, -1);
		Arrays.fill(this.lateDispatches, null);
		Arrays.fill(this.firstPhasePipelines, null);
	}

	/**
	 * Whether the GPU-driven frame being prepared culls by occlusion, decided before Minecraft
	 * opens a render pass.
	 */
	boolean cullsFrame() {
		if (!TerrainRendererDebugConfig.terrainOcclusionCullingEnabled() || this.offReason != null || MeshShaderTerrainProgram.get().depthPyramidPipeline() == null) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		GpuTexture depth = minecraft.gameRenderer.mainRenderTarget().getDepthTexture();
		return minecraft.gameRenderer.useImprovedTransparency() && depth != null && depthSupported(depth);
	}

	/**
	 * Whether the pyramid can be built from a depth image: one of depth alone, as Minecraft's main
	 * target has, whose view Minecraft makes the pyramid pass can sample. A view of depth and
	 * stencil cannot be sampled.
	 */
	private static boolean depthSupported(GpuTexture depth) {
		return depth.getFormat() == GpuFormat.D32_FLOAT;
	}

	/**
	 * Notes where a layer's second-phase tasks go and the draw that launches them.
	 */
	void prepareLayer(int layerOrdinal, int output, int commandIndex, TerrainMeshTaskDispatch dispatch) {
		this.lateOutputs[layerOrdinal] = output;
		this.lateCommands[layerOrdinal] = commandIndex;
		this.lateDispatches[layerOrdinal] = dispatch;
	}

	/**
	 * Called once the first phase has been recorded, which left the pages hidden in the frame before
	 * to the second.
	 */
	void arm(TerrainGpuDrivenFrame frame, VisibleSectionGrid sections, int slotCount, int fadeMillis) {
		this.frame = frame;
		this.sections = sections;
		this.slotCount = slotCount;
		this.fadeMillis = fadeMillis;
		this.armed = true;
		this.occlusionFrames.increment();
	}

	/**
	 * Called when the mesh path has drawn a layer's first-phase terrain in Minecraft's render pass,
	 * with the pipeline state and fog it drew with, which the second phase draws the layer with too.
	 */
	void drewFirstPhase(int layerOrdinal, TerrainPipelineState pipeline, boolean improvedFog) {
		if (this.armed && layerOrdinal >= 0 && layerOrdinal < this.firstPhasePipelines.length) {
			this.firstPhasePipelines[layerOrdinal] = pipeline;
			this.firstPhaseImprovedFog[layerOrdinal] = improvedFog;
		}
	}

	/**
	 * Called once Minecraft has closed the render pass it draws opaque terrain and features in,
	 * before its transparency passes, with the target it drew into. Records the second phase:
	 * the depth pyramid, the test of every page in view, and the draw of the pages seen now that
	 * the first phase left out. A target whose depth the pyramid cannot be built from has every
	 * page the first phase left out drawn untested. The pyramid is built, and the boxes of
	 * {@link EntityOcclusion} tested against it, in frames that noted any, whether or not the
	 * frame's terrain culls by occlusion.
	 */
	public void drawSecondPhase(RenderTarget mainTarget) {
		EntityOcclusion entities = EntityOcclusion.get();
		boolean terrain = this.armed;
		if (!terrain && !entities.wantsTest()) {
			return;
		}
		this.armed = false;
		TerrainGpuDrivenFrame activeFrame = this.frame;
		VisibleSectionGrid activeSections = this.sections;
		MeshShaderTerrainProgram program = MeshShaderTerrainProgram.get();
		VulkanDevice device = program.device();
		ComputePipeline pyramidPipeline = program.depthPyramidPipeline();
		if (device == null || pyramidPipeline == null || terrain && (activeFrame == null || activeSections == null)) {
			if (terrain) {
				turnOff("the renderer was shut down during a frame");
			}
			return;
		}
		TerrainPerformanceMonitor monitor = TerrainPerformanceMonitor.get();
		monitor.markPyramid(false);
		VkCommandBuffer commandBuffer = TerrainRenderPassAccess.frameCommandBuffer(device.createCommandEncoder());
		if (commandBuffer == null) {
			if (terrain) {
				turnOff("a render pass was open where the second phase runs");
			}
			return;
		}
		GpuTexture depth = mainTarget.getDepthTexture();
		boolean tested = depth != null && depthSupported(depth) && mainTarget.getDepthTextureView() instanceof VulkanGpuTextureView;
		if (tested) {
			this.pyramid.ensure(device, depth.getWidth(0), depth.getHeight(0));
			tested = this.pyramid.ready();
		}
		if (tested) {
			this.pyramid.record(commandBuffer, pyramidPipeline, ((VulkanGpuTextureView) mainTarget.getDepthTextureView()).vkImageView());
		}
		else if (terrain) {
			this.untestedFrames.increment();
		}
		monitor.markPyramid(true);
		if (tested) {
			entities.recordTest(commandBuffer, device, program, this.pyramid);
		}
		if (!terrain) {
			if (tested) {
				TerrainDepthPyramid.barrierBeforeDepthWrites(commandBuffer);
			}
			return;
		}
		monitor.markOcclusion(false);
		if (!program.prepareLatePages(commandBuffer, activeFrame, activeSections, this.slotCount, this.lateOutputs, this.lateCommands, this.fadeMillis, tested ? this.pyramid : null)) {
			if (tested) {
				TerrainDepthPyramid.barrierBeforeDepthWrites(commandBuffer);
			}
			turnOff("the second phase's test could not be recorded");
			return;
		}
		drawLateTerrain(program, mainTarget);
		monitor.markOcclusion(true);
	}

	/**
	 * Draws each layer's second-phase tasks with the pipeline state its first phase was drawn with,
	 * which was created for Minecraft's opaque pass, in a pass of the same layout on the same
	 * target that keeps what is in it.
	 */
	private void drawLateTerrain(MeshShaderTerrainProgram program, RenderTarget mainTarget) {
		GpuTextureView color = mainTarget.getColorTextureView();
		GpuTextureView depth = mainTarget.getDepthTextureView();
		if (color == null || depth == null) {
			this.secondPhaseRefusals.increment();
			return;
		}
		DescriptorHeapTerrainResources resources = DescriptorHeapTerrainResources.get();
		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "VIM Terrain Occlusion Second Phase", RenderPassLayouts.COLOR_DEPTH, color, depth)) {
			TerrainRenderPassAccess access = TerrainRenderPassAccess.of(renderPass);
			if (access == null) {
				this.secondPhaseRefusals.increment();
				return;
			}
			TerrainRenderContext.enter();
			try {
				for (int layer = 0; layer < this.lateDispatches.length; layer++) {
					TerrainMeshTaskDispatch dispatch = this.lateDispatches[layer];
					if (dispatch == null) {
						continue;
					}
					TerrainPipelineState pipeline = this.firstPhasePipelines[layer];
					if (pipeline == null || !RenderPassLayouts.COLOR_DEPTH.equals(pipeline.subpass().renderPassLayout()) || pipeline.subpass().index() != 0) {
						this.secondPhaseRefusals.increment();
						continue;
					}
					TerrainRenderContext.setImprovedFog(this.firstPhaseImprovedFog[layer]);
					if (program.drawTerrain(access.vim$commandBuffer(), pipeline, dispatch, resources.meshletHeaderAddress(), resources.meshletVertexPayloadAddress(), resources.meshletIndexPayloadAddress())) {
						this.secondPhaseDraws.increment();
					}
					else {
						this.secondPhaseRefusals.increment();
					}
				}
			} finally {
				TerrainRenderContext.exit();
			}
		}
	}

	private void turnOff(String reason) {
		if (this.offReason == null) {
			this.offReason = reason;
			LOGGER.warn("[Vulkan Improvement] Terrain occlusion culling is off until the renderer is configured again: {}", reason);
		}
	}

	/**
	 * Called when the renderer is configured for a device: the pyramid of the last one, and the
	 * ring of entity occlusion, are given back, and occlusion culling may run again.
	 */
	void reset() {
		this.pyramid.close();
		this.armed = false;
		this.offReason = null;
		EntityOcclusion.get().reset();
	}

	/**
	 * Called when the renderer shuts down at once, with the GPU done with the pyramid.
	 */
	void shutdownNow() {
		this.pyramid.destroyNow();
		this.armed = false;
		EntityOcclusion.get().shutdownNow();
	}

	/**
	 * Includes what the GPU counted: the pages tested, the ones found hidden, and the ones drawn in
	 * the second phase, since the terrain buffers were created.
	 */
	public String describe() {
		long[] counted = DescriptorHeapTerrainResources.get().occlusionCounters();
		return "enabled=" + TerrainRendererDebugConfig.terrainOcclusionCullingEnabled() + ", offReason=" + (this.offReason == null ? "none" : this.offReason) + ", testedPages=" + counted[0] + ", hiddenPages=" + counted[1] + ", secondPhasePages=" + counted[2] + ", occlusionFrames=" + this.occlusionFrames.sum() + ", secondPhaseDraws=" + this.secondPhaseDraws.sum() + ", secondPhaseRefusals=" + this.secondPhaseRefusals.sum() + ", untestedFrames=" + this.untestedFrames.sum() + ", pyramid=" + this.pyramid.width() + "x" + this.pyramid.height() + "/" + this.pyramid.levels();
	}
}
