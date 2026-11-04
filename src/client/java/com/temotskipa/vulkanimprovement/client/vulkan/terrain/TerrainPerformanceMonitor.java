package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.util.*;

/**
 * Measures what terrain costs a frame, for vanilla terrain and the mesh path alike. The time
 * between frames is taken where Minecraft collects a frame's sections, once per frame in a world,
 * together with the time Minecraft's render thread spent on the previous frame without waiting for
 * the GPU: when that is well below the time between frames, the GPU sets the pace.
 * With {@code vim.terrainGpuTimer} on, GPU timestamps bracket the work the mod prepares before
 * Minecraft's render pass, each group of terrain layers Minecraft draws inside it, and the second
 * phase of occlusion culling ({@link TerrainOcclusion}) after it. They are
 * written the way Minecraft writes its own, through its command encoder into a query pool of
 * Minecraft's, which resets a query from the host as it records the write. A frame's timestamps
 * are read back four frames later, when the GPU has long finished them, so nothing waits for the
 * GPU.
 */
public final class TerrainPerformanceMonitor {
	private static final int FRAME_SAMPLES = 16384;
	private static final int SLOTS = 4;
	private static final int QUERIES_PER_SLOT = 16;
	private static final int PREPARE_START = 0;
	private static final int PREPARE_END = 1;
	private static final int FIRST_GROUP_QUERY = 2;
	private static final int GROUPS = ChunkSectionLayerGroup.values().length;
	// The second phase of occlusion culling follows the groups' queries: its depth pyramid, then
	// its test and draw.
	private static final int PYRAMID_QUERY = FIRST_GROUP_QUERY + GROUPS * 2;
	private static final int OCCLUSION_QUERY = PYRAMID_QUERY + 2;
	// Timed spans: the mod's preparation, each group, the depth pyramid, and the rest of the second phase.
	private static final int SPANS = GROUPS + 3;
	// Created after the constants above: the arrays an instance allocates are sized by SPANS.
	private static final TerrainPerformanceMonitor INSTANCE = new TerrainPerformanceMonitor();

	private final long[] frameIntervals = new long[FRAME_SAMPLES];
	private final long[] cpuFrameTimes = new long[FRAME_SAMPLES];
	private final long[] slotWritten = new long[SLOTS];
	private final long[] gpuNanos = new long[SPANS];
	private final long[] gpuSamples = new long[SPANS];
	private int frameCount;
	private long lastFrameNanos;
	private @Nullable VulkanDevice device;
	private @Nullable GpuQueryPool queryPool;
	private double nanosPerTick = 1.0;
	private int slot;
	private boolean slotOpen;
	private long gpuFrames;
	private long gpuFrameNanos;
	private long gpuReadMisses;
	
	private TerrainPerformanceMonitor() {
	}
	
	public static TerrainPerformanceMonitor get() {
		return INSTANCE;
	}
	
	/**
	 * Whether the device can time GPU work for the monitor.
	 */
	public synchronized boolean gpuTimerAvailable() {
		return this.queryPool != null;
	}
	
	synchronized void configure(VulkanDevice device) {
		shutdown();
		this.device = device;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
			VK10.vkGetPhysicalDeviceProperties(device.vkDevice().getPhysicalDevice(), properties);
			this.nanosPerTick = properties.limits().timestampPeriod();
			if (properties.limits().timestampComputeAndGraphics()) {
				this.queryPool = device.createTimestampQueryPool(SLOTS * QUERIES_PER_SLOT);
			}
		}
	}
	
	synchronized void shutdown() {
		GpuQueryPool pool = this.queryPool;
		if (pool != null) {
			pool.close();
		}
		this.device = null;
		this.queryPool = null;
		this.slotOpen = false;
		Arrays.fill(this.slotWritten, 0L);
	}
	
	/**
	 * Called once per frame, after Minecraft has collected the frame's sections and before the mod
	 * prepares anything for them. No render pass is open.
	 */
	public synchronized void beginFrame() {
		long now = System.nanoTime();
		if (this.lastFrameNanos != 0L) {
			this.frameIntervals[this.frameCount % FRAME_SAMPLES] = now - this.lastFrameNanos;
			this.cpuFrameTimes[this.frameCount % FRAME_SAMPLES] = Minecraft.getInstance().getFrameTimeNs();
			this.frameCount++;
		}
		this.lastFrameNanos = now;
		this.slotOpen = false;
		if (!TerrainRendererDebugConfig.terrainGpuTimerEnabled() || this.queryPool == null || this.device == null) {
			return;
		}
		this.slot = (this.slot + 1) % SLOTS;
		readSlot(this.slot);
		this.slotOpen = true;
		write(PREPARE_START);
	}
	
	/**
	 * Adds what a slot measured four frames ago, then marks it unwritten for the frame about to use it.
	 */
	private void readSlot(int readSlot) {
		long written = this.slotWritten[readSlot];
		this.slotWritten[readSlot] = 0L;
		GpuQueryPool pool = this.queryPool;
		if (written == 0L || pool == null) {
			return;
		}
		OptionalLong[] results = pool.getValues(readSlot * QUERIES_PER_SLOT, QUERIES_PER_SLOT);
		long frameNanos = 0L;
		boolean any = false;
		for (int pair = 0; pair < SPANS; pair++) {
			int start = pair * 2;
			if ((written & (3L << start)) != 3L << start) {
				continue;
			}
			OptionalLong begin = results[start];
			OptionalLong end = results[start + 1];
			if (begin.isEmpty() || end.isEmpty()) {
				this.gpuReadMisses++;
				continue;
			}
			long nanos = Math.round((end.getAsLong() - begin.getAsLong()) * this.nanosPerTick);
			if (nanos < 0L) {
				continue;
			}
			this.gpuNanos[pair] += nanos;
			this.gpuSamples[pair]++;
			frameNanos += nanos;
			any = true;
		}
		if (any) {
			this.gpuFrames++;
			this.gpuFrameNanos += frameNanos;
		}
	}
	
	/**
	 * Writes a timestamp where the encoder is recording, inside Minecraft's render pass or outside it.
	 */
	private void write(int query) {
		long bit = 1L << query;
		VulkanDevice activeDevice = this.device;
		GpuQueryPool pool = this.queryPool;
		if ((this.slotWritten[this.slot] & bit) != 0L || activeDevice == null || pool == null) {
			return;
		}
		this.slotWritten[this.slot] |= bit;
		activeDevice.createCommandEncoder().writeTimestamp(pool, this.slot * QUERIES_PER_SLOT + query);
	}
	
	/**
	 * Called when the mod has recorded what it prepares for the frame, still outside the render pass.
	 */
	public synchronized void endPrepare() {
		if (this.slotOpen) {
			write(PREPARE_END);
		}
	}
	
	/**
	 * Called inside Minecraft's render pass where it starts or finishes drawing a group of terrain
	 * layers.
	 */
	public synchronized void markGroup(ChunkSectionLayerGroup group, boolean end) {
		if (this.slotOpen) {
			write(FIRST_GROUP_QUERY + group.ordinal() * 2 + (end ? 1 : 0));
		}
	}

	/**
	 * Called outside any render pass where the second phase of occlusion culling starts building
	 * its depth pyramid, and where it is done.
	 */
	synchronized void markPyramid(boolean end) {
		if (this.slotOpen) {
			write(PYRAMID_QUERY + (end ? 1 : 0));
		}
	}

	/**
	 * Called outside any render pass where the second phase of occlusion culling starts its test,
	 * and after the render pass it draws in where it ends.
	 */
	synchronized void markOcclusion(boolean end) {
		if (this.slotOpen) {
			write(OCCLUSION_QUERY + (end ? 1 : 0));
		}
	}
	
	public synchronized void reset() {
		this.frameCount = 0;
		this.lastFrameNanos = 0L;
		Arrays.fill(this.gpuNanos, 0L);
		Arrays.fill(this.gpuSamples, 0L);
		this.gpuFrames = 0L;
		this.gpuFrameNanos = 0L;
		this.gpuReadMisses = 0L;
	}
	
	public synchronized String describe() {
		StringBuilder text = new StringBuilder();
		int samples = Math.min(this.frameCount, FRAME_SAMPLES);
		text.append("frames=").append(samples);
		if (samples > 0) {
			long[] sorted = Arrays.copyOf(this.frameIntervals, samples);
			Arrays.sort(sorted);
			double meanNanos = Arrays.stream(sorted).average().orElse(0.0);
			text.append(", meanFrameMs=").append(meanNanos / 1.0e6);
			text.append(", p50FrameMs=").append(percentile(sorted, 0.50) / 1.0e6);
			text.append(", p95FrameMs=").append(percentile(sorted, 0.95) / 1.0e6);
			text.append(", p99FrameMs=").append(percentile(sorted, 0.99) / 1.0e6);
			text.append(", maxFrameMs=").append(sorted[samples - 1] / 1.0e6);
			text.append(", meanFps=").append(meanNanos > 0.0 ? 1.0e9 / meanNanos : 0.0);
			text.append(", meanCpuMs=").append(Arrays.stream(this.cpuFrameTimes, 0, samples).average().orElse(0.0) / 1.0e6);
			text.append(", onePercentLowFps=").append(lowFps(sorted));
		}
		text.append(", gpuTimerEnabled=").append(TerrainRendererDebugConfig.terrainGpuTimerEnabled());
		text.append(", gpuTimerAvailable=").append(this.queryPool != null);
		text.append(", gpuFrames=").append(this.gpuFrames);
		text.append(", gpuReadMisses=").append(this.gpuReadMisses);
		text.append(", gpuPrepareMs=").append(meanMs(0));
		for (ChunkSectionLayerGroup group : ChunkSectionLayerGroup.values()) {
			text.append(", gpu").append(group.name().charAt(0)).append(group.name().substring(1).toLowerCase(Locale.ROOT)).append("Ms=").append(meanMs(1 + group.ordinal()));
		}
		text.append(", gpuPyramidMs=").append(meanMs(1 + GROUPS));
		text.append(", gpuOcclusionMs=").append(meanMs(2 + GROUPS));
		text.append(", gpuTerrainMs=").append(this.gpuFrames == 0L ? 0.0 : this.gpuFrameNanos / (double) this.gpuFrames / 1.0e6);
		return text.toString();
	}
	
	private static long percentile(long[] sorted, double fraction) {
		return sorted[Math.min(sorted.length - 1, (int) Math.floor(fraction * (sorted.length - 1)))];
	}
	
	/**
	 * The frame rate of the slowest hundredth of the frames.
	 */
	private static double lowFps(long[] sorted) {
		int count = Math.max(1, sorted.length / 100);
		double sum = 0.0;
		for (int i = sorted.length - count; i < sorted.length; i++) {
			sum += sorted[i];
		}
		return sum > 0.0 ? 1.0e9 * count / sum : 0.0;
	}
	
	private double meanMs(int index) {
		return this.gpuSamples[index] == 0L ? 0.0 : this.gpuNanos[index] / (double) this.gpuSamples[index] / 1.0e6;
	}
}