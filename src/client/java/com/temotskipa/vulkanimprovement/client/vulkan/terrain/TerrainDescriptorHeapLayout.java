package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanCapabilitySnapshot;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.util.LinkedHashMap;
import java.util.Map;

public record TerrainDescriptorHeapLayout(boolean configured, boolean descriptorHeapEnabled, boolean descriptorBufferEnabled, int sectionCapacity, int meshletCapacity, long vertexPayloadBytes, long indexPayloadBytes, long visibleMeshletBytes, long workQueueBytes, long meshTaskCommandBytes, long materialTableBytes, long samplerHeapBytes, long resourceHeapBytes, long samplerDescriptorBufferBytes, long resourceDescriptorBufferBytes, long sectionMetadataBytes, long meshletHeaderBytes, long samplerDescriptorBytes, long imageDescriptorBytes, long bufferDescriptorBytes, long descriptorBufferAlignment, long descriptorBufferSamplerDescriptorBytes, long descriptorBufferSampledImageDescriptorBytes, long descriptorBufferUniformBufferDescriptorBytes, long descriptorBufferStorageBufferDescriptorBytes, Map<String, String> slots) {
    static TerrainDescriptorHeapLayout unconfigured() {
        return new TerrainDescriptorHeapLayout(false, false, false, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, Map.of());
    }
    
    static TerrainDescriptorHeapLayout from(VulkanCapabilitySnapshot capabilities) {
        Map<String, String> slots = new LinkedHashMap<>();
        int sampledImages = 0;
        int uniformBuffers = 0;
        int storageBuffers = 0;
        for (TerrainDescriptorSlot slot : TerrainDescriptorSlot.values()) {
            slots.put(slot.id, slot.descriptorType);
            if ("sampledImage".equals(slot.descriptorType)) {
                sampledImages++;
            }
            else if ("uniformBuffer".equals(slot.descriptorType)) {
                uniformBuffers++;
            }
            else {
                storageBuffers++;
            }
        }
        
        int sectionCapacity = Math.min(TerrainGpuLayout.MAX_PAGES, TerrainRendererDebugConfig.initialSectionCapacity());
        int meshletCapacity = TerrainRendererDebugConfig.initialMeshletCapacity();
        long vertexPayloadBytes = TerrainRendererDebugConfig.initialVertexPayloadBytes();
        long indexPayloadBytes = TerrainRendererDebugConfig.initialIndexPayloadBytes();
        long samplerDescriptorBytes = Math.max(capabilities.samplerDescriptorSize(), 1L);
        long imageDescriptorBytes = Math.max(capabilities.imageDescriptorSize(), 1L);
        long bufferDescriptorBytes = Math.max(capabilities.bufferDescriptorSize(), 1L);
        long alignment = Math.max(capabilities.descriptorBufferOffsetAlignment(), 1L);
        long descriptorBufferSamplerBytes = Math.max(capabilities.descriptorBufferSamplerDescriptorSize(), 1L);
        long descriptorBufferSampledImageBytes = Math.max(capabilities.descriptorBufferSampledImageDescriptorSize(), 1L);
        long descriptorBufferUniformBytes = Math.max(capabilities.descriptorBufferUniformBufferDescriptorSize(), 1L);
        long descriptorBufferStorageBytes = Math.max(capabilities.descriptorBufferStorageBufferDescriptorSize(), 1L);
        long samplerHeapBytes = align(Math.max(samplerDescriptorBytes * sampledImages, 256L), 256L);
        long resourceHeapBytes = align(Math.max(sampledImages * imageDescriptorBytes + (uniformBuffers + storageBuffers) * bufferDescriptorBytes, 1024L), 256L);
        long samplerDescriptorBufferBytes = align(Math.max(descriptorBufferSamplerBytes * sampledImages, 256L), alignment);
        long resourceDescriptorBufferBytes = align(Math.max(sampledImages * (descriptorBufferSampledImageBytes + descriptorBufferSamplerBytes) + uniformBuffers * descriptorBufferUniformBytes + storageBuffers * descriptorBufferStorageBytes, 1024L), alignment);
        long sectionMetadataBytes = (long) sectionCapacity * TerrainGpuLayout.SECTION_METADATA_STRIDE;
        long meshletHeaderBytes = (long) meshletCapacity * TerrainGpuLayout.MESHLET_HEADER_STRIDE;
        long visibleMeshletBytes = (long) meshletCapacity * TerrainGpuLayout.VISIBLE_MESHLET_RING_MULTIPLIER * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE;
        long workQueueBytes = TerrainWorkQueueLayout.bytesForCapacity(meshletCapacity);
        long meshTaskCommandBytes = TerrainMeshTaskCommandLayout.bytesForCapacity();
        long materialTableBytes = (long) TerrainGpuLayout.MATERIAL_TABLE_CAPACITY * TerrainGpuLayout.MATERIAL_RECORD_STRIDE;
        return new TerrainDescriptorHeapLayout(true, capabilities.descriptorHeapExtension(), capabilities.descriptorBufferEnabled(), sectionCapacity, meshletCapacity, vertexPayloadBytes, indexPayloadBytes, visibleMeshletBytes, workQueueBytes, meshTaskCommandBytes, materialTableBytes, samplerHeapBytes, resourceHeapBytes, samplerDescriptorBufferBytes, resourceDescriptorBufferBytes, sectionMetadataBytes, meshletHeaderBytes, samplerDescriptorBytes, imageDescriptorBytes, bufferDescriptorBytes, alignment, descriptorBufferSamplerBytes, descriptorBufferSampledImageBytes, descriptorBufferUniformBytes, descriptorBufferStorageBytes, Map.copyOf(slots));
    }
    
    private static int growIntCapacity(int current, int required) {
        if (required <= current) {
            return current;
        }
        long headroom = required + Math.max(1024L, required / 4L);
        long doubled = Math.max(1L, current) * 2L;
        return (int) Math.min(Integer.MAX_VALUE, nextPowerOfTwo(Math.max(headroom, doubled)));
    }
    
    /**
     * A payload buffer's next size: at most as large as the device lets one buffer be. A page
     * that does not fit even then is turned away.
     */
    private static long growLongCapacity(long current, long required) {
        if (required <= current) {
            return current;
        }
        long headroom = required + Math.max(1L << 20, required / 4L);
        long doubled = Math.max(1L, current) * 2L;
        long grown = Math.min(VulkanImprovementCapabilities.maxBufferBytes(), nextPowerOfTwo(Math.max(headroom, doubled)));
        // Never smaller than now, though the device's limit may be: it is 0 before a device was looked at.
        return Math.max(current, grown);
    }
    
    private static int growMeshletCapacity(int current, int required) {
        int grown = Math.min(maxMeshletCapacity(), growIntCapacity(current, required));
        return Math.max(current, grown);
    }
    
    /**
     * How many meshlets the buffers kept per meshlet may hold, at most: the headers and the
     * visible list, whichever takes more bytes a meshlet, may not outgrow one buffer.
     */
    private static int maxMeshletCapacity() {
        long bytesPerMeshlet = Math.max(TerrainGpuLayout.MESHLET_HEADER_STRIDE, (long) TerrainGpuLayout.VISIBLE_MESHLET_RING_MULTIPLIER * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
        return (int) Math.min(Integer.MAX_VALUE, VulkanImprovementCapabilities.maxBufferBytes() / bytesPerMeshlet);
    }
    
    private static long nextPowerOfTwo(long value) {
        if (value <= 1L) {
            return 1L;
        }
        if (value >= (1L << 62)) {
            return Long.MAX_VALUE;
        }
        return 1L << (Long.SIZE - Long.numberOfLeadingZeros(value - 1L));
    }
    
    private static long align(long value, long alignment) {
        return (value + alignment - 1L) / alignment * alignment;
    }
    
    /**
     * The layout with every buffer that turned a page away made larger. Free space in pieces
     * does not help a page that needs one range, so a buffer that was short grows from its
     * whole capacity, not from what it holds.
     */
    TerrainDescriptorHeapLayout growFor(ResidentPageShortage shortage) {
        int grownSectionCapacity = shortage.pages() > 0 ? Math.min(TerrainGpuLayout.MAX_PAGES, growIntCapacity(this.sectionCapacity, Math.addExact(this.sectionCapacity, shortage.pages()))) : this.sectionCapacity;
        int grownMeshletCapacity = shortage.meshlets() > 0L ? growMeshletCapacity(this.meshletCapacity, Math.toIntExact(this.meshletCapacity + shortage.meshlets())) : this.meshletCapacity;
        long grownVertexPayloadBytes = shortage.vertexBytes() > 0L ? growLongCapacity(this.vertexPayloadBytes, this.vertexPayloadBytes + shortage.vertexBytes()) : this.vertexPayloadBytes;
        long grownIndexPayloadBytes = shortage.indexBytes() > 0L ? growLongCapacity(this.indexPayloadBytes, this.indexPayloadBytes + shortage.indexBytes()) : this.indexPayloadBytes;
        if (grownSectionCapacity == this.sectionCapacity && grownMeshletCapacity == this.meshletCapacity && grownVertexPayloadBytes == this.vertexPayloadBytes && grownIndexPayloadBytes == this.indexPayloadBytes) {
            return this;
        }
        
        return new TerrainDescriptorHeapLayout(this.configured, this.descriptorHeapEnabled, this.descriptorBufferEnabled, grownSectionCapacity, grownMeshletCapacity, grownVertexPayloadBytes, grownIndexPayloadBytes, (long) grownMeshletCapacity * TerrainGpuLayout.VISIBLE_MESHLET_RING_MULTIPLIER * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE, TerrainWorkQueueLayout.bytesForCapacity(grownMeshletCapacity), this.meshTaskCommandBytes, this.materialTableBytes, this.samplerHeapBytes, this.resourceHeapBytes, this.samplerDescriptorBufferBytes, this.resourceDescriptorBufferBytes, (long) grownSectionCapacity * TerrainGpuLayout.SECTION_METADATA_STRIDE, (long) grownMeshletCapacity * TerrainGpuLayout.MESHLET_HEADER_STRIDE, this.samplerDescriptorBytes, this.imageDescriptorBytes, this.bufferDescriptorBytes, this.descriptorBufferAlignment, this.descriptorBufferSamplerDescriptorBytes, this.descriptorBufferSampledImageDescriptorBytes, this.descriptorBufferUniformBufferDescriptorBytes, this.descriptorBufferStorageBufferDescriptorBytes, this.slots);
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configured", this.configured);
        map.put("descriptorHeapEnabled", this.descriptorHeapEnabled);
        map.put("descriptorBufferEnabled", this.descriptorBufferEnabled);
        map.put("sectionCapacity", this.sectionCapacity);
        map.put("meshletCapacity", this.meshletCapacity);
        map.put("vertexPayloadBytes", this.vertexPayloadBytes);
        map.put("indexPayloadBytes", this.indexPayloadBytes);
        map.put("visibleMeshletBytes", this.visibleMeshletBytes);
        map.put("workQueueBytes", this.workQueueBytes);
        map.put("terrainWorkQueueRecordStride", TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE);
        map.put("terrainWorkQueueCounterBytes", TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES);
        map.put("meshTaskCommandBytes", this.meshTaskCommandBytes);
        map.put("terrainMeshTaskCommandStride", TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE);
        map.put("terrainMeshTaskCommandCapacity", TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY);
        map.put("materialTableBytes", this.materialTableBytes);
        map.put("materialRecordStride", TerrainGpuLayout.MATERIAL_RECORD_STRIDE);
        map.put("materialTableCapacity", TerrainGpuLayout.MATERIAL_TABLE_CAPACITY);
        map.put("visibleMeshletRecordStride", TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
        map.put("visibleMeshletRingMultiplier", TerrainGpuLayout.VISIBLE_MESHLET_RING_MULTIPLIER);
        map.put("samplerHeapBytes", this.samplerHeapBytes);
        map.put("resourceHeapBytes", this.resourceHeapBytes);
        map.put("samplerDescriptorBufferBytes", this.samplerDescriptorBufferBytes);
        map.put("resourceDescriptorBufferBytes", this.resourceDescriptorBufferBytes);
        map.put("sectionMetadataBytes", this.sectionMetadataBytes);
        map.put("meshletHeaderBytes", this.meshletHeaderBytes);
        map.put("samplerDescriptorBytes", this.samplerDescriptorBytes);
        map.put("imageDescriptorBytes", this.imageDescriptorBytes);
        map.put("bufferDescriptorBytes", this.bufferDescriptorBytes);
        map.put("descriptorBufferAlignment", this.descriptorBufferAlignment);
        map.put("descriptorBufferSamplerDescriptorBytes", this.descriptorBufferSamplerDescriptorBytes);
        map.put("descriptorBufferSampledImageDescriptorBytes", this.descriptorBufferSampledImageDescriptorBytes);
        map.put("descriptorBufferUniformBufferDescriptorBytes", this.descriptorBufferUniformBufferDescriptorBytes);
        map.put("descriptorBufferStorageBufferDescriptorBytes", this.descriptorBufferStorageBufferDescriptorBytes);
        map.put("slots", this.slots);
        return map;
    }
}