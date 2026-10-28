package com.temotskipa.vulkanimprovement.client.vulkan.presentation;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanCapabilitySnapshot;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class PresentPacingController {
    private static final PresentPacingController INSTANCE = new PresentPacingController();
    
    private final AtomicLong presentCalls = new AtomicLong();
    private final AtomicLong presentIdsSubmitted = new AtomicLong();
    private final AtomicLong presentWaits = new AtomicLong();
    private final AtomicLong presentWaitFailures = new AtomicLong();
    private final AtomicLong nextPresentId = new AtomicLong(1L);
    private final AtomicLong lastPresentStartedNanos = new AtomicLong();
    private final AtomicLong lastPresentDurationNanos = new AtomicLong();
    private volatile boolean presentIdAvailable;
    private volatile boolean presentWaitAvailable;
    private volatile boolean disabled;
    private volatile int frameLatency = TerrainRendererDebugConfig.presentPacingFrameLatency();
    /**
     * The swapchain the last paced present went to, and the first present id it was given; a
     * present id below that one was never presented to it, so it is not waited for.
     */
    private long pacedSwapchain;
    private long firstPresentIdOfSwapchain;
    /**
     * Whether Minecraft presents through the Vulkan swapchain, which pacing acts on, or through
     * DXGI, where it has nothing to act on; null until Minecraft creates its surface.
     */
    private volatile @Nullable Boolean vulkanSwapchain;
    
    public static PresentPacingController get() {
        return INSTANCE;
    }
    
    /**
     * Notes the surface Minecraft created for its window: its Vulkan swapchain, or, with the
     * experimental Prefer DXGI Present option on, a DXGI swapchain that D3D12 presents.
     */
    public void surfaceCreated(boolean vulkanSwapchain) {
        this.vulkanSwapchain = vulkanSwapchain;
    }
    
    public void configure(VulkanCapabilitySnapshot capabilities) {
        this.presentIdAvailable = capabilities.presentIdExtension();
        this.presentWaitAvailable = capabilities.presentWaitExtension();
        this.disabled = TerrainRendererDebugConfig.disablePresentPacing();
    }
    
    public void beforePresent() {
        this.presentCalls.incrementAndGet();
        this.lastPresentStartedNanos.set(System.nanoTime());
    }
    
    public void afterPresent() {
        long started = this.lastPresentStartedNanos.get();
        if (started != 0L) {
            this.lastPresentDurationNanos.set(System.nanoTime() - started);
        }
    }
    
    public int present(VulkanDevice device, VkQueue queue, long swapchain, VkPresentInfoKHR presentInfo) {
        this.disabled = TerrainRendererDebugConfig.disablePresentPacing();
        if (this.disabled || !this.presentIdAvailable) {
            return KHRSwapchain.vkQueuePresentKHR(queue, presentInfo);
        }
        
        int result;
        long presentId = this.nextPresentId.getAndIncrement();
        if (swapchain != this.pacedSwapchain) {
            this.pacedSwapchain = swapchain;
            this.firstPresentIdOfSwapchain = presentId;
        }
        long oldPNext = presentInfo.pNext();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPresentIdKHR presentIdInfo = VkPresentIdKHR.calloc(stack).sType$Default();
            presentIdInfo.pNext(oldPNext);
            presentIdInfo.swapchainCount(presentInfo.swapchainCount());
            presentIdInfo.pPresentIds(stack.longs(presentId));
            presentInfo.pNext(presentIdInfo.address());
            result = KHRSwapchain.vkQueuePresentKHR(queue, presentInfo);
            presentInfo.pNext(oldPNext);
        }
        this.presentIdsSubmitted.incrementAndGet();
        
        // Waiting for the frame just presented would leave the GPU idle while Minecraft builds the
        // next one; waiting for an earlier one keeps that many frames between Minecraft and the screen.
        this.frameLatency = TerrainRendererDebugConfig.presentPacingFrameLatency();
        long waitId = presentId - this.frameLatency;
        if (this.presentWaitAvailable && result >= VK10.VK_SUCCESS && waitId >= this.firstPresentIdOfSwapchain) {
            int waitResult = KHRPresentWait.vkWaitForPresentKHR(device.vkDevice(), swapchain, waitId, 50_000_000L);
            if (waitResult == VK10.VK_SUCCESS) {
                this.presentWaits.incrementAndGet();
            }
            else {
                this.presentWaitFailures.incrementAndGet();
            }
        }
        return result;
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("disabled", this.disabled);
        map.put("presentIdAvailable", this.presentIdAvailable);
        map.put("presentWaitAvailable", this.presentWaitAvailable);
        map.put("frameLatency", this.frameLatency);
        map.put("presentCalls", this.presentCalls.get());
        map.put("presentIdsSubmitted", this.presentIdsSubmitted.get());
        map.put("presentWaits", this.presentWaits.get());
        map.put("presentWaitFailures", this.presentWaitFailures.get());
        map.put("lastPresentDurationMicros", this.lastPresentDurationNanos.get() / 1_000L);
        Boolean swapchain = this.vulkanSwapchain;
        map.put("presentSurface", swapchain == null ? "none" : swapchain ? "vulkan-swapchain" : "dxgi");
        map.put("mode", this.disabled ? "disabled" : Boolean.FALSE.equals(swapchain) ? "inactive" : "present-id-wait");
        return map;
    }
}