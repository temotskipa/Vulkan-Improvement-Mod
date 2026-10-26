package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
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
 * The GPU reads every slot of the page table up to the highest one handed out, so a slot that is
 * given back has its record cleared there in the same copies, and after a reset the whole table.
 *
 * @param <K> what identifies a section layer
 */
final class ResidentTerrainPages<K> {
    private final Map<K, ResidentTerrainPage> pages = new HashMap<>();
    private final Set<K> pending = new LinkedHashSet<>();
    private final TerrainRangeAllocator slots = new TerrainRangeAllocator(0L);
    private final TerrainRangeAllocator meshlets = new TerrainRangeAllocator(0L);
    private final TerrainRangeAllocator vertexBytes = new TerrainRangeAllocator(0L);
    private final TerrainRangeAllocator indexBytes = new TerrainRangeAllocator(0L);
    private final BitSet freedSlots = new BitSet();
    private final int[] tasksByLayer = new int[16];
    private long pagesAllocated;
    private long pagesFreed;
    private long allocationRefusals;
    private long revision;
    private ResidentPageShortage shortage = ResidentPageShortage.NONE;
    private boolean clearTable = true;
    private int slotHighWater;
    
    /**
     * How many tasks the GPU-driven draw launches for a page: one per
     * {@link TerrainGpuLayout#MESHLETS_PER_TASK} of its meshlets, none for a page it cannot draw.
     */
    private static int gpuTasks(int meshletCount) {
        return meshletCount > 0 && meshletCount <= TerrainGpuLayout.MAX_PAGE_MESHLETS ? (meshletCount + TerrainGpuLayout.MESHLETS_PER_TASK - 1) / TerrainGpuLayout.MESHLETS_PER_TASK : 0;
    }
    
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
        this.shortage = ResidentPageShortage.NONE;
        this.freedSlots.clear();
        this.clearTable = true;
        this.slotHighWater = 0;
        Arrays.fill(this.tasksByLayer, 0);
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
    
    synchronized @Nullable ResidentTerrainPage page(K key) {
        return this.pages.get(key);
    }
    
    synchronized List<K> pendingKeys() {
        return new ArrayList<>(this.pending);
    }
    
    synchronized Map<K, ResidentTerrainPage> snapshot() {
        return new HashMap<>(this.pages);
    }
    
    /**
     * Gives a pending section layer a page, replacing the one it had.
     *
     * @return the page, or {@code null} when a buffer has no room for it; the shortage is then
     * remembered so the buffers can be grown, and the section layer stays pending
     */
    synchronized @Nullable ResidentTerrainPage allocate(K key, long sectionNode, int layerOrdinal, int pageVertexBytes, int pageIndexBytes, int meshletCount, boolean customIndexBuffer) {
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
        ResidentTerrainPage page = new ResidentTerrainPage((int) slot, sectionNode, layerOrdinal, vertexOffset, pageVertexBytes, indexOffset, pageIndexBytes, (int) firstMeshlet, meshletCount, customIndexBuffer, ++this.revision);
        this.pages.put(key, page);
        this.pending.remove(key);
        this.pagesAllocated++;
        // The new record takes the slot's place in the copies, so the slot is not cleared.
        this.freedSlots.clear((int) slot);
        this.slotHighWater = Math.max(this.slotHighWater, (int) slot + 1);
        this.tasksByLayer[layerOrdinal] += gpuTasks(meshletCount);
        return page;
    }
    
    /**
     * What did not fit since this was last asked, so the buffers can be grown by that much.
     */
    synchronized ResidentPageShortage takeShortage() {
        ResidentPageShortage taken = this.shortage;
        this.shortage = ResidentPageShortage.NONE;
        return taken;
    }
    
    synchronized ResidentPageStats stats() {
        int[] meshletsByLayer = new int[16];
        int[] customIndexMeshletsByLayer = new int[16];
        int totalMeshlets = 0;
        for (ResidentTerrainPage page : this.pages.values()) {
            totalMeshlets += page.meshletCount();
            if (page.layerOrdinal() >= 0 && page.layerOrdinal() < meshletsByLayer.length) {
                meshletsByLayer[page.layerOrdinal()] += page.meshletCount();
                if (page.customIndexBuffer()) {
                    customIndexMeshletsByLayer[page.layerOrdinal()] += page.meshletCount();
                }
            }
        }
        return new ResidentPageStats(this.pages.size(), this.pending.size(), totalMeshlets, meshletsByLayer, customIndexMeshletsByLayer, this.pagesAllocated, this.pagesFreed, this.allocationRefusals, this.slots.used(), this.slots.capacity(), this.meshlets.used(), this.meshlets.capacity(), this.meshlets.freeRangeCount(), this.vertexBytes.used(), this.vertexBytes.capacity(), this.vertexBytes.largestFreeRange(), this.vertexBytes.freeRangeCount(), this.indexBytes.used(), this.indexBytes.capacity(), this.indexBytes.largestFreeRange(), this.indexBytes.freeRangeCount());
    }
    
    private void free(K key) {
        ResidentTerrainPage page = this.pages.remove(key);
        if (page == null) {
            return;
        }
        this.slots.free(page.slot(), 1L);
        this.meshlets.free(page.firstMeshlet(), page.meshletCount());
        this.vertexBytes.free(page.vertexOffset(), page.vertexBytes());
        this.indexBytes.free(page.indexOffset(), page.indexBytes());
        this.pagesFreed++;
        this.freedSlots.set(page.slot());
        this.tasksByLayer[page.layerOrdinal()] -= gpuTasks(page.meshletCount());
    }
    
    /**
     * What the GPU page table has to be told before it is read again: whether every record is to
     * be cleared, and the slots given back since it was last told. Taken after the frame's pages
     * were handed out, so a slot handed out again is not among them.
     */
    synchronized ResidentPageTableChanges takeTableChanges() {
        ResidentPageTableChanges changes = new ResidentPageTableChanges(this.clearTable, this.freedSlots.stream().toArray());
        this.clearTable = false;
        this.freedSlots.clear();
        return changes;
    }
    
    /**
     * One more than the highest slot handed out since the last reset: the GPU reads the page
     * table up to here.
     */
    synchronized int slotHighWater() {
        return this.slotHighWater;
    }
    
    /**
     * How many tasks drawing every page of a layer would launch, at most.
     */
    synchronized int tasks(int layerOrdinal) {
        return this.tasksByLayer[layerOrdinal];
    }
}