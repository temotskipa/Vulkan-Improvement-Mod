package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Native memory the CPU writes records into, which doubles when it runs out and keeps what was
 * written. Used from the render thread only.
 */
final class GrowingSegment implements AutoCloseable {
    private Arena arena = Arena.ofShared();
    private MemorySegment segment;
    
    GrowingSegment(long initialBytes) {
        this.segment = this.arena.allocate(initialBytes, 16L);
    }
    
    /**
     * The memory, at least {@code bytes} long, holding what was written to its first {@code used}
     * bytes.
     */
    MemorySegment reserve(long used, long bytes) {
        if (bytes > this.segment.byteSize()) {
            Arena grownArena = Arena.ofShared();
            MemorySegment grown = grownArena.allocate(Math.max(bytes, this.segment.byteSize() * 2L), 16L);
            MemorySegment.copy(this.segment, 0L, grown, 0L, used);
            this.arena.close();
            this.arena = grownArena;
            this.segment = grown;
        }
        return this.segment;
    }
    
    MemorySegment segment() {
        return this.segment;
    }
    
    @Override
    public void close() {
        this.arena.close();
    }
}
