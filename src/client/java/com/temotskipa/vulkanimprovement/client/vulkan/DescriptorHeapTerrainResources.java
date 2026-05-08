package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class DescriptorHeapTerrainResources {
    static final int SECTION_METADATA_STRIDE = 80;
    static final int MESHLET_HEADER_STRIDE = 64;
    private static final int DEBUG_COUNTER_BYTES = 64;
    private static final DescriptorHeapTerrainResources INSTANCE = new DescriptorHeapTerrainResources();
    private final AtomicBoolean terrainDataDirty = new AtomicBoolean();
    private final Map<String, TextureBinding> textureBindings = new ConcurrentHashMap<>();
    private final LongAdder descriptorBufferWrites = new LongAdder();
    private final LongAdder descriptorBufferMissing = new LongAdder();
    private volatile Layout layout = Layout.unconfigured();
    private volatile GpuResources resources = GpuResources.empty();
    private volatile UploadStats uploadStats = UploadStats.empty();
    
    private DescriptorHeapTerrainResources() {
    }
    
    public static DescriptorHeapTerrainResources get() {
        return INSTANCE;
    }
    
    private static void writeSampledImageDescriptor(VulkanDevice device, MemoryStack stack, ByteBuffer descriptors, long offset, long size, TextureBinding binding) {
        VkDescriptorImageInfo imageInfo = VkDescriptorImageInfo.calloc(stack).imageView(binding.imageView()).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
        VkDescriptorGetInfoEXT descriptorInfo = VkDescriptorGetInfoEXT.calloc(stack).sType$Default().type(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE);
        descriptorInfo.data().pSampledImage(imageInfo);
        EXTDescriptorBuffer.vkGetDescriptorEXT(device.vkDevice(), descriptorInfo, descriptorSlice(descriptors, offset, size));
    }
    
    private static void writeSamplerDescriptor(VulkanDevice device, MemoryStack stack, ByteBuffer descriptors, long offset, long size, TextureBinding binding) {
        VkDescriptorGetInfoEXT descriptorInfo = VkDescriptorGetInfoEXT.calloc(stack).sType$Default().type(VK10.VK_DESCRIPTOR_TYPE_SAMPLER);
        descriptorInfo.data().pSampler(stack.longs(binding.sampler()));
        EXTDescriptorBuffer.vkGetDescriptorEXT(device.vkDevice(), descriptorInfo, descriptorSlice(descriptors, offset, size));
    }
    
    private static ByteBuffer descriptorSlice(ByteBuffer source, long offset, long size) {
        if (offset < 0L || size <= 0L || offset + size > source.capacity() || offset + size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Descriptor buffer write exceeds mapped terrain descriptor buffer: offset=" + offset + ", size=" + size + ", capacity=" + source.capacity());
        }
        ByteBuffer duplicate = source.duplicate();
        duplicate.position((int) offset);
        duplicate.limit((int) (offset + size));
        return duplicate.slice().order(ByteOrder.LITTLE_ENDIAN);
    }
    
    public synchronized void configure(VulkanDevice device, VulkanImprovementCapabilities.Snapshot capabilities) {
        this.resources.close();
        this.layout = Layout.from(capabilities);
        this.resources = GpuResources.create(device, this.layout);
        this.resources.clearDebugCounters();
        this.terrainDataDirty.set(true);
    }
    
    public synchronized void shutdown() {
        this.resources.close();
        this.resources = GpuResources.empty();
        this.layout = Layout.unconfigured();
        this.uploadStats = UploadStats.empty();
        this.textureBindings.clear();
        this.descriptorBufferWrites.reset();
        this.descriptorBufferMissing.reset();
        this.terrainDataDirty.set(false);
    }
    
    public void recordTextureBinding(String name, GpuTextureView view, GpuSampler sampler) {
        if (!(view instanceof VulkanGpuTextureView vulkanView) || !(sampler instanceof VulkanGpuSampler vulkanSampler)) {
            this.textureBindings.put(name, TextureBinding.unavailable(name, view, sampler));
            return;
        }
        
        this.textureBindings.put(name, new TextureBinding(name, vulkanView.vkImageView(), vulkanSampler.vkSampler(), view.getWidth(0), view.getHeight(0), view.baseMipLevel(), view.mipLevels(), view.isClosed(), false));
    }
    
    public boolean textureBindingReady(String name) {
        TextureBinding binding = this.textureBindings.get(name);
        return binding != null && !binding.unavailable() && !binding.closed() && binding.imageView() != 0L && binding.sampler() != 0L;
    }
    
    public long textureImageView(String name) {
        TextureBinding binding = this.textureBindings.get(name);
        return binding == null ? 0L : binding.imageView();
    }
    
    public long textureSampler(String name) {
        TextureBinding binding = this.textureBindings.get(name);
        return binding == null ? 0L : binding.sampler();
    }
    
    public void markTerrainDataDirty() {
        this.terrainDataDirty.set(true);
    }
    
    public void uploadDirtyTerrainData() {
        if (!this.terrainDataDirty.compareAndSet(true, false)) {
            return;
        }
        GpuResources activeResources = this.resources;
        if (!activeResources.readyForTerrainMetadata()) {
            return;
        }
        
        UploadStats stats = SectionMeshletStore.writeMetadataSnapshot(activeResources.sectionMetadata().mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN), activeResources.meshletHeaders().mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN), activeResources.meshletVertices().mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN), activeResources.meshletIndices().mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN), this.layout.sectionCapacity, this.layout.meshletCapacity);
        activeResources.sectionMetadata().flush();
        activeResources.meshletHeaders().flush();
        activeResources.meshletVertices().flush();
        activeResources.meshletIndices().flush();
        this.uploadStats = stats;
    }
    
    public boolean hasGpuTerrainData() {
        return this.resources.readyForTerrainMetadata() && this.uploadStats.meshletsUploaded() > 0;
    }
    
    public int meshletsUploaded() {
        return this.uploadStats.meshletsUploaded();
    }
    
    public int meshletOffsetForLayer(int layerOrdinal) {
        return this.uploadStats.meshletOffsetForLayer(layerOrdinal);
    }
    
    public int meshletCountForLayer(int layerOrdinal) {
        return this.uploadStats.meshletCountForLayer(layerOrdinal);
    }

    public int customIndexMeshletCountForLayer(int layerOrdinal) {
        return this.uploadStats.customIndexMeshletCountForLayer(layerOrdinal);
    }
    
    public long sectionMetadataAddress() {
        TerrainGpuBuffer buffer = this.resources.sectionMetadata();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long meshletHeaderAddress() {
        TerrainGpuBuffer buffer = this.resources.meshletHeaders();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long meshletVertexPayloadAddress() {
        TerrainGpuBuffer buffer = this.resources.meshletVertices();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long meshletIndexPayloadAddress() {
        TerrainGpuBuffer buffer = this.resources.meshletIndices();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long terrainDescriptorBufferAddress() {
        TerrainGpuBuffer buffer = this.resources.resourceDescriptorBuffer();
        return buffer == null ? 0L : buffer.deviceAddress();
    }

    public long terrainDebugCounterAddress() {
        TerrainGpuBuffer buffer = this.resources.debugCounters();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public boolean writeTerrainTextureDescriptors(VulkanDevice device, TextureDescriptorLayout textureLayout) {
        GpuResources activeResources = this.resources;
        if (!this.layout.descriptorBufferEnabled || !activeResources.readyForDescriptorBuffer() || !textureBindingReady("Sampler0") || !textureBindingReady("Sampler2")) {
            this.descriptorBufferMissing.increment();
            return false;
        }
        
        TextureBinding blockAtlas = this.textureBindings.get("Sampler0");
        TextureBinding lightmap = this.textureBindings.get("Sampler2");
        ByteBuffer descriptors = activeResources.resourceDescriptorBuffer().mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            writeSampledImageDescriptor(device, stack, descriptors, textureLayout.blockAtlasImageOffset(), textureLayout.sampledImageDescriptorBytes(), blockAtlas);
            writeSamplerDescriptor(device, stack, descriptors, textureLayout.blockAtlasSamplerOffset(), textureLayout.samplerDescriptorBytes(), blockAtlas);
            writeSampledImageDescriptor(device, stack, descriptors, textureLayout.lightmapImageOffset(), textureLayout.sampledImageDescriptorBytes(), lightmap);
            writeSamplerDescriptor(device, stack, descriptors, textureLayout.lightmapSamplerOffset(), textureLayout.samplerDescriptorBytes(), lightmap);
        }
        activeResources.resourceDescriptorBuffer().flush();
        this.descriptorBufferWrites.increment();
        return true;
    }
    
    public Layout layout() {
        return this.layout;
    }
    
    public Map<String, Object> asMap() {
        Map<String, Object> map = this.layout.asMap();
        map.put("resources", this.resources.asMap());
        map.put("uploadStats", this.uploadStats.asMap());
        map.put("terrainDataDirty", this.terrainDataDirty.get());
        map.put("sectionMetadataAddress", sectionMetadataAddress());
        map.put("meshletHeaderAddress", meshletHeaderAddress());
        map.put("meshletVertexPayloadAddress", meshletVertexPayloadAddress());
        map.put("meshletIndexPayloadAddress", meshletIndexPayloadAddress());
        map.put("terrainDescriptorBufferAddress", terrainDescriptorBufferAddress());
        map.put("terrainDebugCounterAddress", terrainDebugCounterAddress());
        map.put("descriptorBufferWrites", this.descriptorBufferWrites.sum());
        map.put("descriptorBufferMissing", this.descriptorBufferMissing.sum());
        map.put("debugCounters", this.resources.debugCounterMap());
        map.put("textureBindings", textureBindingMap());
        return map;
    }
    
    private Map<String, Object> textureBindingMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        this.textureBindings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> map.put(entry.getKey(), entry.getValue().asMap()));
        return map;
    }
    
    public enum Slot {
        BLOCK_ATLAS("blockAtlas", "sampledImage"), LIGHTMAP("lightmap", "sampledImage"), GLOBAL_UNIFORMS("globalUniforms", "uniformBuffer"), SECTION_METADATA("sectionMetadata", "storageBuffer"), MESHLET_HEADERS("meshletHeaders", "storageBuffer"), MESHLET_VERTICES("meshletVertices", "storageBuffer"), MESHLET_INDICES("meshletIndices", "storageBuffer"), MATERIAL_TABLE("materialTable", "storageBuffer"), DEBUG_COUNTERS("debugCounters", "storageBuffer");
        
        private final String id;
        private final String descriptorType;
        
        Slot(String id, String descriptorType) {
            this.id = id;
            this.descriptorType = descriptorType;
        }
    }
    
    public record TextureDescriptorLayout(long layoutBytes, long blockAtlasImageOffset, long blockAtlasSamplerOffset,
                                          long lightmapImageOffset, long lightmapSamplerOffset,
                                          long sampledImageDescriptorBytes, long samplerDescriptorBytes) {
        public Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("layoutBytes", this.layoutBytes);
            map.put("blockAtlasImageOffset", this.blockAtlasImageOffset);
            map.put("blockAtlasSamplerOffset", this.blockAtlasSamplerOffset);
            map.put("lightmapImageOffset", this.lightmapImageOffset);
            map.put("lightmapSamplerOffset", this.lightmapSamplerOffset);
            map.put("sampledImageDescriptorBytes", this.sampledImageDescriptorBytes);
            map.put("samplerDescriptorBytes", this.samplerDescriptorBytes);
            return map;
        }
    }
    
    public record Layout(boolean configured, boolean descriptorHeapEnabled, boolean descriptorBufferEnabled,
                         int sectionCapacity, int meshletCapacity, long vertexPayloadBytes, long indexPayloadBytes,
                         long samplerHeapBytes, long resourceHeapBytes, long samplerDescriptorBufferBytes,
                         long resourceDescriptorBufferBytes, long sectionMetadataBytes, long meshletHeaderBytes,
                         long samplerDescriptorBytes, long imageDescriptorBytes, long bufferDescriptorBytes,
                         long descriptorBufferAlignment, long descriptorBufferSamplerDescriptorBytes,
                         long descriptorBufferSampledImageDescriptorBytes,
                         long descriptorBufferUniformBufferDescriptorBytes,
                         long descriptorBufferStorageBufferDescriptorBytes, Map<String, String> slots) {
        static Layout unconfigured() {
            return new Layout(false, false, false, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, Map.of());
        }
        
        static Layout from(VulkanImprovementCapabilities.Snapshot capabilities) {
            Map<String, String> slots = new LinkedHashMap<>();
            int sampledImages = 0;
            int uniformBuffers = 0;
            int storageBuffers = 0;
            for (Slot slot : Slot.values()) {
                slots.put(slot.id, slot.descriptorType);
                if ("sampledImage".equals(slot.descriptorType)) {
                    sampledImages++;
                } else if ("uniformBuffer".equals(slot.descriptorType)) {
                    uniformBuffers++;
                } else {
                    storageBuffers++;
                }
            }
            
            int sectionCapacity = TerrainRendererDebugConfig.INITIAL_SECTION_CAPACITY;
            int meshletCapacity = TerrainRendererDebugConfig.INITIAL_MESHLET_CAPACITY;
            long vertexPayloadBytes = TerrainRendererDebugConfig.INITIAL_VERTEX_PAYLOAD_BYTES;
            long indexPayloadBytes = TerrainRendererDebugConfig.INITIAL_INDEX_PAYLOAD_BYTES;
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
            long sectionMetadataBytes = (long) sectionCapacity * SECTION_METADATA_STRIDE;
            long meshletHeaderBytes = (long) meshletCapacity * MESHLET_HEADER_STRIDE;
            return new Layout(true, capabilities.descriptorHeapExtension(), capabilities.descriptorBufferEnabled(), sectionCapacity, meshletCapacity, vertexPayloadBytes, indexPayloadBytes, samplerHeapBytes, resourceHeapBytes, samplerDescriptorBufferBytes, resourceDescriptorBufferBytes, sectionMetadataBytes, meshletHeaderBytes, samplerDescriptorBytes, imageDescriptorBytes, bufferDescriptorBytes, alignment, descriptorBufferSamplerBytes, descriptorBufferSampledImageBytes, descriptorBufferUniformBytes, descriptorBufferStorageBytes, Map.copyOf(slots));
        }
        
        private static long align(long value, long alignment) {
            return (value + alignment - 1L) / alignment * alignment;
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
    
    public record UploadStats(int sectionsUploaded, int layersUploaded, int meshletsUploaded, int sectionsDropped,
                              int meshletsDropped, long vertexBytesUploaded, long indexBytesUploaded,
                              long vertexBytesDropped, long indexBytesDropped, int[] meshletOffsetsByLayer,
                              int[] meshletCountsByLayer, int[] customIndexMeshletCountsByLayer, long uploadCount) {
        public UploadStats {
            meshletOffsetsByLayer = meshletOffsetsByLayer.clone();
            meshletCountsByLayer = meshletCountsByLayer.clone();
            customIndexMeshletCountsByLayer = customIndexMeshletCountsByLayer.clone();
        }
        
        static UploadStats empty() {
            return new UploadStats(0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, emptyLayerCounters(), emptyLayerCounters(), emptyLayerCounters(), 0L);
        }
        
        private static int[] emptyLayerCounters() {
            return new int[ChunkSectionLayer.values().length];
        }
        
        UploadStats nextUpload(int sections, int layers, int meshlets, int droppedSections, int droppedMeshlets, long vertexBytesUploaded, long indexBytesUploaded, long vertexBytesDropped, long indexBytesDropped, int[] meshletOffsetsByLayer, int[] meshletCountsByLayer, int[] customIndexMeshletCountsByLayer) {
            return new UploadStats(sections, layers, meshlets, droppedSections, droppedMeshlets, vertexBytesUploaded, indexBytesUploaded, vertexBytesDropped, indexBytesDropped, meshletOffsetsByLayer, meshletCountsByLayer, customIndexMeshletCountsByLayer, this.uploadCount + 1L);
        }
        
        int meshletOffsetForLayer(int layerOrdinal) {
            if (layerOrdinal < 0 || layerOrdinal >= this.meshletOffsetsByLayer.length) {
                return 0;
            }
            return this.meshletOffsetsByLayer[layerOrdinal];
        }
        
        int meshletCountForLayer(int layerOrdinal) {
            if (layerOrdinal < 0 || layerOrdinal >= this.meshletCountsByLayer.length) {
                return this.meshletsUploaded;
            }
            return this.meshletCountsByLayer[layerOrdinal];
        }

        int customIndexMeshletCountForLayer(int layerOrdinal) {
            if (layerOrdinal < 0 || layerOrdinal >= this.customIndexMeshletCountsByLayer.length) {
                return 0;
            }
            return this.customIndexMeshletCountsByLayer[layerOrdinal];
        }
        
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sectionsUploaded", this.sectionsUploaded);
            map.put("layersUploaded", this.layersUploaded);
            map.put("meshletsUploaded", this.meshletsUploaded);
            map.put("sectionsDropped", this.sectionsDropped);
            map.put("meshletsDropped", this.meshletsDropped);
            map.put("vertexBytesUploaded", this.vertexBytesUploaded);
            map.put("indexBytesUploaded", this.indexBytesUploaded);
            map.put("vertexBytesDropped", this.vertexBytesDropped);
            map.put("indexBytesDropped", this.indexBytesDropped);
            map.put("meshletRangesByLayer", meshletRangesByLayer());
            map.put("customIndexMeshletsByLayer", customIndexMeshletsByLayer());
            map.put("uploadCount", this.uploadCount);
            return map;
        }
        
        private Map<String, Object> meshletRangesByLayer() {
            Map<String, Object> ranges = new LinkedHashMap<>();
            for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                int ordinal = layer.ordinal();
                ranges.put(layer.label(), Map.of("offset", ordinal < this.meshletOffsetsByLayer.length ? this.meshletOffsetsByLayer[ordinal] : 0, "count", ordinal < this.meshletCountsByLayer.length ? this.meshletCountsByLayer[ordinal] : 0));
            }
            return ranges;
        }

        private Map<String, Object> customIndexMeshletsByLayer() {
            Map<String, Object> ranges = new LinkedHashMap<>();
            for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                int ordinal = layer.ordinal();
                ranges.put(layer.label(), ordinal < this.customIndexMeshletCountsByLayer.length ? this.customIndexMeshletCountsByLayer[ordinal] : 0);
            }
            return ranges;
        }
    }
    
    private record GpuResources(TerrainGpuBuffer samplerHeap, TerrainGpuBuffer resourceHeap,
                                TerrainGpuBuffer samplerDescriptorBuffer, TerrainGpuBuffer resourceDescriptorBuffer,
                                TerrainGpuBuffer sectionMetadata, TerrainGpuBuffer meshletHeaders,
                                TerrainGpuBuffer meshletVertices, TerrainGpuBuffer meshletIndices,
                                TerrainGpuBuffer debugCounters) {
        static GpuResources empty() {
            return new GpuResources(null, null, null, null, null, null, null, null, null);
        }
        
        static GpuResources create(VulkanDevice device, Layout layout) {
            if (!layout.configured) {
                return empty();
            }
            int descriptorHeapUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorHeap.VK_BUFFER_USAGE_DESCRIPTOR_HEAP_BIT_EXT;
            int samplerDescriptorBufferUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
            int resourceDescriptorBufferUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorBuffer.VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
            int storageUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
            TerrainGpuBuffer samplerHeap = layout.descriptorHeapEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Sampler Descriptor Heap", layout.samplerHeapBytes, descriptorHeapUsage, true) : null;
            TerrainGpuBuffer resourceHeap = layout.descriptorHeapEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Resource Descriptor Heap", layout.resourceHeapBytes, descriptorHeapUsage, true) : null;
            TerrainGpuBuffer samplerDescriptorBuffer = layout.descriptorBufferEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Sampler Descriptor Buffer", layout.samplerDescriptorBufferBytes, samplerDescriptorBufferUsage, true) : null;
            TerrainGpuBuffer resourceDescriptorBuffer = layout.descriptorBufferEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Resource Descriptor Buffer", layout.resourceDescriptorBufferBytes, resourceDescriptorBufferUsage, true) : null;
            TerrainGpuBuffer sectionMetadata = new TerrainGpuBuffer(device, "VIM Terrain Section Metadata", layout.sectionMetadataBytes, storageUsage, true);
            TerrainGpuBuffer meshletHeaders = new TerrainGpuBuffer(device, "VIM Terrain Meshlet Headers", layout.meshletHeaderBytes, storageUsage, true);
            TerrainGpuBuffer meshletVertices = new TerrainGpuBuffer(device, "VIM Terrain Meshlet Vertex Payload", layout.vertexPayloadBytes, storageUsage, true);
            TerrainGpuBuffer meshletIndices = new TerrainGpuBuffer(device, "VIM Terrain Meshlet Index Payload", layout.indexPayloadBytes, storageUsage, true);
            TerrainGpuBuffer debugCounters = new TerrainGpuBuffer(device, "VIM Terrain Debug Counters", DEBUG_COUNTER_BYTES, storageUsage, true);
            return new GpuResources(samplerHeap, resourceHeap, samplerDescriptorBuffer, resourceDescriptorBuffer, sectionMetadata, meshletHeaders, meshletVertices, meshletIndices, debugCounters);
        }
        
        private static Map<String, Object> bufferMap(TerrainGpuBuffer buffer) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("allocated", buffer != null);
            if (buffer != null) {
                map.put("vkBuffer", buffer.vkBuffer());
                map.put("size", buffer.size());
                map.put("deviceAddress", buffer.deviceAddress());
                map.put("hostVisible", buffer.hostVisible());
            }
            return map;
        }
        
        private static void close(TerrainGpuBuffer buffer) {
            if (buffer != null) {
                buffer.close();
            }
        }
        
        boolean readyForTerrainMetadata() {
            return this.sectionMetadata != null && this.meshletHeaders != null && this.meshletVertices != null && this.meshletIndices != null;
        }

        void clearDebugCounters() {
            if (this.debugCounters != null) {
                ByteBuffer counters = this.debugCounters.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
                counters.clear();
                for (int i = 0; i < DEBUG_COUNTER_BYTES / Integer.BYTES; i++) {
                    counters.putInt(0);
                }
                this.debugCounters.flush();
            }
        }

        Map<String, Object> debugCounterMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("allocated", this.debugCounters != null);
            if (this.debugCounters != null) {
                this.debugCounters.invalidate();
                ByteBuffer counters = this.debugCounters.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
                map.put("candidateMeshlets", Integer.toUnsignedLong(counters.getInt(0)));
                map.put("culledMeshlets", Integer.toUnsignedLong(counters.getInt(4)));
                map.put("emittedMeshlets", Integer.toUnsignedLong(counters.getInt(8)));
                map.put("reserved", Integer.toUnsignedLong(counters.getInt(12)));
            }
            return map;
        }
        
        boolean readyForDescriptorBuffer() {
            return this.resourceDescriptorBuffer != null && this.resourceDescriptorBuffer.deviceAddress() != 0L && this.resourceDescriptorBuffer.hostVisible();
        }
        
        void close() {
            close(this.debugCounters);
            close(this.meshletIndices);
            close(this.meshletVertices);
            close(this.meshletHeaders);
            close(this.sectionMetadata);
            close(this.resourceDescriptorBuffer);
            close(this.samplerDescriptorBuffer);
            close(this.resourceHeap);
            close(this.samplerHeap);
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
            map.put("debugCounters", bufferMap(this.debugCounters));
            return map;
        }
    }
    
    private record TextureBinding(String name, long imageView, long sampler, int width, int height, int baseMipLevel,
                                  int mipLevels, boolean closed, boolean unavailable) {
        private static TextureBinding unavailable(String name, GpuTextureView view, GpuSampler sampler) {
            return new TextureBinding(name, 0L, 0L, view == null ? 0 : view.getWidth(0), view == null ? 0 : view.getHeight(0), view == null ? 0 : view.baseMipLevel(), view == null ? 0 : view.mipLevels(), view == null || view.isClosed(), true);
        }
        
        private Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", this.name);
            map.put("imageView", this.imageView);
            map.put("sampler", this.sampler);
            map.put("width", this.width);
            map.put("height", this.height);
            map.put("baseMipLevel", this.baseMipLevel);
            map.put("mipLevels", this.mipLevels);
            map.put("closed", this.closed);
            map.put("unavailable", this.unavailable);
            return map;
        }
    }
}
