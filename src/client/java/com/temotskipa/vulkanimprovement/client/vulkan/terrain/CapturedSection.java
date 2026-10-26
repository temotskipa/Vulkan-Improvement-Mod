package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.util.EnumMap;

final class CapturedSection {
    final long sectionNode;
    final EnumMap<ChunkSectionLayer, SectionLayerCapture> layers = new EnumMap<>(ChunkSectionLayer.class);
    private final MeshletPageBinding[] bindings = new MeshletPageBinding[ChunkSectionLayer.values().length];
    // The layers the mesh draws, one bit per layer, whether or not they were captured.
    volatile int drawLayers;
    
    CapturedSection(long sectionNode) {
        this.sectionNode = sectionNode;
        for (int layer = 0; layer < this.bindings.length; layer++) {
            this.bindings[layer] = new MeshletPageBinding();
        }
    }
    
    MeshletPageBinding binding(int layerOrdinal) {
        return this.bindings[layerOrdinal];
    }
    
    void clearBindings() {
        for (MeshletPageBinding binding : this.bindings) {
            binding.page = null;
        }
    }
    
    /**
     * Tells the mesh whether the GPU draws this section whole: each layer the mesh draws has a
     * page the GPU-driven draw can draw. Pages are handed out on the render thread and
     * captures given back on others, so the answer is worked out and kept under one lock.
     */
    synchronized void updateFrameRole(SectionMeshletCaptureHolder holder) {
        holder.vim$setFrameRole(gpuDrawsWhole() ? SectionMeshletStore.ROLE_GPU : SectionMeshletStore.ROLE_MINECRAFT);
    }
    
    private boolean gpuDrawsWhole() {
        int layers = this.drawLayers;
        for (ChunkSectionLayer layer : SectionMeshletStore.LAYERS) {
            if ((layers & 1 << layer.ordinal()) != 0 && !(TerrainLayerReplacementPolicy.canReplaceLayer(layer) && SectionMeshletStore.drawable(binding(layer.ordinal()).page))) {
                return false;
            }
        }
        return layers != 0;
    }
}