package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

/**
 * What the last frame's visible section list came to, for reading off the render thread.
 */
record VisibleFrameSummary(int sectionLayers, int uncaptured, int belowFullVisibility, float minVisibility, boolean gpuDriven) {
}
