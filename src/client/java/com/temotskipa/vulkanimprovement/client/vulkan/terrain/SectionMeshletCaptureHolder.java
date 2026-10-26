package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.jspecify.annotations.Nullable;

/**
 * Implemented on Minecraft's compiled section mesh by a mixin: the section the mod captured from
 * the mesh, so a frame finds it without looking the mesh up.
 */
public interface SectionMeshletCaptureHolder {
    @Nullable Object vim$capturedSection();
    
    void vim$setCapturedSection(@Nullable Object section);
    
    byte vim$frameRole();
    
    void vim$setFrameRole(byte role);
    
    /**
     * When the section this mesh is shown in was first shown, by Minecraft's clock; 0 while
     * Minecraft does not show the mesh.
     */
    long vim$shownSince();
    
    void vim$setShownSince(long millis);
}