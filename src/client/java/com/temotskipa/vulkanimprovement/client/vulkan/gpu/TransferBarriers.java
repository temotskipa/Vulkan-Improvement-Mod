package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Barriers by resource. Minecraft's Vulkan command encoder follows each of its transfers (a clear,
 * an upload or a copy) with a barrier that makes every later command wait for every earlier one.
 * Here the barrier after a transfer is held back until a later command needs it: a transfer that
 * reads what a held one wrote or writes what a held one read or wrote, any command other than a
 * transfer, or the end of the command buffer. Transfers of different buffer ranges and textures
 * then run with no barrier between them, and one barrier follows them all.
 * <p>
 * Buffers are tracked by byte range, textures as a whole. Used on the render thread only, as
 * Minecraft's encoder is.
 */
public final class TransferBarriers {
    private static volatile @Nullable TransferBarriers latest;
    
    private final Map<Long, NavigableMap<Long, Long>> bufferReads = new HashMap<>();
    private final Map<Long, NavigableMap<Long, Long>> bufferWrites = new HashMap<>();
    private final Set<Long> imageReads = new HashSet<>();
    private final Set<Long> imageWrites = new HashSet<>();
    private boolean held;
    private long transfers;
    private long hazardBarriers;
    private long releasedBarriers;
    
    public TransferBarriers() {
        latest = this;
    }
    
    /**
     * The tracker of the encoder created last, for diagnostics.
     */
    public static Optional<TransferBarriers> latest() {
        return Optional.ofNullable(latest);
    }
    
    /**
     * Takes a transfer about to be recorded, whose own barrier is held back. Returns whether a
     * transfer held since the last barrier must finish first, in which case the caller records a
     * barrier before this one.
     */
    public boolean transfer(Access... accesses) {
        transfers++;
        boolean hazard = false;
        for (Access access : accesses) {
            hazard |= conflicts(access);
        }
        if (hazard) {
            clear();
            hazardBarriers++;
        }
        for (Access access : accesses) {
            add(access);
        }
        held = true;
        return hazard;
    }
    
    /**
     * Before a command other than a transfer, or the end of the command buffer: returns whether a
     * barrier is held back, in which case the caller records it now.
     */
    public boolean release() {
        if (!held) {
            return false;
        }
        clear();
        releasedBarriers++;
        return true;
    }
    
    /**
     * Notes a barrier Minecraft recorded on its own, after which nothing is held back.
     */
    public void barrierRecorded() {
        clear();
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("transfers", transfers);
        map.put("hazardBarriers", hazardBarriers);
        map.put("releasedBarriers", releasedBarriers);
        map.put("barriersSaved", transfers - hazardBarriers - releasedBarriers);
        return map;
    }
    
    private boolean conflicts(Access access) {
        if (access.image()) {
            return imageWrites.contains(access.handle()) || access.write() && imageReads.contains(access.handle());
        }
        return overlaps(bufferWrites, access) || access.write() && overlaps(bufferReads, access);
    }
    
    private void add(Access access) {
        if (access.image()) {
            (access.write() ? imageWrites : imageReads).add(access.handle());
        }
        else if (access.end() > access.start()) {
            merge((access.write() ? bufferWrites : bufferReads).computeIfAbsent(access.handle(), _ -> new TreeMap<>()), access.start(), access.end());
        }
    }
    
    private void clear() {
        bufferReads.clear();
        bufferWrites.clear();
        imageReads.clear();
        imageWrites.clear();
        held = false;
    }
    
    /**
     * Whether the access overlaps one of its buffer's ranges. The ranges are disjoint and keyed by
     * their start, so only the last one starting before the access ends can overlap it.
     */
    private static boolean overlaps(Map<Long, NavigableMap<Long, Long>> ranges, Access access) {
        NavigableMap<Long, Long> buffer = ranges.get(access.handle());
        if (buffer == null || access.end() <= access.start()) {
            return false;
        }
        Map.Entry<Long, Long> last = buffer.lowerEntry(access.end());
        return last != null && last.getValue() > access.start();
    }
    
    /**
     * Adds a range to a buffer's ranges, joined with every range it overlaps or touches, so that
     * they stay disjoint.
     */
    private static void merge(NavigableMap<Long, Long> ranges, long start, long end) {
        long from = start;
        long to = end;
        Map.Entry<Long, Long> before = ranges.floorEntry(from);
        if (before != null && before.getValue() >= from) {
            from = before.getKey();
        }
        for (Map.Entry<Long, Long> next = ranges.ceilingEntry(from); next != null && next.getKey() <= to; next = ranges.ceilingEntry(from)) {
            to = Math.max(to, next.getValue());
            ranges.remove(next.getKey());
        }
        ranges.put(from, to);
    }
    
    /**
     * What a transfer reads or writes: a byte range of a buffer, from {@code start} to before
     * {@code end}, or a whole texture.
     */
    public record Access(long handle, long start, long end, boolean image, boolean write) {
        public static Access readBuffer(long vkBuffer, long start, long end) {
            return new Access(vkBuffer, start, end, false, false);
        }
        
        public static Access writeBuffer(long vkBuffer, long start, long end) {
            return new Access(vkBuffer, start, end, false, true);
        }
        
        public static Access readImage(long vkImage) {
            return new Access(vkImage, 0L, 0L, true, false);
        }
        
        public static Access writeImage(long vkImage) {
            return new Access(vkImage, 0L, 0L, true, true);
        }
    }
}