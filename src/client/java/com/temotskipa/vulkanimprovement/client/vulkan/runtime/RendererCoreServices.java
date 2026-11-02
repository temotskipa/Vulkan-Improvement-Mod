package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanCapabilitySnapshot;
import com.temotskipa.vulkanimprovement.client.vulkan.presentation.PresentPacingController;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.FragmentShadingRateController;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

public final class RendererCoreServices {
	private static final RendererCoreServices INSTANCE = new RendererCoreServices();
	
	private volatile RendererLifecycleState lifecycleState = RendererLifecycleState.UNCONFIGURED;
	private volatile String lifecycleReason = "not configured";
	
	private RendererCoreServices() {
	}
	
	public static RendererCoreServices get() {
		return INSTANCE;
	}
	
	public synchronized void beginConfiguration() {
		setLifecycleState(RendererLifecycleState.CONFIGURING, "configuring Vulkan renderer services");
		RendererDomainRegistry.get().reset();
	}
	
	private void setLifecycleState(RendererLifecycleState lifecycleState, String reason) {
		this.lifecycleState = lifecycleState;
		this.lifecycleReason = reason;
	}
	
	public synchronized void configure(VulkanCapabilitySnapshot capabilities) {
		PresentPacingController.get().configure(capabilities);
		FragmentShadingRateController.get().configure(capabilities);
	}
	
	public synchronized void markReady() {
		setLifecycleState(RendererLifecycleState.READY, "renderer services configured");
	}
	
	public synchronized void markFailed(String reason) {
		setLifecycleState(RendererLifecycleState.FAILED, reason);
	}
	
	public synchronized void beginShutdown(String reason) {
		setLifecycleState(RendererLifecycleState.SHUTTING_DOWN, reason);
	}
	
	public synchronized void completeShutdown(String reason) {
		RendererDomainRegistry.get().reset();
		setLifecycleState(RendererLifecycleState.UNCONFIGURED, reason);
	}
	
	public RendererLifecycleState currentLifecycleState() {
		return this.lifecycleState;
	}
	
	public String lifecycleReason() {
		return this.lifecycleReason;
	}
	
	public String describe() {
		if (this.lifecycleState.drawable()) {
			RendererDomainObserver.observeTerrainMode();
		}
		return "lifecycle=" + this.lifecycleState + ", reason=" + this.lifecycleReason + ", mode=" + TerrainRendererDebugConfig.rendererMode();
	}
}