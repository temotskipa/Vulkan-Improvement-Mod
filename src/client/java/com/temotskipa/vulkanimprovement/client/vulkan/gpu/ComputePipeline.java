package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

/**
 * A compute pipeline that reads everything through buffer addresses in its push constants, as all
 * of the mod's compute passes do, so it needs no descriptors. One that reads an image of
 * Minecraft's takes it as a pushed descriptor, set 0, binding 0.
 *
 * @param descriptorSetLayout the layout of the pushed image's set, or 0 for a pipeline without one
 */
public record ComputePipeline(long handle, long layout, int pushConstantBytes, long descriptorSetLayout) {
	public static ComputePipeline create(VulkanDevice device, long module, int pushConstantBytes, String name) {
		return create(device, module, pushConstantBytes, name, 0L);
	}

	/**
	 * A pipeline that also reads one sampled image, pushed with
	 * {@link KHRPushDescriptor#vkCmdPushDescriptorSetKHR} before it is dispatched.
	 */
	public static ComputePipeline createWithPushedImage(VulkanDevice device, long module, int pushConstantBytes, String name) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack).binding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE).descriptorCount(1).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT);
			VkDescriptorSetLayoutCreateInfo layoutCreateInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().flags(KHRPushDescriptor.VK_DESCRIPTOR_SET_LAYOUT_CREATE_PUSH_DESCRIPTOR_BIT_KHR).pBindings(binding);
			LongBuffer layoutHandle = stack.callocLong(1);
			VulkanUtils.crashIfFailure(device, VK12.vkCreateDescriptorSetLayout(device.vkDevice(), layoutCreateInfo, null, layoutHandle), "Failed to create " + name + " descriptor set layout");
			VulkanObjectNames.name(device, VK10.VK_OBJECT_TYPE_DESCRIPTOR_SET_LAYOUT, layoutHandle.get(0), name + " Descriptor Set Layout");
			return create(device, module, pushConstantBytes, name, layoutHandle.get(0));
		}
	}

	private static ComputePipeline create(VulkanDevice device, long module, int pushConstantBytes, String name, long descriptorSetLayout) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushConstantBytes);
			VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pPushConstantRanges(pushConstantRanges);
			if (descriptorSetLayout != 0L) {
				layoutCreateInfo.pSetLayouts(stack.longs(descriptorSetLayout));
			}
			LongBuffer layoutHandle = stack.callocLong(1);
			VulkanUtils.crashIfFailure(device, VK12.vkCreatePipelineLayout(device.vkDevice(), layoutCreateInfo, null, layoutHandle), "Failed to create " + name + " pipeline layout");
			long pipelineLayout = layoutHandle.get(0);

			VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack).sType$Default().stage(VK10.VK_SHADER_STAGE_COMPUTE_BIT).module(module).pName(stack.UTF8("main"));
			VkComputePipelineCreateInfo.Buffer pipelineCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack).sType$Default().stage(shaderStage).layout(pipelineLayout);
			LongBuffer pipelineHandle = stack.callocLong(1);
			VulkanUtils.crashIfFailure(device, VK12.vkCreateComputePipelines(device.vkDevice(), 0L, pipelineCreateInfo, null, pipelineHandle), "Failed to create " + name + " compute pipeline");
			long pipeline = pipelineHandle.get(0);
			VulkanObjectNames.name(device, VK10.VK_OBJECT_TYPE_PIPELINE_LAYOUT, pipelineLayout, name + " Pipeline Layout");
			VulkanObjectNames.name(device, VK10.VK_OBJECT_TYPE_PIPELINE, pipeline, name + " Pipeline");
			return new ComputePipeline(pipeline, pipelineLayout, pushConstantBytes, descriptorSetLayout);
		}
	}

	public void destroy(VulkanDevice device) {
		if (this.handle != 0L) {
			VK12.vkDestroyPipeline(device.vkDevice(), this.handle, null);
		}
		if (this.layout != 0L) {
			VK12.vkDestroyPipelineLayout(device.vkDevice(), this.layout, null);
		}
		if (this.descriptorSetLayout != 0L) {
			VK12.vkDestroyDescriptorSetLayout(device.vkDevice(), this.descriptorSetLayout, null);
		}
	}
}
