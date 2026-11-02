package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanCapabilitySnapshot;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanRuntimeProfile;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldPageKind;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldRevision;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldSectionId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RtPtAccelerationDataInvariantCheck {
	private static final Path RENDERER_DIAGNOSTICS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/RendererDiagnostics.java");
	
	private RtPtAccelerationDataInvariantCheck() {
	}
	
	
	static void main() throws IOException {
		checkRuntimeProfileKeepsRtPtOptional();
		checkRegistryLifecycleDiagnostics();
		checkRendererDiagnosticsIncludesAccelerationData();
		checkAllocationCannotBeScheduled();
	}
	
	/**
	 * The registry's gate combines the device's support with whether allocation is switched on.
	 * It is not, so no allocation task can be scheduled, and diagnostics say why.
	 */
	private static void checkAllocationCannotBeScheduled() {
		VulkanRuntimeProfile unconfigured = VulkanRuntimeProfile.from(VulkanCapabilitySnapshot.empty());
		RtPtAllocationGate gate = RtPtAccelerationDataRegistry.allocationGate(unconfigured);
		require(!gate.canScheduleAllocation(), "no allocation task may be scheduled without device support");
		require(!gate.deviceSupported(), "the gate must take device support from the runtime profile");
		require(gate.disabledReason().startsWith("requires acceleration structures"), "the gate must carry the runtime profile's reason");
		
		RtPtAccelerationDataRegistry registry = RtPtAccelerationDataRegistry.get();
		registry.reset();
		require(registry.describe().contains("allocationGate=RtPtAllocationGate["), "diagnostics must give the allocation gate and why it is closed");
	}
	
	private static void checkRuntimeProfileKeepsRtPtOptional() {
		VulkanRuntimeProfile profile = VulkanRuntimeProfile.from(VulkanCapabilitySnapshot.empty());
		require("not-ready".equals(profile.selectedPaths().rtPtReadiness()), "missing RT/PT support must select not-ready state");
		require("requires all RT/PT readiness extensions".equals(profile.disabledReasons().get("rtPtReadiness")), "missing RT/PT support must explain disabled readiness without backend failure");
	}
	
	private static void checkRegistryLifecycleDiagnostics() {
		RtPtAccelerationDataRegistry registry = RtPtAccelerationDataRegistry.get();
		registry.reset();
		
		require(registry.registeredPageCount() == 0L, "registry must reset registered count");
		require(registry.livePageCount() == 0L, "registry must reset live count");
		require(!RtPtAccelerationDataRegistry.allocationGate(VulkanRuntimeProfile.from(VulkanCapabilitySnapshot.empty())).allocationEnabled(), "registry must not enable allocation before an allocation plan exists");
		
		RtPtAccelerationPage page = samplePage();
		registry.registerPage(page);
		registry.markPendingRebuild(page, "source revision changed");
		registry.markPendingRebuild(page, "material changed");
		registry.retirePage(page, "source page retired");
		registry.retirePage(page, "section unloaded");
		registry.clearForDeviceLost("device lost");
		registry.clearForDeviceLost("driver reset");
		
		require(registry.registeredPageCount() == 1L, "registry must count registered pages");
		require(registry.retiredPageCount() == 2L, "registry must count retired pages");
		require(registry.pendingRebuildCount() == 2L, "registry must count pending rebuilds");
		require(registry.deviceLostClearCount() == 2L, "registry must count device-lost clears");
		require(registry.livePageCount() == 0L, "device-lost clear must leave no live pages");
		
		for (String reason : new String[]{"source revision changed", "material changed", "source page retired", "section unloaded", "device lost", "driver reset"}) {
			require(registry.fallbackReasonCount(reason) == 1L, "registry must count each fallback reason apart: " + reason);
		}
		
		registry.reset();
	}
	
	private static void checkRendererDiagnosticsIncludesAccelerationData() throws IOException {
		String source = Files.readString(RENDERER_DIAGNOSTICS);
		require(source.contains("\"rtPtAccelerationData: \""), "bug reports must include RT/PT acceleration-data diagnostics");
		require(source.contains("RtPtAccelerationDataRegistry.get().describe()"), "bug reports must read RT/PT acceleration diagnostics from the registry");
	}
	
	private static RtPtAccelerationPage samplePage() {
		return new RtPtAccelerationPage(RtPtAccelerationDomain.TERRAIN, GpuWorldSectionId.fromBlockPosition(16, 80, 32), GpuWorldRevision.of(7L), GpuWorldPageKind.CANONICAL_MIRROR, 11L, 13L, "test fallback");
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}