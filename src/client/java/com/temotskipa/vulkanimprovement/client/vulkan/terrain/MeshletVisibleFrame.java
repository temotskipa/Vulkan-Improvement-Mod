package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The section layers Minecraft collected for the frame being prepared, per layer and in its
 * draw order, each with how far its section has faded in. Minecraft makes a section's draw
 * data right after it collects the first of the section's layers, so that entry learns its
 * fade-in after it is added and the section's later layers when they are. Written and read on
 * the render thread only, without a lock: it is touched for every section layer of a frame.
 * Diagnostics read the counts it publishes when a frame's collection ends.
 */
final class MeshletVisibleFrame {
    private final SectionLayerEntries[] layers = new SectionLayerEntries[ChunkSectionLayer.values().length];
    private final MeshletPageList[] pageLists = new MeshletPageList[ChunkSectionLayer.values().length];
    private final int[] uncaptured = new int[ChunkSectionLayer.values().length];
    boolean open;
    volatile boolean takeover;
    boolean gpuDriven;
    volatile Map<String, Object> published = Map.of("entries", 0, "uncaptured", 0, "belowOne", 0, "min", 1.0F, "gpuDriven", false);
    private long currentSection = Long.MIN_VALUE;
    private float currentVisibility = 1.0F;
    private int lastLayer = -1;
    
    MeshletVisibleFrame() {
        for (int layer = 0; layer < this.layers.length; layer++) {
            this.layers[layer] = new SectionLayerEntries();
            this.pageLists[layer] = new MeshletPageList();
        }
    }
    
    void begin(boolean takeoverFrame, boolean gpuDrivenFrame) {
        clear();
        this.takeover = takeoverFrame;
        this.gpuDriven = takeoverFrame && gpuDrivenFrame;
        this.open = true;
    }
    
    boolean takeover() {
        return this.takeover;
    }
    
    boolean gpuDriven() {
        return this.gpuDriven;
    }
    
    boolean collectingTakeover() {
        return this.open && this.takeover;
    }
    
    boolean take(int layerOrdinal, MeshletPageBinding binding, ResidentTerrainPage page, long sectionNode, float visibility) {
        if (!this.open || !this.takeover) {
            return false;
        }
        this.layers[layerOrdinal].add(binding, page, sectionNode, visibility);
        return true;
    }
    
    void end() {
        this.open = false;
        this.published = Map.of("entries", sectionLayerCount(), "uncaptured", uncapturedCount(), "belowOne", belowOne(), "min", minVisibility(), "gpuDriven", this.gpuDriven);
    }
    
    Map<String, Object> published() {
        return this.published;
    }
    
    private int belowOne() {
        int count = 0;
        for (SectionLayerEntries layer : this.layers) {
            for (int i = 0; i < layer.size; i++) {
                if (layer.visibility[i] < 0.999F) {
                    count++;
                }
            }
        }
        return count;
    }
    
    private float minVisibility() {
        float min = 1.0F;
        for (SectionLayerEntries layer : this.layers) {
            for (int i = 0; i < layer.size; i++) {
                min = Math.min(min, layer.visibility[i]);
            }
        }
        return min;
    }
    
    void clear() {
        for (SectionLayerEntries layer : this.layers) {
            layer.clear();
        }
        Arrays.fill(this.uncaptured, 0);
        this.currentSection = Long.MIN_VALUE;
        this.currentVisibility = 1.0F;
        this.lastLayer = -1;
    }
    
    void addUncaptured(int layerOrdinal) {
        if (this.open) {
            this.uncaptured[layerOrdinal]++;
        }
    }
    
    void add(int layerOrdinal, MeshletPageBinding binding, long sectionNode) {
        if (!this.open) {
            return;
        }
        this.layers[layerOrdinal].add(binding, binding.page, sectionNode, sectionNode == this.currentSection ? this.currentVisibility : 1.0F);
        this.lastLayer = layerOrdinal;
    }
    
    void sectionVisibility(long sectionNode, float visibility) {
        if (!this.open || this.takeover) {
            return;
        }
        this.currentSection = sectionNode;
        this.currentVisibility = visibility;
        if (this.lastLayer >= 0) {
            this.layers[this.lastLayer].setLastVisibility(sectionNode, visibility);
        }
    }
    
    @Nullable MeshletPageList pages(int layerOrdinal) {
        if (layerOrdinal < 0 || layerOrdinal >= this.layers.length || !this.takeover && this.uncaptured[layerOrdinal] > 0) {
            return null;
        }
        SectionLayerEntries entries = this.layers[layerOrdinal];
        MeshletPageList pages = this.pageLists[layerOrdinal];
        pages.clear();
        for (int i = 0; i < entries.size; i++) {
            ResidentTerrainPage page = entries.bindings[i].page;
            if (page != null && page == entries.pages[i]) {
                // The page the section layer had when it was collected, as noted then.
                pages.add(entries.slots[i], entries.meshletCounts[i], entries.visibility[i]);
                continue;
            }
            if (!SectionMeshletStore.drawable(page)) {
                // A taken section layer whose page went away this frame is left out: Minecraft
                // no longer draws it either, which is a hole for one frame, not a stale page.
                if (this.takeover) {
                    continue;
                }
                return null;
            }
            pages.add(page.slot(), page.meshletCount(), entries.visibility[i]);
        }
        return pages;
    }
    
    @Nullable List<MeshletRange> meshletRanges(int layerOrdinal) {
        if (layerOrdinal < 0 || layerOrdinal >= this.layers.length || !this.takeover && this.uncaptured[layerOrdinal] > 0) {
            return null;
        }
        SectionLayerEntries entries = this.layers[layerOrdinal];
        List<MeshletRange> ranges = new ArrayList<>(entries.size);
        for (int i = 0; i < entries.size; i++) {
            ResidentTerrainPage page = entries.bindings[i].page;
            if (page == null || page.meshletCount() <= 0) {
                if (this.takeover) {
                    continue;
                }
                return null;
            }
            ranges.add(new MeshletRange(page.sectionNode(), page.firstMeshlet(), page.meshletCount(), entries.visibility[i]));
        }
        return ranges;
    }
    
    int sectionLayerCount() {
        int count = 0;
        for (SectionLayerEntries layer : this.layers) {
            count += layer.size;
        }
        return count;
    }
    
    private int uncapturedCount() {
        return Arrays.stream(this.uncaptured).sum();
    }
}