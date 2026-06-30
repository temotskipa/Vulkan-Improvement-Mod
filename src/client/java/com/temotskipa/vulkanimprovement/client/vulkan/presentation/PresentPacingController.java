package com.temotskipa.vulkanimprovement.client.vulkan.presentation;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import com.mojang.blaze3d.vulkan.VulkanDevice;
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
    
    public static PresentPacingController get() {
        return INSTANCE;
    }

    public void configure(VulkanImprovementCapabilities.Snapshot capabilities) {
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

        if (this.presentWaitAvailable && result >= VK10.VK_SUCCESS) {
            int waitResult = KHRPresentWait.vkWaitForPresentKHR(device.vkDevice(), swapchain, presentId, 50_000_000L);
            if (waitResult == VK10.VK_SUCCESS) {
                this.presentWaits.incrementAndGet();
            } else {
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
        map.put("presentCalls", this.presentCalls.get());
        map.put("presentIdsSubmitted", this.presentIdsSubmitted.get());
        map.put("presentWaits", this.presentWaits.get());
        map.put("presentWaitFailures", this.presentWaitFailures.get());
        map.put("lastPresentDurationMicros", this.lastPresentDurationNanos.get() / 1_000L);
        map.put("mode", this.disabled ? "disabled" : "present-id-wait");
        return map;
    }
}
