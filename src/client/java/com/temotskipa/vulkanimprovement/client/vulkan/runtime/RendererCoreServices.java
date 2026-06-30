package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.presentation.PresentPacingController;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.FragmentShadingRateController;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RendererCoreServices {
    private static final RendererCoreServices INSTANCE = new RendererCoreServices();
    private volatile RendererLifecycleState lifecycleState = RendererLifecycleState.UNCONFIGURED;
    private volatile String lifecycleReason = "not configured";
    
    private RendererCoreServices() {
    }
    
    public static RendererCoreServices get() {
        return INSTANCE;
    }
    
    @SuppressWarnings("SameParameterValue")
    public synchronized void beginConfiguration(String reason) {
        setLifecycleState(RendererLifecycleState.CONFIGURING, reason);
        RendererDomainRegistry.get().reset();
    }
    
    public synchronized void configure(VulkanImprovementCapabilities.Snapshot capabilities) {
        PresentPacingController.get().configure(capabilities);
        FragmentShadingRateController.get().configure(capabilities);
    }
    
    @SuppressWarnings("SameParameterValue")
    public synchronized void markReady(String reason) {
        setLifecycleState(RendererLifecycleState.READY, reason);
    }
    
    public synchronized void markFailed(String reason) {
        setLifecycleState(RendererLifecycleState.FAILED, reason);
    }
    
    public synchronized void markDeviceLost(String reason) {
        setLifecycleState(RendererLifecycleState.DEVICE_LOST, reason == null || reason.isBlank() ? "Vulkan device lost" : reason);
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
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configured", this.lifecycleState.drawable());
        map.put("lifecycleState", this.lifecycleState.name());
        map.put("lifecycleReason", this.lifecycleReason);
        map.put("mode", TerrainRendererDebugConfig.rendererMode());
        map.put("domains", RendererDomainRegistry.get().asMap());
        map.put("presentPacing", PresentPacingController.get().asMap());
        map.put("fragmentShadingRate", FragmentShadingRateController.get().asMap());
        return map;
    }
    
    private void setLifecycleState(RendererLifecycleState lifecycleState, String reason) {
        this.lifecycleState = lifecycleState;
        this.lifecycleReason = reason;
    }
}