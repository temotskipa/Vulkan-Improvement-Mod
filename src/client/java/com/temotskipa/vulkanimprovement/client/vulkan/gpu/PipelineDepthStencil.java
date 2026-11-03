package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.StencilTest;
import com.mojang.renderpearl.api.pipeline.StencilTest.PerFace;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkStencilOpState;

/**
 * The depth, depth bias and stencil state of a Minecraft pipeline, for a mod pipeline that draws in
 * its place: no depth test without a depth state, as Minecraft compiles it.
 */
public final class PipelineDepthStencil {
	private PipelineDepthStencil() {
	}

	/**
	 * Sets the depth bias on the rasterization state and returns the depth and stencil state.
	 */
	public static VkPipelineDepthStencilStateCreateInfo create(@Nullable DepthStencilState state, VkPipelineRasterizationStateCreateInfo rasterizationState, MemoryStack stack) {
		VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default();
		if (state == null) {
			return depthStencil;
		}
		rasterizationState.depthBiasEnable(state.depthBiasConstant() != 0.0F || state.depthBiasScaleFactor() != 0.0F).depthBiasConstantFactor(state.depthBiasConstant()).depthBiasSlopeFactor(state.depthBiasScaleFactor());
		depthStencil.depthTestEnable(true).depthWriteEnable(state.writeDepth()).depthCompareOp(VulkanConst.toVk(state.depthTest()));
		StencilTest stencilTest = state.stencilTest();
		if (stencilTest != null) {
			depthStencil.stencilTestEnable(true);
			face(stencilTest.front(), depthStencil.front());
			face(stencilTest.back(), depthStencil.back());
		}
		return depthStencil;
	}

	/**
	 * Unlike Minecraft's own pipeline in 26.4-snapshot-7, which leaves the compare op at never, this
	 * sets the face's compare op too.
	 */
	private static void face(PerFace face, VkStencilOpState state) {
		state.failOp(VulkanConst.toVk(face.fail())).passOp(VulkanConst.toVk(face.pass())).depthFailOp(VulkanConst.toVk(face.depthFail())).compareOp(VulkanConst.toVk(face.compare())).compareMask(Byte.toUnsignedInt(face.compareMask())).writeMask(Byte.toUnsignedInt(face.writeMask())).reference(Byte.toUnsignedInt(face.reference()));
	}
}
