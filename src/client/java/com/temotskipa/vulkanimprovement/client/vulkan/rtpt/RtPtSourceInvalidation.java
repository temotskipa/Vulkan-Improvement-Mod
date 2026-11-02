package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDirtyReason;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldPageKind;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldRevision;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldSectionId;

public record RtPtSourceInvalidation(long updateSequence, GpuWorldSectionId sectionId, GpuWorldRevision sourceRevision, GpuWorldPageKind pageKind, GpuWorldDirtyReason reason, long invalidatedPageCount, String fallbackReason) {
}