package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.commands.GpuQueryPool;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Measures what terrain costs a frame, for vanilla terrain and the mesh path alike. The time
 * between frames is taken where Minecraft collects a frame's sections, once per frame in a world,
 * together with the time Minecraft's render thread spent on the previous frame without waiting for
 * the GPU: when that is well below the time between frames, the GPU sets the pace.
 * With {@code vim.terrainGpuTimer} on, GPU timestamps bracket the work the mod prepares before
 * Minecraft's render pass and each group of terrain layers Minecraft draws inside it. They are
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
    // Created after the constants above: the arrays an instance allocates are sized by GROUPS.
    private static final TerrainPerformanceMonitor INSTANCE = new TerrainPerformanceMonitor();

    private final long[] frameIntervals = new long[FRAME_SAMPLES];
    private final long[] cpuFrameTimes = new long[FRAME_SAMPLES];
    private final long[] slotWritten = new long[SLOTS];
    private final long[] gpuNanos = new long[1 + GROUPS];
    private final long[] gpuSamples = new long[1 + GROUPS];
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
    
    public synchronized void reset() {
        this.frameCount = 0;
        this.lastFrameNanos = 0L;
        Arrays.fill(this.gpuNanos, 0L);
        Arrays.fill(this.gpuSamples, 0L);
        this.gpuFrames = 0L;
        this.gpuFrameNanos = 0L;
        this.gpuReadMisses = 0L;
    }
    
    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        int samples = Math.min(this.frameCount, FRAME_SAMPLES);
        map.put("frames", samples);
        if (samples > 0) {
            long[] sorted = Arrays.copyOf(this.frameIntervals, samples);
            Arrays.sort(sorted);
            double meanNanos = Arrays.stream(sorted).average().orElse(0.0);
            map.put("meanFrameMs", meanNanos / 1.0e6);
            map.put("p50FrameMs", percentile(sorted, 0.50) / 1.0e6);
            map.put("p95FrameMs", percentile(sorted, 0.95) / 1.0e6);
            map.put("p99FrameMs", percentile(sorted, 0.99) / 1.0e6);
            map.put("maxFrameMs", sorted[samples - 1] / 1.0e6);
            map.put("meanFps", meanNanos > 0.0 ? 1.0e9 / meanNanos : 0.0);
            map.put("meanCpuMs", Arrays.stream(this.cpuFrameTimes, 0, samples).average().orElse(0.0) / 1.0e6);
            map.put("onePercentLowFps", lowFps(sorted));
        }
        Map<String, Object> gpu = new LinkedHashMap<>();
        gpu.put("enabled", TerrainRendererDebugConfig.terrainGpuTimerEnabled());
        gpu.put("available", this.queryPool != null);
        gpu.put("frames", this.gpuFrames);
        gpu.put("readMisses", this.gpuReadMisses);
        gpu.put("prepareMs", meanMs(0));
        for (ChunkSectionLayerGroup group : ChunkSectionLayerGroup.values()) {
            gpu.put(group.name().toLowerCase(Locale.ROOT) + "Ms", meanMs(1 + group.ordinal()));
        }
        gpu.put("terrainMs", this.gpuFrames == 0L ? 0.0 : this.gpuFrameNanos / (double) this.gpuFrames / 1.0e6);
        map.put("gpu", gpu);
        return map;
    }
    
    private double meanMs(int index) {
        return this.gpuSamples[index] == 0L ? 0.0 : this.gpuNanos[index] / (double) this.gpuSamples[index] / 1.0e6;
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
        for (int pair = 0; pair <= GROUPS; pair++) {
            int start = pair * 2;
            if ((written & (3L << start)) != 3L << start) {
                continue;
            }
            if (results[start].isEmpty() || results[start + 1].isEmpty()) {
                this.gpuReadMisses++;
                continue;
            }
            long nanos = Math.round((results[start + 1].getAsLong() - results[start].getAsLong()) * this.nanosPerTick);
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
}
