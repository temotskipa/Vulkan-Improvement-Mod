package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Where each section layer's terrain lives on the GPU. A section layer gets a page when it is
 * captured: a slot in the page table and a range of the vertex payload, the index payload and the
 * meshlet headers. The page stays where it is until the section layer is captured again or
 * released, so a change to one section touches one page and nothing else.
 *
 * <p>A range that is given back can be handed out again at once. Page data only reaches the GPU
 * through copies recorded in the command stream, which run after every draw recorded before them.
 *
 * @param <K> what identifies a section layer
 */
final class ResidentTerrainPages<K> {
    private final Map<K, Page> pages = new HashMap<>();
    private final Set<K> pending = new LinkedHashSet<>();
    private final TerrainRangeAllocator slots = new TerrainRangeAllocator(0L);
    private final TerrainRangeAllocator meshlets = new TerrainRangeAllocator(0L);
    private final TerrainRangeAllocator vertexBytes = new TerrainRangeAllocator(0L);
    private final TerrainRangeAllocator indexBytes = new TerrainRangeAllocator(0L);
    private long pagesAllocated;
    private long pagesFreed;
    private long allocationRefusals;
    private long revision;
    private Shortage shortage = Shortage.NONE;
    
    /**
     * Drops every page and sizes the allocators for a new set of GPU buffers. Section layers that
     * had a page have to be marked pending again by their owner.
     */
    synchronized void reset(int pageCapacity, int meshletCapacity, long vertexPayloadBytes, long indexPayloadBytes) {
        this.pages.clear();
        this.pending.clear();
        this.slots.reset(pageCapacity);
        this.meshlets.reset(meshletCapacity);
        this.vertexBytes.reset(vertexPayloadBytes);
        this.indexBytes.reset(indexPayloadBytes);
        this.shortage = Shortage.NONE;
    }
    
    /**
     * Says that a section layer has new data waiting to be given a page and uploaded.
     */
    synchronized void markPending(K key) {
        this.pending.add(key);
    }
    
    /**
     * Gives back a section layer's page, if it has one, and forgets any upload waiting for it.
     */
    synchronized void release(K key) {
        this.pending.remove(key);
        free(key);
    }
    
    synchronized @Nullable Page page(K key) {
        return this.pages.get(key);
    }
    
    synchronized List<K> pendingKeys() {
        return new ArrayList<>(this.pending);
    }
    
    synchronized Map<K, Page> snapshot() {
        return new HashMap<>(this.pages);
    }
    
    /**
     * Gives a pending section layer a page, replacing the one it had.
     *
     * @return the page, or {@code null} when a buffer has no room for it; the shortage is then
     * remembered so the buffers can be grown, and the section layer stays pending
     */
    synchronized @Nullable Page allocate(K key, long sectionNode, int layerOrdinal, int pageVertexBytes, int pageIndexBytes, int meshletCount, boolean customIndexBuffer) {
        free(key);
        long slot = this.slots.allocate(1L);
        long firstMeshlet = slot == TerrainRangeAllocator.NO_SPACE ? TerrainRangeAllocator.NO_SPACE : this.meshlets.allocate(meshletCount);
        long vertexOffset = firstMeshlet == TerrainRangeAllocator.NO_SPACE ? TerrainRangeAllocator.NO_SPACE : this.vertexBytes.allocate(pageVertexBytes);
        long indexOffset = vertexOffset == TerrainRangeAllocator.NO_SPACE ? TerrainRangeAllocator.NO_SPACE : this.indexBytes.allocate(pageIndexBytes);
        if (indexOffset == TerrainRangeAllocator.NO_SPACE) {
            if (vertexOffset != TerrainRangeAllocator.NO_SPACE) {
                this.vertexBytes.free(vertexOffset, pageVertexBytes);
            }
            if (firstMeshlet != TerrainRangeAllocator.NO_SPACE) {
                this.meshlets.free(firstMeshlet, meshletCount);
            }
            if (slot != TerrainRangeAllocator.NO_SPACE) {
                this.slots.free(slot, 1L);
            }
            this.allocationRefusals++;
            // Only the buffer that had no room is short; the others are not grown for it.
            if (slot == TerrainRangeAllocator.NO_SPACE) {
                this.shortage = this.shortage.plus(1, 0L, 0L, 0L);
            }
            else if (firstMeshlet == TerrainRangeAllocator.NO_SPACE) {
                this.shortage = this.shortage.plus(0, meshletCount, 0L, 0L);
            }
            else if (vertexOffset == TerrainRangeAllocator.NO_SPACE) {
                this.shortage = this.shortage.plus(0, 0L, pageVertexBytes, 0L);
            }
            else {
                this.shortage = this.shortage.plus(0, 0L, 0L, pageIndexBytes);
            }
            return null;
        }
        Page page = new Page((int) slot, sectionNode, layerOrdinal, vertexOffset, pageVertexBytes, indexOffset, pageIndexBytes, (int) firstMeshlet, meshletCount, customIndexBuffer, ++this.revision);
        this.pages.put(key, page);
        this.pending.remove(key);
        this.pagesAllocated++;
        return page;
    }
    
    /**
     * What did not fit since this was last asked, so the buffers can be grown by that much.
     */
    synchronized Shortage takeShortage() {
        Shortage taken = this.shortage;
        this.shortage = Shortage.NONE;
        return taken;
    }
    
    synchronized Stats stats() {
        int[] meshletsByLayer = new int[16];
        int[] customIndexMeshletsByLayer = new int[16];
        int totalMeshlets = 0;
        for (Page page : this.pages.values()) {
            totalMeshlets += page.meshletCount();
            if (page.layerOrdinal() >= 0 && page.layerOrdinal() < meshletsByLayer.length) {
                meshletsByLayer[page.layerOrdinal()] += page.meshletCount();
                if (page.customIndexBuffer()) {
                    customIndexMeshletsByLayer[page.layerOrdinal()] += page.meshletCount();
                }
            }
        }
        return new Stats(this.pages.size(), this.pending.size(), totalMeshlets, meshletsByLayer, customIndexMeshletsByLayer, this.pagesAllocated, this.pagesFreed, this.allocationRefusals, this.slots.used(), this.slots.capacity(), this.meshlets.used(), this.meshlets.capacity(), this.meshlets.freeRangeCount(), this.vertexBytes.used(), this.vertexBytes.capacity(), this.vertexBytes.largestFreeRange(), this.vertexBytes.freeRangeCount(), this.indexBytes.used(), this.indexBytes.capacity(), this.indexBytes.largestFreeRange(), this.indexBytes.freeRangeCount());
    }
    
    private void free(K key) {
        Page page = this.pages.remove(key);
        if (page == null) {
            return;
        }
        this.slots.free(page.slot(), 1L);
        this.meshlets.free(page.firstMeshlet(), page.meshletCount());
        this.vertexBytes.free(page.vertexOffset(), page.vertexBytes());
        this.indexBytes.free(page.indexOffset(), page.indexBytes());
        this.pagesFreed++;
    }
    
    /**
     * @param slot     the page's index in the GPU page table
     * @param revision grows with every page handed out, so a page can be told from the one that
     *                 held the same slot before it
     */
    record Page(int slot, long sectionNode, int layerOrdinal, long vertexOffset, int vertexBytes, long indexOffset, int indexBytes, int firstMeshlet, int meshletCount, boolean customIndexBuffer, long revision) {
    }
    
    record Shortage(int pages, long meshlets, long vertexBytes, long indexBytes) {
        static final Shortage NONE = new Shortage(0, 0L, 0L, 0L);

        boolean any() {
            return this.pages > 0 || this.meshlets > 0L || this.vertexBytes > 0L || this.indexBytes > 0L;
        }

        private Shortage plus(int morePages, long moreMeshlets, long moreVertexBytes, long moreIndexBytes) {
            return new Shortage(this.pages + morePages, this.meshlets + moreMeshlets, this.vertexBytes + moreVertexBytes, this.indexBytes + moreIndexBytes);
        }
    }
    
    record Stats(int livePages, int pendingPages, int meshlets, int[] meshletsByLayer, int[] customIndexMeshletsByLayer, long pagesAllocated, long pagesFreed, long allocationRefusals, long slotsUsed, long slotCapacity, long meshletsUsed, long meshletCapacity, int meshletFreeRanges, long vertexBytesUsed, long vertexByteCapacity, long largestFreeVertexRange, int vertexFreeRanges, long indexBytesUsed, long indexByteCapacity, long largestFreeIndexRange, int indexFreeRanges) {
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("livePages", this.livePages);
            map.put("pendingPages", this.pendingPages);
            map.put("meshlets", this.meshlets);
            map.put("pagesAllocated", this.pagesAllocated);
            map.put("pagesFreed", this.pagesFreed);
            map.put("allocationRefusals", this.allocationRefusals);
            map.put("pageSlots", Map.of("used", this.slotsUsed, "capacity", this.slotCapacity));
            map.put("meshletHeaders", Map.of("used", this.meshletsUsed, "capacity", this.meshletCapacity, "freeRanges", this.meshletFreeRanges));
            map.put("vertexPayload", Map.of("usedBytes", this.vertexBytesUsed, "capacityBytes", this.vertexByteCapacity, "largestFreeRangeBytes", this.largestFreeVertexRange, "freeRanges", this.vertexFreeRanges));
            map.put("indexPayload", Map.of("usedBytes", this.indexBytesUsed, "capacityBytes", this.indexByteCapacity, "largestFreeRangeBytes", this.largestFreeIndexRange, "freeRanges", this.indexFreeRanges));
            return map;
        }
    }
}