package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class TerrainRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<boolean[]> DISPATCHED_LAYERS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DISPATCHED_WHOLE_SET = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> REPLACEMENT_ALLOWED = ThreadLocal.withInitial(() -> true);
    private static final ThreadLocal<Boolean> IMPROVED_FOG = ThreadLocal.withInitial(() -> false);
    /**
     * The mesh-task dispatches prepared for the frame, per layer. They are prepared before
     * Minecraft opens a render pass and drawn inside it, possibly over several layer groups, so
     * they belong to the frame and not to one group. Render thread only.
     */
    private static final List<ArrayDeque<TerrainMeshTaskDispatch>> PREPARED_LAYER_DISPATCHES = createPreparedLayerDispatches();
    
    private TerrainRenderContext() {
    }
    
    public static void enter() {
        int depth = DEPTH.get();
        DEPTH.set(depth + 1);
        if (depth == 0) {
            DISPATCHED_LAYERS.set(new boolean[ChunkSectionLayer.values().length]);
            DISPATCHED_WHOLE_SET.set(false);
            REPLACEMENT_ALLOWED.set(true);
            IMPROVED_FOG.set(false);
        }
    }
    
    public static void exit() {
        int depth = DEPTH.get() - 1;
        if (depth <= 0) {
            DEPTH.remove();
            DISPATCHED_LAYERS.remove();
            DISPATCHED_WHOLE_SET.remove();
            REPLACEMENT_ALLOWED.remove();
            IMPROVED_FOG.remove();
        }
        else {
            DEPTH.set(depth);
        }
    }
    
    public static boolean isTerrainPass() {
        return DEPTH.get() > 0;
    }
    
    public static void setReplacementAllowed(boolean allowed) {
        REPLACEMENT_ALLOWED.set(allowed);
    }
    
    public static boolean replacementAllowed() {
        return REPLACEMENT_ALLOWED.get();
    }
    
    /**
     * @param improvedFog whether Minecraft draws the current group with the pipelines that fade
     *                    terrain into the sky and cloud images
     */
    public static void setImprovedFog(boolean improvedFog) {
        IMPROVED_FOG.set(improvedFog);
    }
    
    public static boolean improvedFog() {
        return IMPROVED_FOG.get();
    }
    
    private static List<ArrayDeque<TerrainMeshTaskDispatch>> createPreparedLayerDispatches() {
        List<ArrayDeque<TerrainMeshTaskDispatch>> preparedDispatches = new ArrayList<>(ChunkSectionLayer.values().length);
        for (int i = 0; i < ChunkSectionLayer.values().length; i++) {
            preparedDispatches.add(new ArrayDeque<>());
        }
        return List.copyOf(preparedDispatches);
    }
    
    /**
     * Discards what the last frame prepared and did not draw, and says how many dispatches that was.
     */
    static int beginPreparedFrame() {
        int unconsumed = unconsumedPreparedDispatchCount();
        PREPARED_LAYER_DISPATCHES.forEach(ArrayDeque::clear);
        return unconsumed;
    }
    
    static void enqueuePreparedLayerDispatch(int layerOrdinal, TerrainMeshTaskDispatch dispatch) {
        if (dispatch != null && layerOrdinal >= 0 && layerOrdinal < PREPARED_LAYER_DISPATCHES.size()) {
            PREPARED_LAYER_DISPATCHES.get(layerOrdinal).addLast(dispatch);
        }
    }
    
    static @Nullable TerrainMeshTaskDispatch pollPreparedLayerDispatch(int layerOrdinal) {
        if (layerOrdinal < 0 || layerOrdinal >= PREPARED_LAYER_DISPATCHES.size()) {
            return null;
        }
        return PREPARED_LAYER_DISPATCHES.get(layerOrdinal).pollFirst();
    }
    
    public static Map<String, Object> preparedDispatchQueueDepths() {
        List<ArrayDeque<TerrainMeshTaskDispatch>> preparedDispatches = PREPARED_LAYER_DISPATCHES;
        Map<String, Object> depths = new LinkedHashMap<>();
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
        int remaining = 0;
        for (ArrayDeque<TerrainMeshTaskDispatch> queue : PREPARED_LAYER_DISPATCHES) {
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