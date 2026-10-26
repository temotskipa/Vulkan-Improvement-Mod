package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.renderpearl.api.device.GpuDevice;
import org.jspecify.annotations.Nullable;

record VulkanRuntimeBackend(@Nullable GpuDevice device, boolean vulkan) {
}