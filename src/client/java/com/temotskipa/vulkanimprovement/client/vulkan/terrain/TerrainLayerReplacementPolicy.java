package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.util.ArrayList;
import java.util.List;

final class TerrainLayerReplacementPolicy {
    private TerrainLayerReplacementPolicy() {
    }

    static boolean canReplaceLayer(int layerOrdinal) {
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        return layerOrdinal >= 0
                && layerOrdinal < layers.length
                && !layers[layerOrdinal].translucent();
    }

    static boolean canReplaceLayer(ChunkSectionLayer layer) {
        return layer != null && canReplaceLayer(layer.ordinal());
    }

    /**
     * Splits a layer's visible ranges into runs that one mesh-task dispatch can take, in order and
     * without dividing a section layer.
     */
    static List<List<SectionMeshletStore.MeshletRange>> dispatchBatches(List<SectionMeshletStore.MeshletRange> ranges) {
        List<List<SectionMeshletStore.MeshletRange>> batches = new ArrayList<>();
        List<SectionMeshletStore.MeshletRange> batch = new ArrayList<>();
        int batchMeshlets = 0;
        for (SectionMeshletStore.MeshletRange range : ranges) {
            if (!batch.isEmpty() && batchMeshlets + range.count() > TerrainMeshTaskDispatch.MAX_DIRECT_TASKS) {
                batches.add(batch);
                batch = new ArrayList<>();
                batchMeshlets = 0;
            }
            batch.add(range);
            batchMeshlets += range.count();
        }
        if (!batch.isEmpty()) {
            batches.add(batch);
        }
        return batches;
    }

    static int dispatchCountForMeshlets(int meshletCount) {
        if (meshletCount <= 0) {
            return 0;
        }
        return (meshletCount + TerrainMeshTaskDispatch.MAX_DIRECT_TASKS - 1) / TerrainMeshTaskDispatch.MAX_DIRECT_TASKS;
    }

    static int segmentMeshletOffset(int baseMeshletOffset, int segmentIndex) {
        if (baseMeshletOffset < 0) {
            throw new IllegalArgumentException("baseMeshletOffset must be non-negative");
        }
        if (segmentIndex < 0) {
            throw new IllegalArgumentException("segmentIndex must be non-negative");
        }
        return baseMeshletOffset + segmentIndex * TerrainMeshTaskDispatch.MAX_DIRECT_TASKS;
    }

    static int segmentMeshletCount(int meshletCount, int segmentIndex) {
        if (meshletCount < 0) {
            throw new IllegalArgumentException("meshletCount must be non-negative");
        }
        if (segmentIndex < 0) {
            throw new IllegalArgumentException("segmentIndex must be non-negative");
        }
        int segmentOffset = segmentIndex * TerrainMeshTaskDispatch.MAX_DIRECT_TASKS;
        if (segmentOffset >= meshletCount) {
            return 0;
        }
        return Math.min(TerrainMeshTaskDispatch.MAX_DIRECT_TASKS, meshletCount - segmentOffset);
    }
}
