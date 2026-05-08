package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public final class TerrainRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_LAYER_ORDINAL = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<boolean[]> DISPATCHED_LAYERS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DISPATCHED_WHOLE_SET = ThreadLocal.withInitial(() -> false);
    
    private TerrainRenderContext() {
    }
    
    public static void enter() {
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        if (depth == 0) {
            CURRENT_LAYER_ORDINAL.set(-1);
            DISPATCHED_LAYERS.set(new boolean[ChunkSectionLayer.values().length]);
            DISPATCHED_WHOLE_SET.set(false);
        }
    }
    
    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
            CURRENT_LAYER_ORDINAL.remove();
            DISPATCHED_LAYERS.remove();
            DISPATCHED_WHOLE_SET.remove();
        } else {
            DEPTH.set(depth);
        }
    }
    
    public static boolean isTerrainPass() {
        return DEPTH.get() > 0;
    }

    public static void setLayerForPipeline(RenderPipeline pipeline) {
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            if (layer.pipeline() == pipeline) {
                CURRENT_LAYER_ORDINAL.set(layer.ordinal());
                return;
            }
        }
        CURRENT_LAYER_ORDINAL.set(-1);
    }

    public static int currentLayerOrdinal() {
        return CURRENT_LAYER_ORDINAL.get();
    }

    public static boolean markDispatchRequired(int layerOrdinal) {
        if (layerOrdinal < 0) {
            if (DISPATCHED_WHOLE_SET.get()) {
                return false;
            }
            DISPATCHED_WHOLE_SET.set(true);
            return true;
        }

        boolean[] dispatchedLayers = DISPATCHED_LAYERS.get();
        if (dispatchedLayers == null || layerOrdinal >= dispatchedLayers.length) {
            return true;
        }
        if (dispatchedLayers[layerOrdinal]) {
            return false;
        }
        dispatchedLayers[layerOrdinal] = true;
        return true;
    }
}
