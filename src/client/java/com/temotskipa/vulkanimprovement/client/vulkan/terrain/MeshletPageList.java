package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.Arrays;

/**
 * A layer's pages as a frame draws them, in Minecraft's order: each page's slot in the GPU page
 * table, how many tasks draw it, and how far its section has faded in. Refilled every frame.
 */
public final class MeshletPageList {
    private int[] slots = new int[256];
    private int[] tasks = new int[256];
    private float[] visibility = new float[256];
    private int size;
    
    public int size() {
        return this.size;
    }
    
    public int slot(int index) {
        return this.slots[index];
    }
    
    public int tasks(int index) {
        return this.tasks[index];
    }
    
    public float visibility(int index) {
        return this.visibility[index];
    }
    
    void clear() {
        this.size = 0;
    }
    
    void add(int slot, int meshletCount, float sectionVisibility) {
        if (this.size == this.slots.length) {
            int capacity = this.size * 2;
            this.slots = Arrays.copyOf(this.slots, capacity);
            this.tasks = Arrays.copyOf(this.tasks, capacity);
            this.visibility = Arrays.copyOf(this.visibility, capacity);
        }
        this.slots[this.size] = slot;
        this.tasks[this.size] = SectionMeshletStore.tasks(meshletCount);
        this.visibility[this.size] = sectionVisibility;
        this.size++;
    }
}