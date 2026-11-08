package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DescriptorStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBuffer;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBufferPlacement;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;

import java.lang.foreign.MemorySegment;

record TerrainGpuResources(DeviceBuffer samplerHeap, DeviceBuffer resourceHeap, DeviceBuffer samplerDescriptorBuffer, DeviceBuffer resourceDescriptorBuffer, DeviceBuffer sectionMetadata, DeviceBuffer meshletHeaders, DeviceBuffer meshletVertices, DeviceBuffer meshletIndices, DeviceBuffer visibleMeshlets, DeviceBuffer workQueue, DeviceBuffer meshTaskCommands, DeviceBuffer materialTable, DeviceBuffer debugCounters) {
	static TerrainGpuResources create(VulkanDevice device, TerrainDescriptorHeapLayout layout) {
		if (!layout.configured()) {
			return empty();
		}
		int descriptorHeapUsage = GpuBuffer.USAGE_COPY_DST | DescriptorStorage.USAGE_DESCRIPTOR_HEAP;
		int samplerDescriptorBufferUsage = GpuBuffer.USAGE_COPY_DST | DescriptorStorage.USAGE_SAMPLER_DESCRIPTORS;
		int resourceDescriptorBufferUsage = GpuBuffer.USAGE_COPY_DST | DescriptorStorage.USAGE_RESOURCE_DESCRIPTORS | DescriptorStorage.USAGE_SAMPLER_DESCRIPTORS;
		int storageUsage = GpuBuffer.USAGE_COPY_DST | ShaderStorage.USAGE_SHADER_STORAGE;
		int indirectStorageUsage = storageUsage | GpuBuffer.USAGE_INDIRECT_PARAMETERS;
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
	
	static TerrainGpuResources empty() {
		return new TerrainGpuResources(null, null, null, null, null, null, null, null, null, null, null, null, null);
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
	
	private static int bufferCount(DeviceBuffer buffer) {
		return buffer == null ? 0 : 1;
	}
	
	private static long bufferBytes(DeviceBuffer buffer) {
		return buffer == null ? 0L : buffer.size();
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
		}
	}
	
	void clearWorkQueueCounters() {
		if (this.workQueue != null) {
			this.workQueue.mapped().asSlice(0L, TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES).fill((byte) 0);
		}
	}
	
	/**
	 * What the GPU last counted. The mesh stage counts culled meshlets only where it culls them
	 * itself, on the CPU work-queue path.
	 */
	String describeDebugCounters() {
		if (this.debugCounters == null) {
			return "none";
		}
		MemorySegment counters = this.debugCounters.mapped();
		return "culledMeshlets=" + Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, 4L)) + ", visibilityInputPages=" + Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET)) + ", visibilityCulledPages=" + Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET + Integer.BYTES)) + ", visibilityVisiblePages=" + Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET + 2 * Integer.BYTES));
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
	
	private static void close(DeviceBuffer buffer) {
		if (buffer != null) {
			buffer.close();
		}
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
	
	private static void destroyNow(DeviceBuffer buffer) {
		if (buffer != null) {
			buffer.destroyNow();
		}
	}
}