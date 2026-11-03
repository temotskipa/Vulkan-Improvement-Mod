package com.temotskipa.vulkanimprovement.client.vulkan.presentation;

import com.mojang.renderpearl.api.device.GpuSurface.PresentMode;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.util.dx.Win32Handle;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanCapabilitySnapshot;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementSupport;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.concurrent.atomic.AtomicLong;

public final class PresentPacingController {
	private static final PresentPacingController INSTANCE = new PresentPacingController();
	/**
	 * What {@code WaitForSingleObjectEx} returns once the object is signalled.
	 */
	private static final int WAIT_OBJECT_0 = 0;
	private static final int PRESENT_WAIT_TIMEOUT_MILLIS = 50;
	
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
	 * Whether Minecraft presents through its Vulkan swapchain, paced with present id and present
	 * wait, or through DXGI, paced with DXGI's frame latency object; null until Minecraft creates
	 * its surface.
	 */
	private volatile @Nullable Boolean vulkanSwapchain;
	/**
	 * How Minecraft's swapchain presents, null until it is configured. Presents are waited for only
	 * in vsync order: a present that is shown at once reaches the screen of a composed window at
	 * its next refresh, so waiting for one holds the frame rate near the refresh rate.
	 */
	private volatile @Nullable PresentMode presentMode;
	/**
	 * Whether Minecraft's DXGI swapchain was created with a frame latency object, which every
	 * present waits on.
	 */
	private volatile boolean dxgiPaced;
	
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
	
	/**
	 * Whether Minecraft presents through DXGI, which can be paced on any device.
	 */
	public boolean presentsThroughDxgi() {
		return Boolean.FALSE.equals(this.vulkanSwapchain);
	}
	
	/**
	 * Notes the present mode Minecraft configures its swapchain with: FIFO with vsync on.
	 */
	public void swapchainConfigured(PresentMode mode) {
		this.presentMode = mode;
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
		if (this.disabled || !this.presentIdAvailable || !presentsInVsyncOrder()) {
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
			int waitResult = KHRPresentWait.vkWaitForPresentKHR(device.vkDevice(), swapchain, waitId, PRESENT_WAIT_TIMEOUT_MILLIS * 1_000_000L);
			if (waitResult == VK10.VK_SUCCESS) {
				this.presentWaits.incrementAndGet();
			}
			else {
				this.presentWaitFailures.incrementAndGet();
			}
		}
		return result;
	}
	
	/**
	 * Whether the DXGI swapchain Minecraft is about to create gets a frame latency object: on a
	 * device the mod runs on, with pacing on. Pacing is decided once for each swapchain: a swapchain
	 * with the object no longer waits in its present, so every present has to wait on the object,
	 * and lowering its latency once it is in use
	 * leaves it presenting at about five sixths of the refresh rate.
	 */
	public boolean pacesNewDxgiSwapchain() {
		this.disabled = TerrainRendererDebugConfig.disablePresentPacing();
		this.dxgiPaced = !this.disabled && VulkanImprovementSupport.enabled();
		return this.dxgiPaced;
	}
	
	/**
	 * How many frames a paced DXGI swapchain may hold for the screen: the one just presented and
	 * those Minecraft may run ahead by, as many as present wait lets it on the Vulkan swapchain.
	 */
	public int dxgiMaximumFrameLatency() {
		this.frameLatency = TerrainRendererDebugConfig.presentPacingFrameLatency();
		return this.frameLatency + 1;
	}
	
	/**
	 * Waits after a present to a paced DXGI swapchain until it holds fewer frames than its maximum
	 * latency, which it signals on its frame latency object.
	 */
	public void afterDxgiPresent(Win32Handle frameLatencyObject) {
		if (frameLatencyObject.waitSingleObject(PRESENT_WAIT_TIMEOUT_MILLIS, true) == WAIT_OBJECT_0) {
			this.presentWaits.incrementAndGet();
		}
		else {
			this.presentWaitFailures.incrementAndGet();
		}
	}
	
	private boolean presentsInVsyncOrder() {
		PresentMode mode = this.presentMode;
		return mode == PresentMode.FIFO || mode == PresentMode.FIFO_RELAXED;
	}
	
	public String describe() {
		Boolean swapchain = this.vulkanSwapchain;
		PresentMode mode = this.presentMode;
		String surface = swapchain == null ? "none" : swapchain ? "vulkan-swapchain" : "dxgi";
		String pacing = Boolean.FALSE.equals(swapchain) ? this.dxgiPaced ? "dxgi-frame-latency-wait" : "dxgi-unpaced" : this.disabled ? "disabled" : presentsInVsyncOrder() ? "present-id-wait" : "unpaced-without-vsync";
		return "mode=" + pacing + ", presentSurface=" + surface + ", presentMode=" + mode + ", presentIdAvailable=" + this.presentIdAvailable + ", presentWaitAvailable=" + this.presentWaitAvailable + ", frameLatency=" + this.frameLatency + ", presentCalls=" + this.presentCalls.get() + ", presentIdsSubmitted=" + this.presentIdsSubmitted.get() + ", presentWaits=" + this.presentWaits.get() + ", presentWaitFailures=" + this.presentWaitFailures.get() + ", lastPresentDurationMicros=" + this.lastPresentDurationNanos.get() / 1_000L;
	}
}