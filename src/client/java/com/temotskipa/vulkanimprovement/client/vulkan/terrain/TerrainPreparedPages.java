package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

/**
 * One layer's part of a frame's visibility passes: the dispatch that will draw what the pass
 * keeps, and the list of pages the pass looks at.
 */
public record TerrainPreparedPages(TerrainMeshTaskDispatch dispatch, TerrainVisiblePageUpload pages) {
}