package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.Map;
import java.util.TreeMap;

/**
 * Hands out ranges of a GPU buffer and takes them back, in the buffer's own unit: bytes of vertex
 * payload, meshlet header slots, page slots. The first free range that is long enough is used,
 * and free ranges that touch are merged.
 */
final class TerrainRangeAllocator {
    static final long NO_SPACE = -1L;
    private final TreeMap<Long, Long> freeLengthByOffset = new TreeMap<>();
    private long capacity;
    private long used;
    
    TerrainRangeAllocator(long capacity) {
        reset(capacity);
    }
    
    /**
     * Forgets every range and starts again with the given capacity.
     */
    void reset(long capacity) {
        if (capacity < 0L) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
        this.freeLengthByOffset.clear();
        this.capacity = capacity;
        this.used = 0L;
        if (capacity > 0L) {
            this.freeLengthByOffset.put(0L, capacity);
        }
    }
    
    /**
     * @return where a range of the given length starts, or {@link #NO_SPACE} when no free range
     * is long enough. A range of length zero occupies nothing and starts at 0.
     */
    long allocate(long length) {
        if (length < 0L) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        if (length == 0L) {
            return 0L;
        }
        for (Map.Entry<Long, Long> range : this.freeLengthByOffset.entrySet()) {
            long freeLength = range.getValue();
            if (freeLength >= length) {
                long offset = range.getKey();
                this.freeLengthByOffset.remove(offset);
                if (freeLength > length) {
                    this.freeLengthByOffset.put(offset + length, freeLength - length);
                }
                this.used += length;
                return offset;
            }
        }
        return NO_SPACE;
    }
    
    void free(long offset, long length) {
        if (length == 0L) {
            return;
        }
        if (offset < 0L || length < 0L || offset + length > this.capacity) {
            throw new IllegalArgumentException("range is outside the buffer: offset=" + offset + ", length=" + length + ", capacity=" + this.capacity);
        }
        Map.Entry<Long, Long> before = this.freeLengthByOffset.floorEntry(offset);
        Map.Entry<Long, Long> after = this.freeLengthByOffset.ceilingEntry(offset);
        if (before != null && before.getKey() + before.getValue() > offset || after != null && after.getKey() < offset + length) {
            throw new IllegalStateException("range is already free: offset=" + offset + ", length=" + length);
        }
        long start = offset;
        long end = offset + length;
        if (before != null && before.getKey() + before.getValue() == offset) {
            start = before.getKey();
            this.freeLengthByOffset.remove(start);
        }
        if (after != null && after.getKey() == end) {
            end += after.getValue();
            this.freeLengthByOffset.remove(after.getKey());
        }
        this.freeLengthByOffset.put(start, end - start);
        this.used -= length;
    }
    
    long capacity() {
        return this.capacity;
    }
    
    long used() {
        return this.used;
    }
    
    int freeRangeCount() {
        return this.freeLengthByOffset.size();
    }
    
    long largestFreeRange() {
        long largest = 0L;
        for (long length : this.freeLengthByOffset.values()) {
            largest = Math.max(largest, length);
        }
        return largest;
    }
}