package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRFragmentShadingRate;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkExtent2D;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class FragmentShadingRateController {
    private static final FragmentShadingRateController INSTANCE = new FragmentShadingRateController();
    private final AtomicLong terrainCommands = new AtomicLong();
    private volatile boolean available;
    private volatile boolean disabled;
    private volatile int width = 1;
    private volatile int height = 1;
    private volatile int maxWidth = 1;
    private volatile int maxHeight = 1;
    
    public static FragmentShadingRateController get() {
        return INSTANCE;
    }
    
    public boolean fragmentShadingRateAvailable() {
        return this.available;
    }
    
    public void configure(VulkanImprovementCapabilities.Snapshot capabilities) {
        this.available = capabilities.fragmentShadingRateExtension();
        this.disabled = !TerrainRendererDebugConfig.fragmentShadingRateEnabled();
        this.maxWidth = Math.max(capabilities.maxFragmentWidth(), 1);
        this.maxHeight = Math.max(capabilities.maxFragmentHeight(), 1);
        int requestedRate = TerrainRendererDebugConfig.terrainFragmentShadingRate();
        this.width = Math.clamp(requestedRate, 1, this.maxWidth);
        this.height = Math.clamp(requestedRate, 1, this.maxHeight);
    }
    
    public void applyToTerrain(VkCommandBuffer commandBuffer) {
        this.disabled = !TerrainRendererDebugConfig.fragmentShadingRateEnabled();
        if (!this.available || this.disabled || !TerrainRenderContext.isTerrainPass()) {
            return;
        }
        int requestedRate = TerrainRendererDebugConfig.terrainFragmentShadingRate();
        this.width = Math.clamp(requestedRate, 1, this.maxWidth);
        this.height = Math.clamp(requestedRate, 1, this.maxHeight);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D fragmentSize = VkExtent2D.calloc(stack).set(this.width, this.height);
            KHRFragmentShadingRate.vkCmdSetFragmentShadingRateKHR(commandBuffer, fragmentSize, stack.ints(KHRFragmentShadingRate.VK_FRAGMENT_SHADING_RATE_COMBINER_OP_KEEP_KHR, KHRFragmentShadingRate.VK_FRAGMENT_SHADING_RATE_COMBINER_OP_KEEP_KHR));
            this.terrainCommands.incrementAndGet();
        }
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("available", this.available);
        map.put("disabled", this.disabled);
        map.put("width", this.width);
        map.put("height", this.height);
        map.put("maxWidth", this.maxWidth);
        map.put("maxHeight", this.maxHeight);
        map.put("terrainCommands", this.terrainCommands.get());
        return map;
    }
}