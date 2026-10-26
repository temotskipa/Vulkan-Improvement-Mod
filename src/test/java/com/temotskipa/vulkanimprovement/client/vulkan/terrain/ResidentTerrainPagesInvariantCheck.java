package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Checks the bookkeeping behind resident terrain pages: the range allocator and the page table.
 * Neither touches the GPU, so both are exercised directly.
 */
public final class ResidentTerrainPagesInvariantCheck {
    private ResidentTerrainPagesInvariantCheck() {
    }
    
    static void main() {
        checkRangesAreReusedAndMerged();
        checkAllocatorRejectsWhatItDidNotHandOut();
        checkRandomAllocationsNeverOverlap();
        checkPageKeepsItsPlaceUntilItsSectionLayerChanges();
        checkOnlyTheShortBufferIsReported();
        checkResetDropsEveryPage();
    }
    
    private static void checkRangesAreReusedAndMerged() {
        TerrainRangeAllocator allocator = new TerrainRangeAllocator(100L);
        long first = allocator.allocate(40L);
        long second = allocator.allocate(40L);
        require(first == 0L && second == 40L, "ranges must be handed out from the start of the buffer");
        require(allocator.allocate(30L) == TerrainRangeAllocator.NO_SPACE, "a range longer than any free range must be refused");
        require(allocator.used() == 80L, "a refused range must not count as used");
        require(allocator.allocate(0L) == 0L && allocator.used() == 80L, "an empty range must occupy nothing");
        
        allocator.free(first, 40L);
        require(allocator.freeRangeCount() == 2 && allocator.largestFreeRange() == 40L, "a freed range must be free on its own until a neighbour is freed too");
        require(allocator.allocate(30L) == 0L, "the first free range that is long enough must be used");
        allocator.free(0L, 30L);
        allocator.free(second, 40L);
        require(allocator.freeRangeCount() == 1 && allocator.largestFreeRange() == 100L && allocator.used() == 0L, "free ranges that touch must merge into one");
    }
    
    private static void checkAllocatorRejectsWhatItDidNotHandOut() {
        TerrainRangeAllocator allocator = new TerrainRangeAllocator(64L);
        long offset = allocator.allocate(16L);
        allocator.free(offset, 16L);
        requireThrows(() -> allocator.free(offset, 16L), "a range must not be freed twice");
        requireThrows(() -> allocator.free(60L, 16L), "a range outside the buffer must be rejected");
        requireThrows(() -> allocator.allocate(-1L), "a negative length must be rejected");
    }
    
    /**
     * Whatever is allocated and freed, in whatever order: no two live ranges share a unit.
     */
    private static void checkRandomAllocationsNeverOverlap() {
        int capacity = 4096;
        TerrainRangeAllocator allocator = new TerrainRangeAllocator(capacity);
        boolean[] occupied = new boolean[capacity];
        List<long[]> live = new ArrayList<>();
        Random random = new Random(26_4L);
        for (int step = 0; step < 20_000; step++) {
            if (live.isEmpty() || random.nextInt(3) != 0) {
                int length = 1 + random.nextInt(96);
                long offset = allocator.allocate(length);
                if (offset == TerrainRangeAllocator.NO_SPACE) {
                    require(allocator.largestFreeRange() < length, "a range must only be refused when no free range is long enough");
                    continue;
                }
                for (int unit = (int) offset; unit < offset + length; unit++) {
                    require(!occupied[unit], "a unit was handed out twice");
                    occupied[unit] = true;
                }
                live.add(new long[]{offset, length});
            }
            else {
                long[] range = live.remove(random.nextInt(live.size()));
                allocator.free(range[0], range[1]);
                for (int unit = (int) range[0]; unit < range[0] + range[1]; unit++) {
                    occupied[unit] = false;
                }
            }
        }
        long used = 0L;
        for (long[] range : live) {
            used += range[1];
        }
        require(allocator.used() == used, "the allocator must count exactly what is live");
    }
    
    private static void checkPageKeepsItsPlaceUntilItsSectionLayerChanges() {
        ResidentTerrainPages<String> pages = new ResidentTerrainPages<>();
        pages.reset(8, 64, 1024L, 256L);
        pages.markPending("a");
        pages.markPending("b");
        ResidentTerrainPage a = pages.allocate("a", 1L, 0, 280, 0, 3, false);
        ResidentTerrainPage b = pages.allocate("b", 2L, 1, 560, 24, 5, true);
        require(a != null && b != null, "pages that fit must be handed out");
        require(a.slot() != b.slot() && a.firstMeshlet() + a.meshletCount() <= b.firstMeshlet() && a.vertexOffset() + a.vertexBytes() <= b.vertexOffset(), "two pages must not share a slot, a meshlet or a byte");
        require(pages.pendingKeys().isEmpty(), "a section layer that got its page must stop waiting");
        require(pages.page("a") == a && pages.page("b") == b, "a page must stay where it is while another is added");
        
        ResidentTerrainPage changed = pages.allocate("a", 1L, 0, 140, 0, 2, false);
        require(changed != null && changed.revision() > a.revision(), "a section layer that is captured again must get a new page revision");
        require(pages.page("b") == b, "a change to one section layer must not move another's page");
        ResidentPageStats stats = pages.stats();
        require(stats.livePages() == 2 && stats.meshlets() == 7 && stats.pagesFreed() == 1L, "replacing a page must give the old one back");
        require(stats.vertexBytesUsed() == 700L && stats.meshletsByLayer()[0] == 2 && stats.customIndexMeshletsByLayer()[1] == 5, "the page statistics must count what is live, by layer");
        
        pages.release("b");
        require(pages.page("b") == null && pages.stats().livePages() == 1, "a released section layer must lose its page");
        ResidentTerrainPage reused = pages.allocate("c", 3L, 1, 560, 24, 5, true);
        // What the first page gave back when it shrank and what the second page held are one free range now.
        require(reused != null && reused.vertexOffset() == changed.vertexOffset() + changed.vertexBytes(), "space that was given back must be handed out again, from the start of the merged free range");
    }
    
    private static void checkOnlyTheShortBufferIsReported() {
        ResidentTerrainPages<String> pages = new ResidentTerrainPages<>();
        pages.reset(4, 4, 1000L, 100L);
        pages.markPending("large");
        require(pages.allocate("large", 1L, 0, 2000, 10, 2, false) == null, "a page that does not fit must be refused");
        ResidentPageShortage shortage = pages.takeShortage();
        require(shortage.any() && shortage.vertexBytes() == 2000L && shortage.pages() == 0 && shortage.meshlets() == 0L && shortage.indexBytes() == 0L, "only the buffer that had no room must be reported as short");
        require(!pages.takeShortage().any(), "a shortage must be reported once");
        require(pages.pendingKeys().contains("large") && pages.stats().livePages() == 0, "a refused section layer must keep waiting and hold nothing");
        ResidentPageStats stats = pages.stats();
        require(stats.slotsUsed() == 0L && stats.meshletsUsed() == 0L && stats.indexBytesUsed() == 0L && stats.allocationRefusals() == 1L, "a refused page must give back what it had been given");
    }
    
    private static void checkResetDropsEveryPage() {
        ResidentTerrainPages<String> pages = new ResidentTerrainPages<>();
        pages.reset(4, 16, 1024L, 64L);
        pages.markPending("a");
        require(pages.allocate("a", 1L, 0, 280, 0, 3, false) != null, "the page must fit");
        pages.reset(8, 32, 2048L, 128L);
        ResidentPageStats stats = pages.stats();
        require(stats.livePages() == 0 && pages.page("a") == null && stats.slotCapacity() == 8L && stats.vertexByteCapacity() == 2048L && stats.vertexBytesUsed() == 0L, "new buffers must start with no page and the new capacity");
    }
    
    private static void requireThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}