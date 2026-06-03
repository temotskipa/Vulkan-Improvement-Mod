package com.temotskipa.vulkanimprovement.client.vulkan;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class DescriptorHeapTerrainResources {
    private static final DescriptorHeapTerrainResources INSTANCE = new DescriptorHeapTerrainResources();
    private final AtomicBoolean terrainDataDirty = new AtomicBoolean();
    private final Map<String, TextureBinding> textureBindings = new ConcurrentHashMap<>();
    private final LongAdder descriptorBufferWrites = new LongAdder();
    private final LongAdder descriptorBufferMissing = new LongAdder();
    private final LongAdder materialTableWrites = new LongAdder();
    private final LongAdder materialTableMissing = new LongAdder();
    private final LongAdder terrainCapacityGrowths = new LongAdder();
    private final LongAdder terrainUploadDeviceIdleWaits = new LongAdder();
    private final LongAdder terrainUploadFenceWaits = new LongAdder();
    private final LongAdder terrainUploadFenceDeferrals = new LongAdder();
    private final LongAdder terrainUploadFenceFailures = new LongAdder();
    private final LongAdder visibleMeshletListUploads = new LongAdder();
    private final LongAdder visibleMeshletRecordsUploaded = new LongAdder();
    private final LongAdder visibleMeshletRecordsDropped = new LongAdder();
    private final LongAdder visibleMeshletListDeferrals = new LongAdder();
    private final LongAdder visibleMeshletRingWraps = new LongAdder();
    private final LongAdder terrainWorkQueueUploads = new LongAdder();
    private final LongAdder terrainWorkQueueRecordsUploaded = new LongAdder();
    private final LongAdder terrainWorkQueueRecordsDropped = new LongAdder();
    private final LongAdder terrainWorkQueueDeferrals = new LongAdder();
    private final LongAdder terrainWorkQueueRingWraps = new LongAdder();
    private final LongAdder terrainMeshTaskCommandUploads = new LongAdder();
    private final LongAdder terrainMeshTaskCommandReservations = new LongAdder();
    private final LongAdder terrainMeshTaskCommandDrops = new LongAdder();
    private final LongAdder terrainMeshTaskCommandDeferrals = new LongAdder();
    private final LongAdder terrainMeshTaskCommandRingWraps = new LongAdder();
    private final List<GpuResources> retiredResources = new ArrayList<>();
    private volatile Layout layout = Layout.unconfigured();
    private volatile GpuResources resources = GpuResources.empty();
    private volatile UploadStats uploadStats = UploadStats.empty();
    private volatile VulkanDevice device;
    private volatile GpuFence lastTerrainReadFence;
    private volatile VisibleMeshletStats lastVisibleMeshletUpload = VisibleMeshletStats.empty();
    private volatile TerrainWorkQueueStats lastWorkQueueUpload = TerrainWorkQueueStats.empty();
    private volatile MeshTaskCommandStats lastMeshTaskCommandUpload = MeshTaskCommandStats.empty();
    private int visibleMeshletCursor;
    private int workQueueCursor;
    private int meshTaskCommandCursor;
    private volatile String lastCapacityGrowthReason = "";

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

    private static GpuMaterialRecord.TextureInfo textureInfo(TextureBinding binding) {
        if (binding == null || binding.unavailable() || binding.closed()) {
            return GpuMaterialRecord.TextureInfo.unavailable();
        }
        return new GpuMaterialRecord.TextureInfo(true, binding.width(), binding.height(), binding.baseMipLevel(), binding.mipLevels());
    }

    private static int visibleMeshletRecordCapacity(TerrainGpuBuffer visibleMeshlets) {
        return (int) Math.min(Integer.MAX_VALUE, visibleMeshlets.size() / TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
    }

    private static int workQueueRecordCapacity(TerrainGpuBuffer workQueue) {
        long payloadBytes = Math.max(0L, workQueue.size() - TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES);
        return (int) Math.min(Integer.MAX_VALUE, payloadBytes / TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE);
    }

    private static int meshTaskCommandCapacity(TerrainGpuBuffer meshTaskCommands) {
        return (int) Math.min(Integer.MAX_VALUE, meshTaskCommands.size() / TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE);
    }

    private static int visibleMeshletRecordCount(List<SectionMeshletStore.MeshletRange> ranges) {
        long count = 0L;
        for (SectionMeshletStore.MeshletRange range : ranges) {
            count += Math.max(range.count(), 0);
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    public synchronized void configure(VulkanDevice device, VulkanImprovementCapabilities.Snapshot capabilities) {
        this.device = device;
        retireResources(this.resources);
        this.layout = Layout.from(capabilities);
        this.resources = GpuResources.create(device, this.layout);
        this.resources.clearDebugCounters();
        this.resources.clearWorkQueueCounters();
        writeTerrainMaterialRecords(this.resources, null, null);
        this.terrainDataDirty.set(true);
        this.visibleMeshletCursor = 0;
        this.workQueueCursor = 0;
        this.meshTaskCommandCursor = 0;
    }

    public synchronized void shutdown() {
        retireResources(this.resources);
        this.resources = GpuResources.empty();
        this.layout = Layout.unconfigured();
        this.uploadStats = UploadStats.empty();
        this.textureBindings.clear();
        this.descriptorBufferWrites.reset();
        this.descriptorBufferMissing.reset();
        this.materialTableWrites.reset();
        this.materialTableMissing.reset();
        this.terrainCapacityGrowths.reset();
        this.terrainUploadDeviceIdleWaits.reset();
        this.terrainUploadFenceWaits.reset();
        this.terrainUploadFenceDeferrals.reset();
        this.terrainUploadFenceFailures.reset();
        this.visibleMeshletListUploads.reset();
        this.visibleMeshletRecordsUploaded.reset();
        this.visibleMeshletRecordsDropped.reset();
        this.visibleMeshletListDeferrals.reset();
        this.visibleMeshletRingWraps.reset();
        this.terrainWorkQueueUploads.reset();
        this.terrainWorkQueueRecordsUploaded.reset();
        this.terrainWorkQueueRecordsDropped.reset();
        this.terrainWorkQueueDeferrals.reset();
        this.terrainWorkQueueRingWraps.reset();
        this.terrainMeshTaskCommandUploads.reset();
        this.terrainMeshTaskCommandReservations.reset();
        this.terrainMeshTaskCommandDrops.reset();
        this.terrainMeshTaskCommandDeferrals.reset();
        this.terrainMeshTaskCommandRingWraps.reset();
        this.lastVisibleMeshletUpload = VisibleMeshletStats.empty();
        this.lastWorkQueueUpload = TerrainWorkQueueStats.empty();
        this.lastMeshTaskCommandUpload = MeshTaskCommandStats.empty();
        this.visibleMeshletCursor = 0;
        this.workQueueCursor = 0;
        this.meshTaskCommandCursor = 0;
        this.terrainDataDirty.set(false);
        closeLastTerrainReadFence();
        this.device = null;
        this.lastCapacityGrowthReason = "";
    }

    public synchronized void shutdownNow() {
        destroyRetiredResourcesNow();
        this.resources.destroyNow();
        this.resources = GpuResources.empty();
        this.layout = Layout.unconfigured();
        this.uploadStats = UploadStats.empty();
        this.textureBindings.clear();
        this.descriptorBufferWrites.reset();
        this.descriptorBufferMissing.reset();
        this.materialTableWrites.reset();
        this.materialTableMissing.reset();
        this.terrainCapacityGrowths.reset();
        this.terrainUploadDeviceIdleWaits.reset();
        this.terrainUploadFenceWaits.reset();
        this.terrainUploadFenceDeferrals.reset();
        this.terrainUploadFenceFailures.reset();
        this.visibleMeshletListUploads.reset();
        this.visibleMeshletRecordsUploaded.reset();
        this.visibleMeshletRecordsDropped.reset();
        this.visibleMeshletListDeferrals.reset();
        this.visibleMeshletRingWraps.reset();
        this.terrainWorkQueueUploads.reset();
        this.terrainWorkQueueRecordsUploaded.reset();
        this.terrainWorkQueueRecordsDropped.reset();
        this.terrainWorkQueueDeferrals.reset();
        this.terrainWorkQueueRingWraps.reset();
        this.terrainMeshTaskCommandUploads.reset();
        this.terrainMeshTaskCommandReservations.reset();
        this.terrainMeshTaskCommandDrops.reset();
        this.terrainMeshTaskCommandDeferrals.reset();
        this.terrainMeshTaskCommandRingWraps.reset();
        this.lastVisibleMeshletUpload = VisibleMeshletStats.empty();
        this.lastWorkQueueUpload = TerrainWorkQueueStats.empty();
        this.lastMeshTaskCommandUpload = MeshTaskCommandStats.empty();
        this.visibleMeshletCursor = 0;
        this.workQueueCursor = 0;
        this.meshTaskCommandCursor = 0;
        this.terrainDataDirty.set(false);
        closeLastTerrainReadFence();
        this.device = null;
        this.lastCapacityGrowthReason = "";
    }

    private void retireResources(GpuResources resources) {
        if (!resources.allocated()) {
            return;
        }
        resources.close();
        this.retiredResources.add(resources);
    }

    private void destroyRetiredResourcesNow() {
        for (GpuResources resources : this.retiredResources) {
            resources.destroyNow();
        }
        this.retiredResources.clear();
    }

    private synchronized GpuResourceRetirementStats retiredResourceStats() {
        GpuResourceRetirementStats stats = GpuResourceRetirementStats.empty();
        for (GpuResources resources : this.retiredResources) {
            stats = stats.plus(resources.retirementStats());
        }
        return stats;
    }

    public void recordTextureBinding(String name, GpuTextureView view, GpuSampler sampler) {
        if (!(view instanceof VulkanGpuTextureView vulkanView) || !(sampler instanceof VulkanGpuSampler vulkanSampler)) {
            this.textureBindings.put(name, TextureBinding.unavailable(name, view, sampler));
            return;
        }

        this.textureBindings.put(name, new TextureBinding(name, vulkanView.vkImageView(), vulkanSampler.vkSampler(), view.getWidth(0), view.getHeight(0), view.baseMipLevel(), view.mipLevels(), view.isClosed(), false));
        writeTerrainMaterialRecords(this.resources, this.textureBindings.get("Sampler0"), this.textureBindings.get("Sampler2"));
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

    public synchronized void markTerrainReadInCurrentSubmit() {
        VulkanDevice activeDevice = this.device;
        if (activeDevice == null) {
            return;
        }
        closeLastTerrainReadFence();
        this.lastTerrainReadFence = activeDevice.createCommandEncoder().createFence();
    }

    public synchronized void uploadDirtyTerrainData() {
        if (!this.terrainDataDirty.compareAndSet(true, false)) {
            return;
        }
        GpuResources activeResources = this.resources;
        if (!activeResources.readyForTerrainMetadata()) {
            this.terrainDataDirty.set(true);
            return;
        }

        if (!waitForTerrainUploadSafety()) {
            this.terrainDataDirty.set(true);
            return;
        }

        long startedNanos = System.nanoTime();
        UploadStats stats = SectionMeshletStore.writeMetadataSnapshot(activeResources.sectionMetadata().mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN), activeResources.meshletHeaders().mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN), activeResources.meshletVertices(), activeResources.meshletIndices(), this.layout.sectionCapacity, this.layout.meshletCapacity, this.uploadStats);
        activeResources.sectionMetadata().flush();
        activeResources.meshletHeaders().flush();
        activeResources.meshletVertices().flush();
        activeResources.meshletIndices().flush();
        this.uploadStats = stats.withCpuNanos(System.nanoTime() - startedNanos);
        growTerrainResourcesIfNeeded(this.uploadStats);
    }

    private boolean waitForTerrainUploadSafety() {
        if (!waitForTerrainReadFence()) {
            return false;
        }
        if (!TerrainRendererDebugConfig.WAIT_IDLE_BEFORE_TERRAIN_UPLOAD) {
            return true;
        }
        VulkanDevice activeDevice = this.device;
        if (activeDevice == null) {
            return true;
        }
        VulkanUtils.crashIfFailure(activeDevice, VK12.vkDeviceWaitIdle(activeDevice.vkDevice()), "Failed to wait for Vulkan Improvement terrain upload safety");
        this.terrainUploadDeviceIdleWaits.increment();
        return true;
    }

    private boolean waitForTerrainReadFence() {
        GpuFence fence = this.lastTerrainReadFence;
        if (fence == null) {
            return true;
        }
        try {
            if (!fence.awaitCompletion(5000L)) {
                this.terrainUploadFenceDeferrals.increment();
                return false;
            }
            this.terrainUploadFenceWaits.increment();
            if (this.lastTerrainReadFence == fence) {
                this.lastTerrainReadFence = null;
            }
            fence.close();
            return true;
        } catch (IllegalStateException ex) {
            this.terrainUploadFenceDeferrals.increment();
            return false;
        } catch (RuntimeException ex) {
            this.terrainUploadFenceFailures.increment();
            throw ex;
        }
    }

    private void closeLastTerrainReadFence() {
        GpuFence fence = this.lastTerrainReadFence;
        this.lastTerrainReadFence = null;
        if (fence != null) {
            fence.close();
        }
    }

    private void growTerrainResourcesIfNeeded(UploadStats stats) {
        if (!stats.droppedAny()) {
            return;
        }
        VulkanDevice activeDevice = this.device;
        if (activeDevice == null) {
            this.terrainDataDirty.set(true);
            return;
        }
        Layout grownLayout = this.layout.growFor(stats);
        if (grownLayout.equals(this.layout)) {
            this.terrainDataDirty.set(true);
            return;
        }

        this.lastCapacityGrowthReason = "droppedSections=" + stats.sectionsDropped + ", droppedMeshlets=" + stats.meshletsDropped + ", requiredVertexBytes=" + stats.requiredVertexBytes() + ", requiredIndexBytes=" + stats.requiredIndexBytes();
        retireResources(this.resources);
        this.layout = grownLayout;
        this.resources = GpuResources.create(activeDevice, grownLayout);
        this.resources.clearDebugCounters();
        this.resources.clearWorkQueueCounters();
        this.uploadStats = UploadStats.empty();
        SectionMeshletStore.clearGpuMeshletRanges();
        this.workQueueCursor = 0;
        this.meshTaskCommandCursor = 0;
        this.terrainCapacityGrowths.increment();
        this.terrainDataDirty.set(true);
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

    public long visibleMeshletListAddress() {
        TerrainGpuBuffer buffer = this.resources.visibleMeshlets();
        return buffer == null ? 0L : buffer.deviceAddress();
    }

    public long terrainWorkQueueAddress() {
        TerrainGpuBuffer buffer = this.resources.workQueue();
        return buffer == null ? 0L : buffer.deviceAddress();
    }

    public long terrainMeshTaskCommandAddress() {
        TerrainGpuBuffer buffer = this.resources.meshTaskCommands();
        return buffer == null ? 0L : buffer.deviceAddress();
    }

    public long terrainDescriptorBufferAddress() {
        TerrainGpuBuffer buffer = this.resources.resourceDescriptorBuffer();
        return buffer == null ? 0L : buffer.deviceAddress();
    }

    public long materialTableAddress() {
        TerrainGpuBuffer buffer = this.resources.materialTable();
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
        writeTerrainMaterialRecords(activeResources, blockAtlas, lightmap);
        this.descriptorBufferWrites.increment();
        return true;
    }

    private void writeTerrainMaterialRecords(GpuResources activeResources, TextureBinding blockAtlas, TextureBinding lightmap) {
        TerrainGpuBuffer materialTable = activeResources.materialTable();
        if (materialTable == null || !materialTable.hostVisible() || materialTable.size() < TerrainGpuLayout.MATERIAL_RECORD_STRIDE) {
            this.materialTableMissing.increment();
            return;
        }

        ByteBuffer materials = materialTable.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        TerrainMaterialClassifier.writeTerrainLayerRecords(materials, textureInfo(blockAtlas), textureInfo(lightmap));
        materialTable.flush();
        this.materialTableWrites.increment();
    }

    public synchronized VisibleMeshletUpload writeVisibleMeshletList(List<SectionMeshletStore.MeshletRange> ranges) {
        int requiredRecords = visibleMeshletRecordCount(ranges);
        if (requiredRecords <= 0) {
            TerrainVisibleMeshletRing.Allocation allocation = new TerrainVisibleMeshletRing(visibleMeshletRecordCapacity(), this.visibleMeshletCursor).allocate(requiredRecords, true);
            this.lastVisibleMeshletUpload = VisibleMeshletStats.fromAllocation(0, 0, allocation);
            return VisibleMeshletUpload.unavailable(0);
        }

        GpuResources activeResources = this.resources;
        TerrainGpuBuffer visibleMeshlets = activeResources.visibleMeshlets();
        if (visibleMeshlets == null || visibleMeshlets.deviceAddress() == 0L || !visibleMeshlets.hostVisible()) {
            TerrainVisibleMeshletRing.Allocation allocation = new TerrainVisibleMeshletRing(0, 0).allocate(requiredRecords, true);
            this.lastVisibleMeshletUpload = VisibleMeshletStats.fromAllocation(0, requiredRecords, allocation);
            this.visibleMeshletRecordsDropped.add(requiredRecords);
            return VisibleMeshletUpload.unavailable(requiredRecords);
        }

        int capacity = visibleMeshletRecordCapacity(visibleMeshlets);
        TerrainVisibleMeshletRing ring = new TerrainVisibleMeshletRing(capacity, this.visibleMeshletCursor);
        TerrainVisibleMeshletRing.Allocation allocation = ring.allocate(requiredRecords, false);
        if (allocation.wrapBlocked()) {
            if (!waitForTerrainReadFence()) {
                this.visibleMeshletListDeferrals.increment();
                this.lastVisibleMeshletUpload = VisibleMeshletStats.fromAllocation(0, 0, allocation);
                return VisibleMeshletUpload.unavailable(requiredRecords);
            }
            allocation = ring.allocate(requiredRecords, true);
        }
        if (!allocation.ready()) {
            this.lastVisibleMeshletUpload = VisibleMeshletStats.fromAllocation(0, requiredRecords, allocation);
            this.visibleMeshletRecordsDropped.add(requiredRecords);
            return VisibleMeshletUpload.unavailable(requiredRecords);
        }

        int firstRecord = allocation.offset();
        this.visibleMeshletCursor = allocation.nextOffset();
        if (allocation.wrapped()) {
            this.visibleMeshletRingWraps.increment();
        }
        ByteBuffer visibleRecords = visibleMeshlets.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        visibleRecords.position(firstRecord * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
        for (SectionMeshletStore.MeshletRange range : ranges) {
            float visibility = Math.clamp(range.visibility(), 0.0F, 1.0F);
            for (int i = 0; i < range.count(); i++) {
                visibleRecords.putInt(range.offset() + i);
                visibleRecords.putFloat(visibility);
            }
        }
        visibleMeshlets.flush();
        this.visibleMeshletListUploads.increment();
        this.visibleMeshletRecordsUploaded.add(requiredRecords);
        this.lastVisibleMeshletUpload = VisibleMeshletStats.fromAllocation(requiredRecords, 0, allocation);
        return new VisibleMeshletUpload(firstRecord, requiredRecords, visibleMeshlets.deviceAddress(), 0, true);
    }

    public synchronized TerrainWorkQueueUpload writeLayerWorkQueue(int meshletOffset, int layerOrdinal, int requestedRecords) {
        if (requestedRecords <= 0) {
            this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(0, workQueueRecordCapacity(), this.workQueueCursor, "no terrain work queue records requested", false);
            return TerrainWorkQueueUpload.unavailable(0);
        }

        TerrainGpuBuffer workQueue = this.resources.workQueue();
        if (workQueue == null || workQueue.deviceAddress() == 0L || !workQueue.hostVisible()) {
            this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(requestedRecords, 0, this.workQueueCursor, "terrain work queue buffer unavailable", false);
            this.terrainWorkQueueRecordsDropped.add(requestedRecords);
            return TerrainWorkQueueUpload.unavailable(requestedRecords);
        }

        int capacity = workQueueRecordCapacity(workQueue);
        if (requestedRecords > capacity) {
            this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(requestedRecords, capacity, this.workQueueCursor, "terrain work queue records exceed capacity", false);
            this.terrainWorkQueueRecordsDropped.add(requestedRecords);
            return TerrainWorkQueueUpload.unavailable(requestedRecords);
        }

        boolean wrapped = false;
        if (this.workQueueCursor + requestedRecords > capacity) {
            if (!waitForTerrainReadFence()) {
                this.terrainWorkQueueDeferrals.increment();
                this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(requestedRecords, capacity, this.workQueueCursor, "terrain work queue wrap requires completed terrain read fence", true);
                return TerrainWorkQueueUpload.unavailable(requestedRecords);
            }
            this.workQueueCursor = 0;
            wrapped = true;
        }

        int firstRecord = this.workQueueCursor;
        this.workQueueCursor += requestedRecords;
        ByteBuffer queue = workQueue.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        queue.position(0);
        queue.putInt(requestedRecords);
        queue.putInt(0);
        queue.putInt(0);
        queue.putInt(0);
        queue.position((int) TerrainWorkQueueLayout.recordOffset(firstRecord));
        for (int i = 0; i < requestedRecords; i++) {
            queue.putInt(meshletOffset + i);
            queue.putInt(layerOrdinal);
            queue.putInt(0);
            queue.putInt(0);
        }
        workQueue.flush();
        this.terrainWorkQueueUploads.increment();
        this.terrainWorkQueueRecordsUploaded.add(requestedRecords);
        if (wrapped) {
            this.terrainWorkQueueRingWraps.increment();
        }
        this.lastWorkQueueUpload = new TerrainWorkQueueStats(requestedRecords, 0, firstRecord, capacity, this.workQueueCursor, wrapped, false, "");
        return new TerrainWorkQueueUpload(firstRecord, requestedRecords, workQueue.deviceAddress(), 0, true);
    }

    public synchronized TerrainWorkQueueUpload writeVisibleWorkQueue(List<SectionMeshletStore.MeshletRange> ranges, int layerOrdinal) {
        int requiredRecords = visibleMeshletRecordCount(ranges);
        if (requiredRecords <= 0) {
            this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(0, workQueueRecordCapacity(), this.workQueueCursor, "no visible terrain work queue records requested", false);
            return TerrainWorkQueueUpload.unavailable(0);
        }

        TerrainGpuBuffer workQueue = this.resources.workQueue();
        if (workQueue == null || workQueue.deviceAddress() == 0L || !workQueue.hostVisible()) {
            this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(requiredRecords, 0, this.workQueueCursor, "terrain work queue buffer unavailable", false);
            this.terrainWorkQueueRecordsDropped.add(requiredRecords);
            return TerrainWorkQueueUpload.unavailable(requiredRecords);
        }

        int capacity = workQueueRecordCapacity(workQueue);
        if (requiredRecords > capacity) {
            this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(requiredRecords, capacity, this.workQueueCursor, "visible terrain work queue records exceed capacity", false);
            this.terrainWorkQueueRecordsDropped.add(requiredRecords);
            return TerrainWorkQueueUpload.unavailable(requiredRecords);
        }

        boolean wrapped = false;
        if (this.workQueueCursor + requiredRecords > capacity) {
            if (!waitForTerrainReadFence()) {
                this.terrainWorkQueueDeferrals.increment();
                this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(requiredRecords, capacity, this.workQueueCursor, "visible terrain work queue wrap requires completed terrain read fence", true);
                return TerrainWorkQueueUpload.unavailable(requiredRecords);
            }
            this.workQueueCursor = 0;
            wrapped = true;
        }

        int firstRecord = this.workQueueCursor;
        this.workQueueCursor += requiredRecords;
        ByteBuffer queue = workQueue.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        queue.position(0);
        queue.putInt(requiredRecords);
        queue.putInt(0);
        queue.putInt(0);
        queue.putInt(0);
        queue.position((int) TerrainWorkQueueLayout.recordOffset(firstRecord));
        for (SectionMeshletStore.MeshletRange range : ranges) {
            for (int i = 0; i < range.count(); i++) {
                queue.putInt(range.offset() + i);
                queue.putInt(layerOrdinal);
                queue.putInt(0);
                queue.putInt(0);
            }
        }
        workQueue.flush();
        this.terrainWorkQueueUploads.increment();
        this.terrainWorkQueueRecordsUploaded.add(requiredRecords);
        if (wrapped) {
            this.terrainWorkQueueRingWraps.increment();
        }
        this.lastWorkQueueUpload = new TerrainWorkQueueStats(requiredRecords, 0, firstRecord, capacity, this.workQueueCursor, wrapped, false, "visible-draw-list");
        return new TerrainWorkQueueUpload(firstRecord, requiredRecords, workQueue.deviceAddress(), 0, true);
    }

    public synchronized TerrainMeshTaskCommandUpload writeMeshTaskCommand(int taskCount) {
        TerrainMeshTaskCommandUpload upload = allocateMeshTaskCommand(taskCount, false);
        if (!upload.ready()) {
            return upload;
        }

        TerrainGpuBuffer meshTaskCommands = this.resources.meshTaskCommands();
        ByteBuffer commands = meshTaskCommands.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        commands.position((int) upload.offsetBytes());
        commands.putInt(taskCount);
        commands.putInt(1);
        commands.putInt(1);
        meshTaskCommands.flush();
        this.terrainMeshTaskCommandUploads.increment();
        return upload;
    }

    public synchronized TerrainMeshTaskCommandUpload reserveMeshTaskCommand(int taskCount) {
        TerrainMeshTaskCommandUpload upload = allocateMeshTaskCommand(taskCount, true);
        if (upload.ready()) {
            this.terrainMeshTaskCommandReservations.increment();
        }
        return upload;
    }

    private TerrainMeshTaskCommandUpload allocateMeshTaskCommand(int taskCount, boolean gpuGenerated) {
        if (taskCount <= 0) {
            this.lastMeshTaskCommandUpload = MeshTaskCommandStats.unavailable(0, meshTaskCommandCapacity(), this.meshTaskCommandCursor, "no terrain mesh-task command requested", false, gpuGenerated);
            return TerrainMeshTaskCommandUpload.unavailable(0);
        }

        TerrainGpuBuffer meshTaskCommands = this.resources.meshTaskCommands();
        if (meshTaskCommands == null || meshTaskCommands.vkBuffer() == 0L || !meshTaskCommands.hostVisible()) {
            this.lastMeshTaskCommandUpload = MeshTaskCommandStats.unavailable(1, 0, this.meshTaskCommandCursor, "terrain mesh-task command buffer unavailable", false, gpuGenerated);
            this.terrainMeshTaskCommandDrops.increment();
            return TerrainMeshTaskCommandUpload.unavailable(1);
        }

        int capacity = meshTaskCommandCapacity(meshTaskCommands);
        if (capacity <= 0) {
            this.lastMeshTaskCommandUpload = MeshTaskCommandStats.unavailable(1, capacity, this.meshTaskCommandCursor, "terrain mesh-task command buffer has no capacity", false, gpuGenerated);
            this.terrainMeshTaskCommandDrops.increment();
            return TerrainMeshTaskCommandUpload.unavailable(1);
        }

        boolean wrapped = false;
        if (this.meshTaskCommandCursor + 1 > capacity) {
            if (!waitForTerrainReadFence()) {
                this.terrainMeshTaskCommandDeferrals.increment();
                this.lastMeshTaskCommandUpload = MeshTaskCommandStats.unavailable(1, capacity, this.meshTaskCommandCursor, "terrain mesh-task command wrap requires completed terrain read fence", true, gpuGenerated);
                return TerrainMeshTaskCommandUpload.unavailable(1);
            }
            this.meshTaskCommandCursor = 0;
            wrapped = true;
        }

        int commandIndex = this.meshTaskCommandCursor;
        this.meshTaskCommandCursor++;
        long offsetBytes = TerrainMeshTaskCommandLayout.commandOffset(commandIndex);
        if (wrapped) {
            this.terrainMeshTaskCommandRingWraps.increment();
        }
        this.lastMeshTaskCommandUpload = new MeshTaskCommandStats(1, 0, commandIndex, capacity, this.meshTaskCommandCursor, wrapped, false, "", gpuGenerated);
        return new TerrainMeshTaskCommandUpload(commandIndex, taskCount, meshTaskCommands.vkBuffer(), offsetBytes, TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, meshTaskCommands.deviceAddress() + offsetBytes, 0, true, gpuGenerated);
    }

    private int visibleMeshletRecordCapacity() {
        TerrainGpuBuffer visibleMeshlets = this.resources.visibleMeshlets();
        return visibleMeshlets == null ? 0 : visibleMeshletRecordCapacity(visibleMeshlets);
    }

    private int workQueueRecordCapacity() {
        TerrainGpuBuffer workQueue = this.resources.workQueue();
        return workQueue == null ? 0 : workQueueRecordCapacity(workQueue);
    }

    private int meshTaskCommandCapacity() {
        TerrainGpuBuffer meshTaskCommands = this.resources.meshTaskCommands();
        return meshTaskCommands == null ? 0 : meshTaskCommandCapacity(meshTaskCommands);
    }

    public Layout layout() {
        return this.layout;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = this.layout.asMap();
        map.put("resources", this.resources.asMap());
        map.put("retiredResources", retiredResourceStats().asMap());
        map.put("uploadStats", this.uploadStats.asMap());
        map.put("terrainDataDirty", this.terrainDataDirty.get());
        map.put("sectionMetadataAddress", sectionMetadataAddress());
        map.put("meshletHeaderAddress", meshletHeaderAddress());
        map.put("meshletVertexPayloadAddress", meshletVertexPayloadAddress());
        map.put("meshletIndexPayloadAddress", meshletIndexPayloadAddress());
        map.put("visibleMeshletListAddress", visibleMeshletListAddress());
        map.put("terrainWorkQueueAddress", terrainWorkQueueAddress());
        map.put("terrainMeshTaskCommandAddress", terrainMeshTaskCommandAddress());
        map.put("terrainDescriptorBufferAddress", terrainDescriptorBufferAddress());
        map.put("materialTableAddress", materialTableAddress());
        map.put("materialRecordLayout", GpuMaterialRecord.layoutMap());
        map.put("terrainMaterialClassification", TerrainMaterialClassifier.asMap());
        map.put("terrainDebugCounterAddress", terrainDebugCounterAddress());
        map.put("descriptorBufferWrites", this.descriptorBufferWrites.sum());
        map.put("descriptorBufferMissing", this.descriptorBufferMissing.sum());
        map.put("materialTableWrites", this.materialTableWrites.sum());
        map.put("materialTableMissing", this.materialTableMissing.sum());
        map.put("terrainCapacityGrowths", this.terrainCapacityGrowths.sum());
        map.put("waitIdleBeforeTerrainUpload", TerrainRendererDebugConfig.WAIT_IDLE_BEFORE_TERRAIN_UPLOAD);
        map.put("terrainUploadDeviceIdleWaits", this.terrainUploadDeviceIdleWaits.sum());
        map.put("terrainUploadFenceWaits", this.terrainUploadFenceWaits.sum());
        map.put("terrainUploadFenceDeferrals", this.terrainUploadFenceDeferrals.sum());
        map.put("terrainUploadFenceFailures", this.terrainUploadFenceFailures.sum());
        map.put("terrainReadFenceInFlight", this.lastTerrainReadFence != null);
        map.put("visibleMeshletListUploads", this.visibleMeshletListUploads.sum());
        map.put("visibleMeshletRecordsUploaded", this.visibleMeshletRecordsUploaded.sum());
        map.put("visibleMeshletRecordsDropped", this.visibleMeshletRecordsDropped.sum());
        map.put("visibleMeshletListDeferrals", this.visibleMeshletListDeferrals.sum());
        map.put("visibleMeshletRingWraps", this.visibleMeshletRingWraps.sum());
        map.put("lastVisibleMeshletUpload", this.lastVisibleMeshletUpload.asMap());
        map.put("terrainWorkQueueUploads", this.terrainWorkQueueUploads.sum());
        map.put("terrainWorkQueueRecordsUploaded", this.terrainWorkQueueRecordsUploaded.sum());
        map.put("terrainWorkQueueRecordsDropped", this.terrainWorkQueueRecordsDropped.sum());
        map.put("terrainWorkQueueDeferrals", this.terrainWorkQueueDeferrals.sum());
        map.put("terrainWorkQueueRingWraps", this.terrainWorkQueueRingWraps.sum());
        map.put("lastTerrainWorkQueueUpload", this.lastWorkQueueUpload.asMap());
        map.put("terrainWorkQueueLayout", TerrainWorkQueueLayout.asMap(this.layout.meshletCapacity));
        map.put("terrainMeshTaskCommandUploads", this.terrainMeshTaskCommandUploads.sum());
        map.put("terrainMeshTaskCommandReservations", this.terrainMeshTaskCommandReservations.sum());
        map.put("terrainMeshTaskCommandDrops", this.terrainMeshTaskCommandDrops.sum());
        map.put("terrainMeshTaskCommandDeferrals", this.terrainMeshTaskCommandDeferrals.sum());
        map.put("terrainMeshTaskCommandRingWraps", this.terrainMeshTaskCommandRingWraps.sum());
        map.put("lastTerrainMeshTaskCommandUpload", this.lastMeshTaskCommandUpload.asMap());
        map.put("terrainMeshTaskCommandLayout", TerrainMeshTaskCommandLayout.asMap());
        map.put("lastCapacityGrowthReason", this.lastCapacityGrowthReason);
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
        BLOCK_ATLAS("blockAtlas", "sampledImage"), LIGHTMAP("lightmap", "sampledImage"), GLOBAL_UNIFORMS("globalUniforms", "uniformBuffer"), SECTION_METADATA("sectionMetadata", "storageBuffer"), MESHLET_HEADERS("meshletHeaders", "storageBuffer"), MESHLET_VERTICES("meshletVertices", "storageBuffer"), MESHLET_INDICES("meshletIndices", "storageBuffer"), VISIBLE_MESHLETS("visibleMeshlets", "storageBuffer"), TERRAIN_WORK_QUEUE("terrainWorkQueue", "storageBuffer"), MESH_TASK_COMMANDS("meshTaskCommands", "storageBuffer"), MATERIAL_TABLE("materialTable", "storageBuffer"), DEBUG_COUNTERS("debugCounters", "storageBuffer");

        private final String id;
        private final String descriptorType;

        Slot(String id, String descriptorType) {
            this.id = id;
            this.descriptorType = descriptorType;
        }
    }

    public record VisibleMeshletUpload(int offset, int count, long address, int dropped, boolean ready) {
        static VisibleMeshletUpload unavailable(int dropped) {
            return new VisibleMeshletUpload(0, 0, 0L, dropped, false);
        }
    }

    public record TerrainWorkQueueUpload(int offset, int count, long address, int dropped, boolean ready) {
        static TerrainWorkQueueUpload unavailable(int dropped) {
            return new TerrainWorkQueueUpload(0, 0, 0L, dropped, false);
        }
    }

    public record TerrainMeshTaskCommandUpload(int commandIndex, int taskCount, long vkBuffer, long offsetBytes,
                                               int strideBytes, long address, int dropped, boolean ready,
                                               boolean gpuGenerated) {
        static TerrainMeshTaskCommandUpload unavailable(int dropped) {
            return new TerrainMeshTaskCommandUpload(0, 0, 0L, 0L, 0, 0L, dropped, false, false);
        }
    }

    private record VisibleMeshletStats(int uploaded, int dropped, int offset, int capacity, int nextOffset,
                                       boolean wrapped, boolean wrapBlocked, String reason) {
        static VisibleMeshletStats empty() {
            return new VisibleMeshletStats(0, 0, 0, 0, 0, false, false, "");
        }

        static VisibleMeshletStats fromAllocation(int uploaded, int dropped, TerrainVisibleMeshletRing.Allocation allocation) {
            return new VisibleMeshletStats(uploaded, dropped, allocation.offset(), allocation.capacity(), allocation.nextOffset(), allocation.wrapped(), allocation.wrapBlocked(), allocation.reason());
        }

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uploaded", this.uploaded);
            map.put("dropped", this.dropped);
            map.put("offset", this.offset);
            map.put("capacity", this.capacity);
            map.put("nextOffset", this.nextOffset);
            map.put("wrapped", this.wrapped);
            map.put("wrapBlocked", this.wrapBlocked);
            map.put("reason", this.reason);
            map.put("recordStride", TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
            return map;
        }
    }

    private record TerrainWorkQueueStats(int uploaded, int dropped, int offset, int capacity, int nextOffset,
                                         boolean wrapped, boolean wrapBlocked, String reason) {
        static TerrainWorkQueueStats empty() {
            return new TerrainWorkQueueStats(0, 0, 0, 0, 0, false, false, "");
        }

        static TerrainWorkQueueStats unavailable(int dropped, int capacity, int nextOffset, String reason, boolean wrapBlocked) {
            return new TerrainWorkQueueStats(0, dropped, 0, capacity, nextOffset, false, wrapBlocked, reason);
        }

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uploaded", this.uploaded);
            map.put("dropped", this.dropped);
            map.put("offset", this.offset);
            map.put("capacity", this.capacity);
            map.put("nextOffset", this.nextOffset);
            map.put("wrapped", this.wrapped);
            map.put("wrapBlocked", this.wrapBlocked);
            map.put("reason", this.reason);
            map.put("recordStride", TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE);
            return map;
        }
    }

    private record MeshTaskCommandStats(int uploaded, int dropped, int offset, int capacity, int nextOffset,
                                        boolean wrapped, boolean wrapBlocked, String reason, boolean gpuGenerated) {
        static MeshTaskCommandStats empty() {
            return new MeshTaskCommandStats(0, 0, 0, 0, 0, false, false, "", false);
        }

        static MeshTaskCommandStats unavailable(int dropped, int capacity, int nextOffset, String reason, boolean wrapBlocked, boolean gpuGenerated) {
            return new MeshTaskCommandStats(0, dropped, 0, capacity, nextOffset, false, wrapBlocked, reason, gpuGenerated);
        }

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("uploaded", this.uploaded);
            map.put("dropped", this.dropped);
            map.put("offset", this.offset);
            map.put("capacity", this.capacity);
            map.put("nextOffset", this.nextOffset);
            map.put("wrapped", this.wrapped);
            map.put("wrapBlocked", this.wrapBlocked);
            map.put("reason", this.reason);
            map.put("gpuGenerated", this.gpuGenerated);
            map.put("commandStride", TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE);
            return map;
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
                         long visibleMeshletBytes, long workQueueBytes, long meshTaskCommandBytes,
                         long materialTableBytes, long samplerHeapBytes, long resourceHeapBytes,
                         long samplerDescriptorBufferBytes, long resourceDescriptorBufferBytes,
                         long sectionMetadataBytes, long meshletHeaderBytes, long samplerDescriptorBytes,
                         long imageDescriptorBytes, long bufferDescriptorBytes, long descriptorBufferAlignment,
                         long descriptorBufferSamplerDescriptorBytes, long descriptorBufferSampledImageDescriptorBytes,
                         long descriptorBufferUniformBufferDescriptorBytes,
                         long descriptorBufferStorageBufferDescriptorBytes, Map<String, String> slots) {
        // Java ByteBuffer and LWJGL MemoryUtil.memByteBuffer cannot wrap segments >= Integer.MAX_VALUE.
        // Cap host-mapped GPU buffers at 1 GiB which is a safe power-of-two well under that limit.
        private static final long MAX_HOST_MAPPED_BUFFER_BYTES = 1L << 30;
        static Layout unconfigured() {
            return new Layout(false, false, false, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, Map.of());
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
            long sectionMetadataBytes = (long) sectionCapacity * TerrainGpuLayout.SECTION_METADATA_STRIDE;
            long meshletHeaderBytes = (long) meshletCapacity * TerrainGpuLayout.MESHLET_HEADER_STRIDE;
            long visibleMeshletBytes = (long) meshletCapacity * TerrainGpuLayout.VISIBLE_MESHLET_RING_MULTIPLIER * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE;
            long workQueueBytes = TerrainWorkQueueLayout.bytesForCapacity(meshletCapacity);
            long meshTaskCommandBytes = TerrainMeshTaskCommandLayout.bytesForCapacity();
            long materialTableBytes = (long) TerrainGpuLayout.MATERIAL_TABLE_CAPACITY * TerrainGpuLayout.MATERIAL_RECORD_STRIDE;
            return new Layout(true, capabilities.descriptorHeapExtension(), capabilities.descriptorBufferEnabled(), sectionCapacity, meshletCapacity, vertexPayloadBytes, indexPayloadBytes, visibleMeshletBytes, workQueueBytes, meshTaskCommandBytes, materialTableBytes, samplerHeapBytes, resourceHeapBytes, samplerDescriptorBufferBytes, resourceDescriptorBufferBytes, sectionMetadataBytes, meshletHeaderBytes, samplerDescriptorBytes, imageDescriptorBytes, bufferDescriptorBytes, alignment, descriptorBufferSamplerBytes, descriptorBufferSampledImageBytes, descriptorBufferUniformBytes, descriptorBufferStorageBytes, Map.copyOf(slots));
        }

        private static int growIntCapacity(int current, int required) {
            if (required <= current) {
                return current;
            }
            long headroom = required + Math.max(1024L, required / 4L);
            long doubled = Math.max(1L, current) * 2L;
            return (int) Math.min(Integer.MAX_VALUE, nextPowerOfTwo(Math.max(headroom, doubled)));
        }

        private static long growLongCapacity(long current, long required) {
            if (required <= current) {
                return current;
            }
            long headroom = required + Math.max(1L << 20, required / 4L);
            long doubled = Math.max(1L, current) * 2L;
            return Math.min(MAX_HOST_MAPPED_BUFFER_BYTES, nextPowerOfTwo(Math.max(headroom, doubled)));
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

        Layout growFor(UploadStats stats) {
            int grownSectionCapacity = growIntCapacity(this.sectionCapacity, stats.requiredSectionLayers());
            int grownMeshletCapacity = growIntCapacity(this.meshletCapacity, stats.requiredMeshlets());
            long grownVertexPayloadBytes = growLongCapacity(this.vertexPayloadBytes, stats.requiredVertexBytes());
            long grownIndexPayloadBytes = growLongCapacity(this.indexPayloadBytes, stats.requiredIndexBytes());
            if (grownSectionCapacity == this.sectionCapacity && grownMeshletCapacity == this.meshletCapacity && grownVertexPayloadBytes == this.vertexPayloadBytes && grownIndexPayloadBytes == this.indexPayloadBytes) {
                return this;
            }

            return new Layout(this.configured, this.descriptorHeapEnabled, this.descriptorBufferEnabled, grownSectionCapacity, grownMeshletCapacity, grownVertexPayloadBytes, grownIndexPayloadBytes, (long) grownMeshletCapacity * TerrainGpuLayout.VISIBLE_MESHLET_RING_MULTIPLIER * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE, TerrainWorkQueueLayout.bytesForCapacity(grownMeshletCapacity), this.meshTaskCommandBytes, this.materialTableBytes, this.samplerHeapBytes, this.resourceHeapBytes, this.samplerDescriptorBufferBytes, this.resourceDescriptorBufferBytes, (long) grownSectionCapacity * TerrainGpuLayout.SECTION_METADATA_STRIDE, (long) grownMeshletCapacity * TerrainGpuLayout.MESHLET_HEADER_STRIDE, this.samplerDescriptorBytes, this.imageDescriptorBytes, this.bufferDescriptorBytes, this.descriptorBufferAlignment, this.descriptorBufferSamplerDescriptorBytes, this.descriptorBufferSampledImageDescriptorBytes, this.descriptorBufferUniformBufferDescriptorBytes, this.descriptorBufferStorageBufferDescriptorBytes, this.slots);
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

    public record UploadStats(int sectionsUploaded, int layersUploaded, int meshletsUploaded, int sectionsDropped,
                              int meshletsDropped, long vertexBytesUploaded, long indexBytesUploaded,
                              long vertexBytesDropped, long indexBytesDropped, int[] meshletOffsetsByLayer,
                              int[] meshletCountsByLayer, int[] customIndexMeshletCountsByLayer, long uploadCount,
                              long lastUploadCpuNanos, long totalUploadCpuNanos) {
        public UploadStats {
            meshletOffsetsByLayer = meshletOffsetsByLayer.clone();
            meshletCountsByLayer = meshletCountsByLayer.clone();
            customIndexMeshletCountsByLayer = customIndexMeshletCountsByLayer.clone();
        }

        static UploadStats empty() {
            return new UploadStats(0, 0, 0, 0, 0, 0L, 0L, 0L, 0L, emptyLayerCounters(), emptyLayerCounters(), emptyLayerCounters(), 0L, 0L, 0L);
        }

        private static int[] emptyLayerCounters() {
            return new int[ChunkSectionLayer.values().length];
        }

        private static long nanosToMicros(long nanos) {
            return nanos / 1_000L;
        }

        UploadStats nextUpload(int sections, int layers, int meshlets, int droppedSections, int droppedMeshlets, long vertexBytesUploaded, long indexBytesUploaded, long vertexBytesDropped, long indexBytesDropped, int[] meshletOffsetsByLayer, int[] meshletCountsByLayer, int[] customIndexMeshletCountsByLayer) {
            return new UploadStats(sections, layers, meshlets, droppedSections, droppedMeshlets, vertexBytesUploaded, indexBytesUploaded, vertexBytesDropped, indexBytesDropped, meshletOffsetsByLayer, meshletCountsByLayer, customIndexMeshletCountsByLayer, this.uploadCount + 1L, 0L, this.totalUploadCpuNanos);
        }

        UploadStats withCpuNanos(long cpuNanos) {
            long measuredNanos = Math.max(cpuNanos, 0L);
            return new UploadStats(this.sectionsUploaded, this.layersUploaded, this.meshletsUploaded, this.sectionsDropped, this.meshletsDropped, this.vertexBytesUploaded, this.indexBytesUploaded, this.vertexBytesDropped, this.indexBytesDropped, this.meshletOffsetsByLayer, this.meshletCountsByLayer, this.customIndexMeshletCountsByLayer, this.uploadCount, measuredNanos, this.totalUploadCpuNanos + measuredNanos);
        }

        boolean droppedAny() {
            return this.sectionsDropped > 0 || this.meshletsDropped > 0 || this.vertexBytesDropped > 0L || this.indexBytesDropped > 0L;
        }

        int requiredSectionLayers() {
            return this.layersUploaded + this.sectionsDropped;
        }

        int requiredMeshlets() {
            long required = (long) this.meshletsUploaded + this.meshletsDropped;
            return (int) Math.min(Integer.MAX_VALUE, required);
        }

        long requiredVertexBytes() {
            return this.vertexBytesUploaded + this.vertexBytesDropped;
        }

        long requiredIndexBytes() {
            return this.indexBytesUploaded + this.indexBytesDropped;
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
            map.put("lastUploadCpuMicros", nanosToMicros(this.lastUploadCpuNanos));
            map.put("totalUploadCpuMicros", nanosToMicros(this.totalUploadCpuNanos));
            map.put("averageUploadCpuMicros", this.uploadCount == 0L ? 0L : nanosToMicros(this.totalUploadCpuNanos / this.uploadCount));
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
                                TerrainGpuBuffer visibleMeshlets, TerrainGpuBuffer workQueue,
                                TerrainGpuBuffer meshTaskCommands, TerrainGpuBuffer materialTable,
                                TerrainGpuBuffer debugCounters) {
        static GpuResources empty() {
            return new GpuResources(null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        static GpuResources create(VulkanDevice device, Layout layout) {
            if (!layout.configured) {
                return empty();
            }
            int descriptorHeapUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorHeap.VK_BUFFER_USAGE_DESCRIPTOR_HEAP_BIT_EXT;
            int samplerDescriptorBufferUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
            int resourceDescriptorBufferUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | EXTDescriptorBuffer.VK_BUFFER_USAGE_RESOURCE_DESCRIPTOR_BUFFER_BIT_EXT | EXTDescriptorBuffer.VK_BUFFER_USAGE_SAMPLER_DESCRIPTOR_BUFFER_BIT_EXT;
            int storageUsage = VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
            int indirectStorageUsage = storageUsage | VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
            TerrainGpuBuffer samplerHeap = layout.descriptorHeapEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Sampler Descriptor Heap", layout.samplerHeapBytes, descriptorHeapUsage, true) : null;
            TerrainGpuBuffer resourceHeap = layout.descriptorHeapEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Resource Descriptor Heap", layout.resourceHeapBytes, descriptorHeapUsage, true) : null;
            TerrainGpuBuffer samplerDescriptorBuffer = layout.descriptorBufferEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Sampler Descriptor Buffer", layout.samplerDescriptorBufferBytes, samplerDescriptorBufferUsage, true) : null;
            TerrainGpuBuffer resourceDescriptorBuffer = layout.descriptorBufferEnabled ? new TerrainGpuBuffer(device, "VIM Terrain Resource Descriptor Buffer", layout.resourceDescriptorBufferBytes, resourceDescriptorBufferUsage, true) : null;
            TerrainGpuBuffer sectionMetadata = new TerrainGpuBuffer(device, "VIM Terrain Section Metadata", layout.sectionMetadataBytes, storageUsage, true);
            TerrainGpuBuffer meshletHeaders = new TerrainGpuBuffer(device, "VIM Terrain Meshlet Headers", layout.meshletHeaderBytes, storageUsage, true);
            TerrainGpuBuffer meshletVertices = new TerrainGpuBuffer(device, "VIM Terrain Meshlet Vertex Payload", layout.vertexPayloadBytes, storageUsage, true);
            TerrainGpuBuffer meshletIndices = new TerrainGpuBuffer(device, "VIM Terrain Meshlet Index Payload", layout.indexPayloadBytes, storageUsage, true);
            TerrainGpuBuffer visibleMeshlets = new TerrainGpuBuffer(device, "VIM Terrain Visible Meshlet List", layout.visibleMeshletBytes, storageUsage, true);
            TerrainGpuBuffer workQueue = new TerrainGpuBuffer(device, "VIM Terrain Work Queue", layout.workQueueBytes, storageUsage, true);
            TerrainGpuBuffer meshTaskCommands = new TerrainGpuBuffer(device, "VIM Terrain Mesh Task Commands", layout.meshTaskCommandBytes, indirectStorageUsage, true);
            TerrainGpuBuffer materialTable = new TerrainGpuBuffer(device, "VIM Terrain Material Table", layout.materialTableBytes, storageUsage, true);
            TerrainGpuBuffer debugCounters = new TerrainGpuBuffer(device, "VIM Terrain Debug Counters", TerrainGpuLayout.DEBUG_COUNTER_BYTES, storageUsage, true);
            return new GpuResources(samplerHeap, resourceHeap, samplerDescriptorBuffer, resourceDescriptorBuffer, sectionMetadata, meshletHeaders, meshletVertices, meshletIndices, visibleMeshlets, workQueue, meshTaskCommands, materialTable, debugCounters);
        }

        private static Map<String, Object> bufferMap(TerrainGpuBuffer buffer) {
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

        private static void close(TerrainGpuBuffer buffer) {
            if (buffer != null) {
                buffer.close();
            }
        }

        private static void destroyNow(TerrainGpuBuffer buffer) {
            if (buffer != null) {
                buffer.destroyNow();
            }
        }

        private static int bufferCount(TerrainGpuBuffer buffer) {
            return buffer == null ? 0 : 1;
        }

        private static long bufferBytes(TerrainGpuBuffer buffer) {
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

        boolean readyForTerrainMetadata() {
            return this.sectionMetadata != null && this.meshletHeaders != null && this.meshletVertices != null && this.meshletIndices != null;
        }

        void clearDebugCounters() {
            if (this.debugCounters != null) {
                ByteBuffer counters = this.debugCounters.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
                counters.clear();
                for (int i = 0; i < TerrainGpuLayout.DEBUG_COUNTER_BYTES / Integer.BYTES; i++) {
                    counters.putInt(0);
                }
                this.debugCounters.flush();
            }
        }

        void clearWorkQueueCounters() {
            if (this.workQueue != null) {
                ByteBuffer counters = this.workQueue.mappedByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
                counters.clear();
                int counterInts = TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES / Integer.BYTES;
                for (int i = 0; i < counterInts; i++) {
                    counters.putInt(0);
                }
                this.workQueue.flush();
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
