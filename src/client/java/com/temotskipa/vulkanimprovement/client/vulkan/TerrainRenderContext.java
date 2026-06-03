package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TerrainRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> CURRENT_LAYER_ORDINAL = ThreadLocal.withInitial(() -> -1);
    private static final ThreadLocal<boolean[]> DISPATCHED_LAYERS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DISPATCHED_WHOLE_SET = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> REPLACEMENT_ALLOWED = ThreadLocal.withInitial(() -> true);
    private static final ThreadLocal<Boolean> MESH_PIPELINE_BOUND = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<List<ArrayDeque<TerrainMeshTaskDispatch>>> PREPARED_LAYER_DISPATCHES = new ThreadLocal<>();
    private static final ThreadLocal<ChunkSectionsToRender> CURRENT_SECTIONS_TO_RENDER = new ThreadLocal<>();
    private static final ThreadLocal<ChunkSectionLayerGroup> CURRENT_GROUP = new ThreadLocal<>();

    private TerrainRenderContext() {
    }

    public static void enter() {
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        if (depth == 0) {
            CURRENT_LAYER_ORDINAL.set(-1);
            DISPATCHED_LAYERS.set(new boolean[ChunkSectionLayer.values().length]);
            DISPATCHED_WHOLE_SET.set(false);
            REPLACEMENT_ALLOWED.set(true);
            MESH_PIPELINE_BOUND.set(false);
            List<ArrayDeque<TerrainMeshTaskDispatch>> preparedDispatches = new ArrayList<>(ChunkSectionLayer.values().length);
            for (int i = 0; i < ChunkSectionLayer.values().length; i++) {
                preparedDispatches.add(new ArrayDeque<>());
            }
            PREPARED_LAYER_DISPATCHES.set(preparedDispatches);
            CURRENT_SECTIONS_TO_RENDER.remove();
            CURRENT_GROUP.remove();
        }
    }

    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
            CURRENT_LAYER_ORDINAL.remove();
            DISPATCHED_LAYERS.remove();
            DISPATCHED_WHOLE_SET.remove();
            REPLACEMENT_ALLOWED.remove();
            MESH_PIPELINE_BOUND.remove();
            PREPARED_LAYER_DISPATCHES.remove();
            CURRENT_SECTIONS_TO_RENDER.remove();
            CURRENT_GROUP.remove();
        } else {
            DEPTH.set(depth);
        }
    }

    public static boolean isTerrainPass() {
        return DEPTH.get() > 0;
    }

    public static void setLayerForPipeline(RenderPipeline pipeline) {
        MESH_PIPELINE_BOUND.set(false);
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

    public static void setCurrentTerrainGroup(ChunkSectionsToRender sectionsToRender, ChunkSectionLayerGroup group) {
        CURRENT_SECTIONS_TO_RENDER.set(sectionsToRender);
        CURRENT_GROUP.set(group);
    }

    public static @Nullable ChunkSectionsToRender currentSectionsToRender() {
        return CURRENT_SECTIONS_TO_RENDER.get();
    }

    public static @Nullable ChunkSectionLayerGroup currentGroup() {
        return CURRENT_GROUP.get();
    }

    public static void setReplacementAllowed(boolean allowed) {
        REPLACEMENT_ALLOWED.set(allowed);
    }

    public static boolean replacementAllowed() {
        return REPLACEMENT_ALLOWED.get();
    }

    public static void markMeshPipelineBound() {
        MESH_PIPELINE_BOUND.set(true);
    }

    public static boolean meshPipelineBound() {
        return MESH_PIPELINE_BOUND.get();
    }

    public static void enqueuePreparedLayerDispatch(int layerOrdinal, TerrainMeshTaskDispatch dispatch) {
        List<ArrayDeque<TerrainMeshTaskDispatch>> preparedDispatches = PREPARED_LAYER_DISPATCHES.get();
        if (preparedDispatches != null && dispatch != null && layerOrdinal >= 0 && layerOrdinal < preparedDispatches.size()) {
            preparedDispatches.get(layerOrdinal).addLast(dispatch);
        }
    }

    public static @Nullable TerrainMeshTaskDispatch pollPreparedLayerDispatch(int layerOrdinal) {
        List<ArrayDeque<TerrainMeshTaskDispatch>> preparedDispatches = PREPARED_LAYER_DISPATCHES.get();
        if (preparedDispatches == null || layerOrdinal < 0 || layerOrdinal >= preparedDispatches.size()) {
            return null;
        }
        return preparedDispatches.get(layerOrdinal).pollFirst();
    }

    public static void clearMeshPipelineBound() {
        MESH_PIPELINE_BOUND.set(false);
    }

    public static Map<String, Object> preparedDispatchQueueDepths() {
        List<ArrayDeque<TerrainMeshTaskDispatch>> preparedDispatches = PREPARED_LAYER_DISPATCHES.get();
        Map<String, Object> depths = new LinkedHashMap<>();
        if (preparedDispatches == null) {
            return depths;
        }
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        for (int layerOrdinal = 0; layerOrdinal < preparedDispatches.size() && layerOrdinal < layers.length; layerOrdinal++) {
            int depth = preparedDispatches.get(layerOrdinal).size();
            if (depth > 0) {
                depths.put(layers[layerOrdinal].label(), depth);
            }
        }
        return depths;
    }

    public static int unconsumedPreparedDispatchCount() {
        List<ArrayDeque<TerrainMeshTaskDispatch>> preparedDispatches = PREPARED_LAYER_DISPATCHES.get();
        if (preparedDispatches == null) {
            return 0;
        }
        int remaining = 0;
        for (ArrayDeque<TerrainMeshTaskDispatch> queue : preparedDispatches) {
            remaining += queue.size();
        }
        return remaining;
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
