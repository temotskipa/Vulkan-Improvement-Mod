package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.nio.LongBuffer;
import java.util.List;

/**
 * What Minecraft builds a framebuffer from: the render pass, the attachment views in order,
 * {@code VK_NULL_HANDLE} for an unused color attachment, and the size.
 */
record FramebufferKey(long renderPass, List<Long> views, int width, int height) {
    static FramebufferKey of(VkFramebufferCreateInfo createInfo) {
        LongBuffer attachments = createInfo.pAttachments();
        Long[] views = new Long[attachments == null ? 0 : attachments.remaining()];
        for (int i = 0; i < views.length; i++) {
            views[i] = attachments.get(attachments.position() + i);
        }
        return new FramebufferKey(createInfo.renderPass(), List.of(views), createInfo.width(), createInfo.height());
    }
}