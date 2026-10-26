package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import net.minecraft.client.renderer.chunk.CompiledSectionMesh;

record SectionLayerKey(CompiledSectionMesh mesh, long sectionNode, int layerOrdinal) {
}