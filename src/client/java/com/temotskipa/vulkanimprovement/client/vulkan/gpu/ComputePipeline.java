package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;

import java.nio.LongBuffer;

/**
 * A compute pipeline that reads everything through buffer addresses in its push constants, as all
 * of the mod's compute passes do, so it needs no descriptors.
 */
public record ComputePipeline(long handle, long layout) {
    public static ComputePipeline create(VulkanDevice device, long module, int pushConstantBytes, String name) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPushConstantRange.Buffer pushConstantRanges = VkPushConstantRange.calloc(1, stack).stageFlags(VK10.VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(pushConstantBytes);
            VkPipelineLayoutCreateInfo layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pPushConstantRanges(pushConstantRanges);
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
            return new ComputePipeline(pipeline, pipelineLayout);
        }
    }
    
    public void destroy(VulkanDevice device) {
        if (this.handle != 0L) {
            VK12.vkDestroyPipeline(device.vkDevice(), this.handle, null);
        }
        if (this.layout != 0L) {
            VK12.vkDestroyPipelineLayout(device.vkDevice(), this.layout, null);
        }
    }
}