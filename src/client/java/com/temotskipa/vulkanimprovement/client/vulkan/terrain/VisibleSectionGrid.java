package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.SectionPos;

import java.util.Arrays;

/**
 * The sections Minecraft found in view for a frame, as one bit per section of the box around them,
 * which is all a GPU-driven frame tells the GPU about its terrain. Built on the render thread and
 * reused from frame to frame.
 */
final class VisibleSectionGrid {
    /**
     * A box of more sections than this is left to the listed path. At render distance 32 the box
     * has about a hundred thousand.
     */
    private static final long MAX_CELLS = 1L << 24;
    
    private long[] nodes = new long[8192];
    private long[] words = new long[2048];
    private int wordCount;
    private int minX;
    private int minY;
    private int minZ;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    
    /**
     * Marks the sections in view, in one pass over Minecraft's list, and adds to {@code collected},
     * in Minecraft's order, those it still has to collect because they draw something the GPU
     * does not.
     *
     * @return whether the box was small enough to mark; if not, {@code collected} is to be ignored
     */
    boolean build(ObjectArrayList<RenderSection> sections, ObjectArrayList<RenderSection> collected) {
        int count = sections.size();
        if (count == 0) {
            return false;
        }
        if (this.nodes.length < count) {
            this.nodes = new long[Math.max(count, this.nodes.length * 2)];
        }
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            SectionRenderDispatcher.RenderSection section = sections.get(i);
            long node = section.getSectionNode();
            this.nodes[i] = node;
            int x = SectionPos.x(node);
            int y = SectionPos.y(node);
            int z = SectionPos.z(node);
            lowX = Math.min(lowX, x);
            lowY = Math.min(lowY, y);
            lowZ = Math.min(lowZ, z);
            highX = Math.max(highX, x);
            highY = Math.max(highY, y);
            highZ = Math.max(highZ, z);
            if (SectionMeshletStore.needsMinecraft(section.getSectionMesh())) {
                collected.add(section);
            }
        }
        long cells = (long) (highX - lowX + 1) * (highY - lowY + 1) * (highZ - lowZ + 1);
        if (cells > MAX_CELLS) {
            return false;
        }
        this.minX = lowX;
        this.minY = lowY;
        this.minZ = lowZ;
        this.sizeX = highX - lowX + 1;
        this.sizeY = highY - lowY + 1;
        this.sizeZ = highZ - lowZ + 1;
        this.wordCount = (int) ((cells + Long.SIZE - 1) / Long.SIZE);
        if (this.words.length < this.wordCount) {
            this.words = new long[Math.max(this.wordCount, this.words.length * 2)];
        }
        else {
            Arrays.fill(this.words, 0, this.wordCount, 0L);
        }
        for (int i = 0; i < count; i++) {
            long node = this.nodes[i];
            int bit = ((SectionPos.y(node) - lowY) * this.sizeZ + SectionPos.z(node) - lowZ) * this.sizeX + SectionPos.x(node) - lowX;
            this.words[bit >>> 6] |= 1L << bit;
        }
        return true;
    }
    
    /**
     * The bits, 64 to a word, the first section in the lowest bit of the first word; read as
     * little-endian 32-bit words they are the same bits in the same order.
     */
    long[] words() {
        return this.words;
    }
    
    int wordCount() {
        return this.wordCount;
    }
    
    int minX() {
        return this.minX;
    }
    
    int minY() {
        return this.minY;
    }
    
    int minZ() {
        return this.minZ;
    }
    
    int sizeX() {
        return this.sizeX;
    }
    
    int sizeY() {
        return this.sizeY;
    }
    
    int sizeZ() {
        return this.sizeZ;
    }
}