package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

record GpuWorldVisibilityCommandPass(long inputSectionCount, long culledSectionCount, long visibleSectionCount, long emittedCommandCount, long overflowedCommandCount, long fallbackCommandCount) {
}