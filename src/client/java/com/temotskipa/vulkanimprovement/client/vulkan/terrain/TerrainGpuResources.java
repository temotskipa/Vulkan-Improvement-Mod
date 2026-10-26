package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBuffer;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBufferPlacement;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;
import org.lwjgl.vulkan.EXTDescriptorBuffer;
import org.lwjgl.vulkan.EXTDescriptorHeap;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;

import java.lang.foreign.MemorySegment;
import java.util.LinkedHashMap;
import java.util.Map;

record TerrainGpuResources(DeviceBuffer samplerHeap, DeviceBuffer resourceHeap, DeviceBuffer samplerDescriptorBuffer, DeviceBuffer resourceDescriptorBuffer, DeviceBuffer sectionMetadata, DeviceBuffer meshletHeaders, DeviceBuffer meshletVertices, DeviceBuffer meshletIndices, DeviceBuffer visibleMeshlets, DeviceBuffer workQueue, DeviceBuffer meshTaskCommands, DeviceBuffer materialTable, DeviceBuffer debugCounters) {
    static TerrainGpuResources empty() {
        return new TerrainGpuResources(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
    
    static TerrainGpuResources create(VulkanDevice device, TerrainDescriptorHeapLayout layout) {
        if (!layout.configured()) {
            return empty();
        }
        int descriptorHeapUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorHeap.VK_BUFFER_USAGE_DESCRIPTOR_HEAP_BIT_EXT;
        int samplerDescriptorBufferUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
        int resourceDescriptorBufferUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorBuffer.VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
        int storageUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
        int indirectStorageUsage = storageUsage | VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
        DeviceBuffer samplerHeap = layout.descriptorHeapEnabled() ? new DeviceBuffer(device, "VIM Terrain Sampler Descriptor Heap", layout.samplerHeapBytes(), descriptorHeapUsage, DeviceBufferPlacement.DEVICE_MAPPED) : null;
        DeviceBuffer resourceHeap = layout.descriptorHeapEnabled() ? new DeviceBuffer(device, "VIM Terrain Resource Descriptor Heap", layout.resourceHeapBytes(), descriptorHeapUsage, DeviceBufferPlacement.DEVICE_MAPPED) : null;
        DeviceBuffer samplerDescriptorBuffer = layout.descriptorBufferEnabled() ? new DeviceBuffer(device, "VIM Terrain Sampler Descriptor Buffer", layout.samplerDescriptorBufferBytes(), samplerDescriptorBufferUsage, DeviceBufferPlacement.DEVICE_MAPPED) : null;
        DeviceBuffer resourceDescriptorBuffer = layout.descriptorBufferEnabled() ? new DeviceBuffer(device, "VIM Terrain Resource Descriptor Buffer", layout.resourceDescriptorBufferBytes(), resourceDescriptorBufferUsage, DeviceBufferPlacement.DEVICE_MAPPED) : null;
        // The page table and what the pages hold live in device memory and are written by copies only.
        DeviceBuffer sectionMetadata = new DeviceBuffer(device, "VIM Terrain Page Table", layout.sectionMetadataBytes(), storageUsage, DeviceBufferPlacement.DEVICE);
        DeviceBuffer meshletHeaders = new DeviceBuffer(device, "VIM Terrain Meshlet Headers", layout.meshletHeaderBytes(), storageUsage, DeviceBufferPlacement.DEVICE);
        DeviceBuffer meshletVertices = new DeviceBuffer(device, "VIM Terrain Meshlet Vertex Payload", layout.vertexPayloadBytes(), storageUsage, DeviceBufferPlacement.DEVICE);
        DeviceBuffer meshletIndices = new DeviceBuffer(device, "VIM Terrain Meshlet Index Payload", layout.indexPayloadBytes(), storageUsage, DeviceBufferPlacement.DEVICE);
        // What the CPU writes each frame and the GPU reads or counts up in the same frame. In host
        // memory every read and every atomic of the GPU crosses the bus: one atomic a meshlet
        // in host memory cost the opaque layers two milliseconds a frame.
        DeviceBuffer visibleMeshlets = new DeviceBuffer(device, "VIM Terrain Visible Meshlet List", layout.visibleMeshletBytes(), storageUsage, DeviceBufferPlacement.DEVICE_MAPPED);
        DeviceBuffer workQueue = new DeviceBuffer(device, "VIM Terrain Work Queue", layout.workQueueBytes(), storageUsage, DeviceBufferPlacement.DEVICE_MAPPED);
        DeviceBuffer meshTaskCommands = new DeviceBuffer(device, "VIM Terrain Mesh Task Commands", layout.meshTaskCommandBytes(), indirectStorageUsage, DeviceBufferPlacement.DEVICE_MAPPED);
        DeviceBuffer materialTable = new DeviceBuffer(device, "VIM Terrain Material Table", layout.materialTableBytes(), storageUsage, DeviceBufferPlacement.DEVICE_MAPPED);
        DeviceBuffer debugCounters = new DeviceBuffer(device, "VIM Terrain Debug Counters", TerrainGpuLayout.DEBUG_COUNTER_BYTES, storageUsage, DeviceBufferPlacement.DEVICE_MAPPED);
        return new TerrainGpuResources(samplerHeap, resourceHeap, samplerDescriptorBuffer, resourceDescriptorBuffer, sectionMetadata, meshletHeaders, meshletVertices, meshletIndices, visibleMeshlets, workQueue, meshTaskCommands, materialTable, debugCounters);
    }
    
    private static Map<String, Object> bufferMap(DeviceBuffer buffer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("allocated", buffer != null);
        if (buffer != null) {
            map.put("label", buffer.label());
            map.put("vkBuffer", buffer.vkBuffer());
            map.put("vkBufferHex", buffer.vkBufferHex());
            map.put("size", buffer.size());
            map.put("deviceAddress", buffer.deviceAddress());
            map.put("hostVisible", buffer.hostVisible());
            map.put("closed", buffer.closed());
            map.put("destroyed", buffer.destroyed());
        }
        return map;
    }
    
    private static void close(DeviceBuffer buffer) {
        if (buffer != null) {
            buffer.close();
        }
    }
    
    private static void destroyNow(DeviceBuffer buffer) {
        if (buffer != null) {
            buffer.destroyNow();
        }
    }
    
    private static int bufferCount(DeviceBuffer buffer) {
        return buffer == null ? 0 : 1;
    }
    
    private static long bufferBytes(DeviceBuffer buffer) {
        return buffer == null ? 0L : buffer.size();
    }
    
    boolean allocated() {
        return this.samplerHeap != null || this.resourceHeap != null || this.samplerDescriptorBuffer != null || this.resourceDescriptorBuffer != null || this.sectionMetadata != null || this.meshletHeaders != null || this.meshletVertices != null || this.meshletIndices != null || this.visibleMeshlets != null || this.workQueue != null || this.meshTaskCommands != null || this.materialTable != null || this.debugCounters != null;
    }
    
    GpuResourceRetirementStats retirementStats() {
        return GpuResourceRetirementStats.singleSet(allocatedBufferCount(), allocatedBytes());
    }
    
    private int allocatedBufferCount() {
        return bufferCount(this.samplerHeap) + bufferCount(this.resourceHeap) + bufferCount(this.samplerDescriptorBuffer) + bufferCount(this.resourceDescriptorBuffer) + bufferCount(this.sectionMetadata) + bufferCount(this.meshletHeaders) + bufferCount(this.meshletVertices) + bufferCount(this.meshletIndices) + bufferCount(this.visibleMeshlets) + bufferCount(this.workQueue) + bufferCount(this.meshTaskCommands) + bufferCount(this.materialTable) + bufferCount(this.debugCounters);
    }
    
    private long allocatedBytes() {
        return bufferBytes(this.samplerHeap) + bufferBytes(this.resourceHeap) + bufferBytes(this.samplerDescriptorBuffer) + bufferBytes(this.resourceDescriptorBuffer) + bufferBytes(this.sectionMetadata) + bufferBytes(this.meshletHeaders) + bufferBytes(this.meshletVertices) + bufferBytes(this.meshletIndices) + bufferBytes(this.visibleMeshlets) + bufferBytes(this.workQueue) + bufferBytes(this.meshTaskCommands) + bufferBytes(this.materialTable) + bufferBytes(this.debugCounters);
    }
    
    long residentTerrainBytes() {
        return bufferBytes(this.sectionMetadata) + bufferBytes(this.meshletHeaders) + bufferBytes(this.meshletVertices) + bufferBytes(this.meshletIndices);
    }
    
    boolean readyForTerrainMetadata() {
        return this.sectionMetadata != null && this.meshletHeaders != null && this.meshletVertices != null && this.meshletIndices != null;
    }
    
    void clearDebugCounters() {
        if (this.debugCounters != null) {
            this.debugCounters.mapped().asSlice(0L, TerrainGpuLayout.DEBUG_COUNTER_BYTES).fill((byte) 0);
            this.debugCounters.flush();
        }
    }
    
    void clearWorkQueueCounters() {
        if (this.workQueue != null) {
            this.workQueue.mapped().asSlice(0L, TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES).fill((byte) 0);
            this.workQueue.flush();
        }
    }
    
    Map<String, Object> debugCounterMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("allocated", this.debugCounters != null);
        if (this.debugCounters != null) {
            this.debugCounters.invalidate();
            MemorySegment counters = this.debugCounters.mapped();
            // Counted only where the mesh stage culls meshlets itself, on the CPU work-queue path.
            map.put("culledMeshlets", Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, 4L)));
            map.put("visibilityInputPages", Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET)));
            map.put("visibilityCulledPages", Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET + Integer.BYTES)));
            map.put("visibilityVisiblePages", Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET + 2 * Integer.BYTES)));
        }
        return map;
    }
    
    boolean readyForDescriptorBuffer() {
        return this.resourceDescriptorBuffer != null && this.resourceDescriptorBuffer.deviceAddress() != 0L && this.resourceDescriptorBuffer.hostVisible();
    }
    
    void close() {
        close(this.debugCounters);
        close(this.materialTable);
        close(this.meshTaskCommands);
        close(this.workQueue);
        close(this.visibleMeshlets);
        close(this.meshletIndices);
        close(this.meshletVertices);
        close(this.meshletHeaders);
        close(this.sectionMetadata);
        close(this.resourceDescriptorBuffer);
        close(this.samplerDescriptorBuffer);
        close(this.resourceHeap);
        close(this.samplerHeap);
    }
    
    void destroyNow() {
        destroyNow(this.debugCounters);
        destroyNow(this.materialTable);
        destroyNow(this.meshTaskCommands);
        destroyNow(this.workQueue);
        destroyNow(this.visibleMeshlets);
        destroyNow(this.meshletIndices);
        destroyNow(this.meshletVertices);
        destroyNow(this.meshletHeaders);
        destroyNow(this.sectionMetadata);
        destroyNow(this.resourceDescriptorBuffer);
        destroyNow(this.samplerDescriptorBuffer);
        destroyNow(this.resourceHeap);
        destroyNow(this.samplerHeap);
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("samplerHeap", bufferMap(this.samplerHeap));
        map.put("resourceHeap", bufferMap(this.resourceHeap));
        map.put("samplerDescriptorBuffer", bufferMap(this.samplerDescriptorBuffer));
        map.put("resourceDescriptorBuffer", bufferMap(this.resourceDescriptorBuffer));
        map.put("sectionMetadata", bufferMap(this.sectionMetadata));
        map.put("meshletHeaders", bufferMap(this.meshletHeaders));
        map.put("meshletVertices", bufferMap(this.meshletVertices));
        map.put("meshletIndices", bufferMap(this.meshletIndices));
        map.put("visibleMeshlets", bufferMap(this.visibleMeshlets));
        map.put("workQueue", bufferMap(this.workQueue));
        map.put("meshTaskCommands", bufferMap(this.meshTaskCommands));
        map.put("materialTable", bufferMap(this.materialTable));
        map.put("debugCounters", bufferMap(this.debugCounters));
        return map;
    }
}