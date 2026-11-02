package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

public record RtPtAllocationGate(boolean deviceSupported, boolean allocationEnabled, String disabledReason) {
	public boolean canScheduleAllocation() {
		return this.deviceSupported && this.allocationEnabled;
	}
}