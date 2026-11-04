package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBuffer;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBufferPlacement;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

/**
 * Ever smaller levels over the frame's depth image, each texel holding the farthest depth of the
 * pixels under it, all in one buffer the GPU reaches by its address. Level 1, half the image's
 * size, comes first; the image itself is read where it is. The occlusion test reads the level at
 * which a section's rectangle on the screen spans at most two texels each way. Render thread only.
 */
final class TerrainDepthPyramid {
	private @Nullable DeviceBuffer buffer;
	private int width;
	private int height;
	private int levels;

	/**
	 * Makes room for a pyramid over a depth image of the given size, replacing the buffer when the
	 * size changed. A buffer given back is destroyed once the frames that use it are done.
	 */
	void ensure(VulkanDevice device, int width, int height) {
		if (this.buffer != null && this.width == width && this.height == height) {
			return;
		}
		close();
		long texels = 0L;
		int levelCount = 0;
		for (int levelWidth = width, levelHeight = height; levelWidth > 1 || levelHeight > 1; ) {
			levelWidth = (levelWidth + 1) / 2;
			levelHeight = (levelHeight + 1) / 2;
			texels += (long) levelWidth * levelHeight;
			levelCount++;
		}
		if (levelCount == 0) {
			return;
		}
		this.buffer = new DeviceBuffer(device, "VIM Terrain Depth Pyramid", texels * Float.BYTES, VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, DeviceBufferPlacement.DEVICE);
		this.width = width;
		this.height = height;
		this.levels = levelCount;
	}

	boolean ready() {
		return this.buffer != null;
	}

	long address() {
		return this.buffer == null ? 0L : this.buffer.deviceAddress();
	}

	/**
	 * The width of the depth image the pyramid is built over.
	 */
	int width() {
		return this.width;
	}

	int height() {
		return this.height;
	}

	/**
	 * How many levels the pyramid stores above the image.
	 */
	int levels() {
		return this.levels;
	}

	/**
	 * Builds the levels outside any render pass, six at a time, each dispatch behind a barrier on
	 * the one before. The first reads the depth image, which Minecraft keeps in the general layout
	 * and its render pass has written before this; the last barrier makes the pyramid visible to
	 * the compute pass that reads it.
	 *
	 * @param depthView a view of only the depth of an image of the pyramid's size
	 */
	void record(VkCommandBuffer commandBuffer, ComputePipeline pipeline, long depthView) {
		DeviceBuffer target = this.buffer;
		if (target == null) {
			throw new IllegalStateException("The depth pyramid has no buffer");
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			// The depth the render pass wrote is read; the pyramid the last frame's test read is overwritten.
			barrierBeforeCompute(commandBuffer, stack, VK13.VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK13.VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT | VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK13.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK13.VK_ACCESS_2_SHADER_SAMPLED_READ_BIT | VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
			VK12.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.handle());
			VkDescriptorImageInfo.Buffer image = VkDescriptorImageInfo.calloc(1, stack).imageView(depthView).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
			VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack).sType$Default().dstBinding(0).descriptorCount(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE).pImageInfo(image);
			KHRPushDescriptor.vkCmdPushDescriptorSetKHR(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.layout(), 0, write);
			ByteBuffer pushConstants = stack.malloc(TerrainGpuLayout.DEPTH_PYRAMID_PUSH_CONSTANT_BYTES);
			for (int source = 0; source < this.levels; source += TerrainGpuLayout.DEPTH_PYRAMID_LEVELS_PER_DISPATCH) {
				int sourceWidth = levelSize(this.width, source);
				int sourceHeight = levelSize(this.height, source);
				pushConstants.clear();
				pushConstants.putLong(target.deviceAddress());
				pushConstants.putInt(this.width).putInt(this.height);
				pushConstants.putInt(source).putInt(this.levels);
				pushConstants.flip();
				VK12.vkCmdPushConstants(commandBuffer, pipeline.layout(), VK10.VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstants);
				VK12.vkCmdDispatch(commandBuffer, Math.ceilDiv(sourceWidth, TerrainGpuLayout.DEPTH_PYRAMID_TILE), Math.ceilDiv(sourceHeight, TerrainGpuLayout.DEPTH_PYRAMID_TILE), 1);
				barrierBeforeCompute(commandBuffer, stack, VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT, VK13.VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK13.VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
			}
		}
	}

	/**
	 * A level's size along one axis: the image's, halved as many times as the level's number and
	 * rounded up.
	 */
	private static int levelSize(int imageSize, int level) {
		return (int) (((long) imageSize + (1L << level) - 1L) >> level);
	}

	/**
	 * A barrier that makes earlier work visible to the compute shaders after it, which are all
	 * that follow one here: the pyramid's next dispatch, or the pass that tests against it.
	 */
	private static void barrierBeforeCompute(VkCommandBuffer commandBuffer, MemoryStack stack, long sourceStages, long sourceAccess, long targetAccess) {
		VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack).sType$Default().srcStageMask(sourceStages).srcAccessMask(sourceAccess).dstStageMask(VK13.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT).dstAccessMask(targetAccess);
		VK13.vkCmdPipelineBarrier2(commandBuffer, VkDependencyInfo.calloc(stack).sType$Default().pMemoryBarriers(barrier));
	}

	void close() {
		DeviceBuffer active = this.buffer;
		if (active != null) {
			active.close();
		}
		forget();
	}

	/**
	 * Destroys the buffer at once, for a renderer shutting down with the GPU done with it.
	 */
	void destroyNow() {
		DeviceBuffer active = this.buffer;
		if (active != null) {
			active.destroyNow();
		}
		forget();
	}

	private void forget() {
		this.buffer = null;
		this.width = 0;
		this.height = 0;
		this.levels = 0;
	}
}
