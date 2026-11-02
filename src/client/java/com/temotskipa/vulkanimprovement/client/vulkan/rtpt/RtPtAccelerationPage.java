package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldPageKind;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldRevision;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldSectionId;

public record RtPtAccelerationPage(RtPtAccelerationDomain domain, GpuWorldSectionId sectionId, GpuWorldRevision sourceRevision, GpuWorldPageKind sourcePageKind, long blasHandle, long tlasInstanceHandle, String fallbackReason) {
	public RtPtAccelerationPage {
		if (blasHandle < 0L) {
			throw new IllegalArgumentException("BLAS handle must be opaque or zero, not negative: " + blasHandle);
		}
		if (tlasInstanceHandle < 0L) {
			throw new IllegalArgumentException("TLAS instance handle must be opaque or zero, not negative: " + tlasInstanceHandle);
		}
		fallbackReason = fallbackReason == null ? "" : fallbackReason;
	}
}