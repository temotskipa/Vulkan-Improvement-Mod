package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * A layer's section layers of the frame being collected. Each keeps its page binding, and the
 * page it had when collected with that page's slot and size, read while the page was at hand:
 * the frame's list is built from these unless the binding has changed since.
 */
final class SectionLayerEntries {
    MeshletPageBinding[] bindings = new MeshletPageBinding[256];
    ResidentTerrainPage[] pages = new ResidentTerrainPage[256];
    int[] slots = new int[256];
    int[] meshletCounts = new int[256];
    private long[] sectionNodes = new long[256];
    float[] visibility = new float[256];
    int size;
    
    void add(MeshletPageBinding binding, @Nullable ResidentTerrainPage page, long sectionNode, float sectionVisibility) {
        if (this.size == this.bindings.length) {
            int capacity = this.size * 2;
            this.bindings = Arrays.copyOf(this.bindings, capacity);
            this.pages = Arrays.copyOf(this.pages, capacity);
            this.slots = Arrays.copyOf(this.slots, capacity);
            this.meshletCounts = Arrays.copyOf(this.meshletCounts, capacity);
            this.sectionNodes = Arrays.copyOf(this.sectionNodes, capacity);
            this.visibility = Arrays.copyOf(this.visibility, capacity);
        }
        boolean drawable = SectionMeshletStore.drawable(page);
        this.bindings[this.size] = binding;
        this.pages[this.size] = drawable ? page : null;
        this.slots[this.size] = drawable ? page.slot() : 0;
        this.meshletCounts[this.size] = drawable ? page.meshletCount() : 0;
        this.sectionNodes[this.size] = sectionNode;
        this.visibility[this.size] = sectionVisibility;
        this.size++;
    }
    
    void setLastVisibility(long sectionNode, float sectionVisibility) {
        if (this.size > 0 && this.sectionNodes[this.size - 1] == sectionNode) {
            this.visibility[this.size - 1] = sectionVisibility;
        }
    }
    
    void clear() {
        Arrays.fill(this.bindings, 0, this.size, null);
        Arrays.fill(this.pages, 0, this.size, null);
        this.size = 0;
    }
}