package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.jspecify.annotations.Nullable;

/**
 * The page a section layer has on the GPU now, or {@code null}. Set when the page is handed out
 * and cleared when it is given back, so a frame reads it without looking the section layer up.
 */
final class MeshletPageBinding {
    volatile @Nullable ResidentTerrainPage page;
}