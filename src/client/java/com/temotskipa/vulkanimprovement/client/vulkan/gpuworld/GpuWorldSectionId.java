package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import net.minecraft.core.SectionPos;

import java.util.LinkedHashMap;
import java.util.Map;

public record GpuWorldSectionId(long sectionNode, int sectionX, int sectionY, int sectionZ) {
    public GpuWorldSectionId {
        long encoded = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (sectionNode != encoded) {
            throw new IllegalArgumentException("Section node does not match coordinates: node=" + sectionNode + ", encoded=" + encoded);
        }
    }

    public static GpuWorldSectionId fromSectionNode(long sectionNode) {
        return new GpuWorldSectionId(sectionNode, SectionPos.x(sectionNode), SectionPos.y(sectionNode), SectionPos.z(sectionNode));
    }

    public static GpuWorldSectionId fromBlockPosition(int blockX, int blockY, int blockZ) {
        return fromSectionNode(SectionPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockY), SectionPos.blockToSectionCoord(blockZ)));
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sectionNode", this.sectionNode);
        map.put("sectionX", this.sectionX);
        map.put("sectionY", this.sectionY);
        map.put("sectionZ", this.sectionZ);
        return map;
    }
}
