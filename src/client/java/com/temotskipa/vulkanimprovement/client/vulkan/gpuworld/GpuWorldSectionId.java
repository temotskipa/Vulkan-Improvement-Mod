package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import net.minecraft.core.SectionPos;

public record GpuWorldSectionId(long sectionNode, int sectionX, int sectionY, int sectionZ) {
	public GpuWorldSectionId {
		long encoded = SectionPos.asLong(sectionX, sectionY, sectionZ);
		if (sectionNode != encoded) {
			throw new IllegalArgumentException("Section node does not match coordinates: node=" + sectionNode + ", encoded=" + encoded);
		}
	}
	
	public static GpuWorldSectionId fromBlockPosition(int blockX, int blockY, int blockZ) {
		return fromSectionNode(SectionPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockY), SectionPos.blockToSectionCoord(blockZ)));
	}
	
	public static GpuWorldSectionId fromSectionNode(long sectionNode) {
		return new GpuWorldSectionId(sectionNode, SectionPos.x(sectionNode), SectionPos.y(sectionNode), SectionPos.z(sectionNode));
	}
}