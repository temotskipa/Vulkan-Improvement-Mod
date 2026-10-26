package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

/**
 * @param slot     the page's index in the GPU page table
 * @param revision grows with every page handed out, so a page can be told from the one that
 *                 held the same slot before it
 */
record ResidentTerrainPage(int slot, long sectionNode, int layerOrdinal, long vertexOffset, int vertexBytes, long indexOffset, int indexBytes, int firstMeshlet, int meshletCount, boolean customIndexBuffer, long revision) {
}