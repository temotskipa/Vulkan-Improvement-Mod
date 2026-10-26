package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.*;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanCapabilitySnapshot;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBuffer;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBufferPlacement;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDatabase;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

public final class DescriptorHeapTerrainResources {
    /**
     * The images improved fog reads, under the names Minecraft's terrain shaders give them.
     */
    public static final String SKY_IMAGE = "SkySampler";
    public static final String CLOUDS_IMAGE = "CloudsSampler";
    public static final String CLOUDS_DEPTH_IMAGE = "CloudsDepthSampler";
    private static final DescriptorHeapTerrainResources INSTANCE = new DescriptorHeapTerrainResources();
    /**
     * How much page data one frame copies to the GPU at most, so that loading a world spreads its
     * uploads over frames. One page is always copied, however large.
     */
    private static final long PAGE_UPLOAD_BYTES_PER_FRAME = 32L << 20;
    private static final int PAGE_UPLOADS_PER_FRAME = 4096;
    private final AtomicBoolean terrainDataDirty = new AtomicBoolean();
    private final Map<String, TerrainTextureBinding> textureBindings = new ConcurrentHashMap<>();
    private final LongAdder descriptorBufferWrites = new LongAdder();
    private final LongAdder descriptorBufferMissing = new LongAdder();
    private final LongAdder materialTableWrites = new LongAdder();
    private final LongAdder materialTableMissing = new LongAdder();
    private final LongAdder visiblePageListUploads = new LongAdder();
    private final LongAdder gpuDrivenFrameUploads = new LongAdder();
    private final LongAdder visiblePageRecordsUploaded = new LongAdder();
    private final LongAdder pageUploads = new LongAdder();
    private final LongAdder pageUploadBytes = new LongAdder();
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
    private final List<TerrainGpuResources> retiredResources = new ArrayList<>();
    // The fence of the frame that started each ring's current lap. A ring starts over only once
    // that frame is done on the GPU; Minecraft keeps two frames in flight and a lap is far longer,
    // so that frame has finished long before and nothing waits.
    private final GpuFence[] ringLaps = new GpuFence[TerrainDescriptorRing.values().length];
    private volatile TerrainDescriptorHeapLayout layout = TerrainDescriptorHeapLayout.unconfigured();
    private volatile TerrainGpuResources resources = TerrainGpuResources.empty();
    private volatile TerrainUploadStats uploadStats = TerrainUploadStats.empty();
    private volatile VulkanDevice device;
    private volatile TerrainDescriptorWrite lastDescriptorWrite;
    private volatile TerrainVisibleMeshletStats lastVisibleMeshletUpload = TerrainVisibleMeshletStats.empty();
    private volatile TerrainWorkQueueStats lastWorkQueueUpload = TerrainWorkQueueStats.empty();
    private volatile TerrainMeshTaskCommandStats lastMeshTaskCommandUpload = TerrainMeshTaskCommandStats.empty();
    private int visibleMeshletCursor;
    private int workQueueCursor;
    private int meshTaskCommandCursor;
    private volatile String lastCapacityGrowthReason = "";
    
    private DescriptorHeapTerrainResources() {
    }
    
    public static DescriptorHeapTerrainResources get() {
        return INSTANCE;
    }
    
    private static void writeSampledImageDescriptor(VulkanDevice device, MemoryStack stack, MemorySegment descriptors, long offset, long size, TerrainTextureBinding binding) {
        VkDescriptorImageInfo imageInfo = VkDescriptorImageInfo.calloc(stack).imageView(binding.imageView()).imageLayout(VK10.VK_IMAGE_LAYOUT_GENERAL);
        VkDescriptorGetInfoEXT descriptorInfo = VkDescriptorGetInfoEXT.calloc(stack).sType$Default().type(VK10.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE);
        descriptorInfo.data().pSampledImage(imageInfo);
        EXTDescriptorBuffer.vkGetDescriptorEXT(device.vkDevice(), descriptorInfo, descriptorSlice(descriptors, offset, size));
    }
    
    private static void writeSamplerDescriptor(VulkanDevice device, MemoryStack stack, MemorySegment descriptors, long offset, long size, TerrainTextureBinding binding) {
        VkDescriptorGetInfoEXT descriptorInfo = VkDescriptorGetInfoEXT.calloc(stack).sType$Default().type(VK10.VK_DESCRIPTOR_TYPE_SAMPLER);
        descriptorInfo.data().pSampler(stack.longs(binding.sampler()));
        EXTDescriptorBuffer.vkGetDescriptorEXT(device.vkDevice(), descriptorInfo, descriptorSlice(descriptors, offset, size));
    }
    
    /**
     * Where a descriptor goes, as the buffer LWJGL writes it into.
     */
    private static ByteBuffer descriptorSlice(MemorySegment descriptors, long offset, long size) {
        if (offset < 0L || size <= 0L || offset + size > descriptors.byteSize()) {
            throw new IllegalArgumentException("Descriptor buffer write exceeds mapped terrain descriptor buffer: offset=" + offset + ", size=" + size + ", capacity=" + descriptors.byteSize());
        }
        return descriptors.asSlice(offset, size).asByteBuffer();
    }
    
    private static TextureInfo textureInfo(TerrainTextureBinding binding) {
        if (binding == null || binding.unavailable() || binding.closed()) {
            return TextureInfo.unavailable();
        }
        return new TextureInfo(true, binding.width(), binding.height(), binding.baseMipLevel(), binding.mipLevels());
    }
    
    private static int visibleMeshletRecordCapacity(DeviceBuffer visibleMeshlets) {
        return (int) Math.min(Integer.MAX_VALUE, visibleMeshlets.size() / TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
    }
    
    private static int workQueueRecordCapacity(DeviceBuffer workQueue) {
        long payloadBytes = Math.max(0L, workQueue.size() - TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES);
        return (int) Math.min(Integer.MAX_VALUE, payloadBytes / TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE);
    }
    
    private static int meshTaskCommandCapacity(DeviceBuffer meshTaskCommands) {
        return (int) Math.min(Integer.MAX_VALUE, meshTaskCommands.size() / TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE);
    }
    
    private static int visibleMeshletRecordCount(List<MeshletRange> ranges) {
        long count = 0L;
        for (MeshletRange range : ranges) {
            count += Math.max(range.count(), 0);
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }
    
    /**
     * Clears the page-table records the GPU must no longer draw, since the GPU-driven draw reads
     * every slot: the whole table after a reset, else the slots given back since the last upload.
     * Those exclude every slot written below, so only clearing the whole table needs a barrier
     * before the records are written.
     */
    private static void clearPageRecords(VkCommandBuffer commandBuffer, DeviceBuffer pageTable, ResidentPageTableChanges changes, MemoryStack stack) {
        if (changes.clearAll()) {
            VK10.vkCmdFillBuffer(commandBuffer, pageTable.vkBuffer(), 0L, VK10.VK_WHOLE_SIZE, 0);
            VulkanCommandEncoder.memoryBarrier(commandBuffer, stack);
            return;
        }
        int[] slots = changes.freedSlots();
        for (int first = 0, end; first < slots.length; first = end) {
            end = first + 1;
            while (end < slots.length && slots[end] == slots[end - 1] + 1) {
                end++;
            }
            VK10.vkCmdFillBuffer(commandBuffer, pageTable.vkBuffer(), (long) slots[first] * TerrainGpuLayout.SECTION_METADATA_STRIDE, (long) (end - first) * TerrainGpuLayout.SECTION_METADATA_STRIDE, 0);
        }
    }
    
    private static long stage(MemorySegment staged, long cursor, MemorySegment source, VkBufferCopy.Buffer regions, long targetOffset) {
        long length = source.byteSize();
        if (length == 0L) {
            return cursor;
        }
        MemorySegment.copy(source, 0L, staged, cursor, length);
        regions.apply(region -> region.srcOffset(cursor).dstOffset(targetOffset).size(length));
        return cursor + length;
    }
    
    private static void copy(VkCommandBuffer commandBuffer, DeviceBuffer staging, DeviceBuffer target, VkBufferCopy.Buffer regions) {
        regions.flip();
        if (regions.hasRemaining()) {
            VK10.vkCmdCopyBuffer(commandBuffer, staging.vkBuffer(), target.vkBuffer(), regions);
        }
    }
    
    /**
     * A command that launches some tasks in one row of one layer.
     */
    private static void writeMeshTaskCommand(MemorySegment commands, long offset, int taskCount) {
        commands.set(TerrainGpuRecords.INT, offset + TerrainMeshTaskCommandRecord.GROUP_COUNT_X, taskCount);
        commands.set(TerrainGpuRecords.INT, offset + TerrainMeshTaskCommandRecord.GROUP_COUNT_Y, 1);
        commands.set(TerrainGpuRecords.INT, offset + TerrainMeshTaskCommandRecord.GROUP_COUNT_Z, 1);
    }
    
    public synchronized void configure(VulkanDevice device, VulkanCapabilitySnapshot capabilities) {
        this.device = device;
        retireResources(this.resources);
        this.layout = TerrainDescriptorHeapLayout.from(capabilities);
        this.resources = TerrainGpuResources.create(device, this.layout);
        this.resources.clearDebugCounters();
        this.resources.clearWorkQueueCounters();
        SectionMeshletStore.resetResidentPages(this.layout.sectionCapacity(), this.layout.meshletCapacity(), this.layout.vertexPayloadBytes(), this.layout.indexPayloadBytes());
        writeTerrainMaterialRecords(this.resources, null, null);
        this.terrainDataDirty.set(true);
        this.visibleMeshletCursor = 0;
        this.workQueueCursor = 0;
        this.meshTaskCommandCursor = 0;
        forgetRingLaps();
    }
    
    public synchronized void shutdownNow() {
        destroyRetiredResourcesNow();
        this.resources.destroyNow();
        this.resources = TerrainGpuResources.empty();
        this.layout = TerrainDescriptorHeapLayout.unconfigured();
        SectionMeshletStore.resetResidentPages(0, 0, 0L, 0L);
        this.uploadStats = TerrainUploadStats.empty();
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
        this.lastVisibleMeshletUpload = TerrainVisibleMeshletStats.empty();
        this.lastWorkQueueUpload = TerrainWorkQueueStats.empty();
        this.lastMeshTaskCommandUpload = TerrainMeshTaskCommandStats.empty();
        this.visibleMeshletCursor = 0;
        this.workQueueCursor = 0;
        this.meshTaskCommandCursor = 0;
        this.terrainDataDirty.set(false);
        forgetRingLaps();
        this.device = null;
        this.lastCapacityGrowthReason = "";
    }
    
    private void retireResources(TerrainGpuResources resources) {
        if (!resources.allocated()) {
            return;
        }
        resources.close();
        this.retiredResources.add(resources);
    }
    
    private void destroyRetiredResourcesNow() {
        for (TerrainGpuResources resources : this.retiredResources) {
            resources.destroyNow();
        }
        this.retiredResources.clear();
    }
    
    private synchronized GpuResourceRetirementStats retiredResourceStats() {
        GpuResourceRetirementStats stats = GpuResourceRetirementStats.empty();
        for (TerrainGpuResources resources : this.retiredResources) {
            stats = stats.plus(resources.retirementStats());
        }
        return stats;
    }
    
    /**
     * Records an image the terrain fragment stage reads texel by texel, which takes no sampler.
     */
    public void recordImageBinding(String name, GpuTextureView view) {
        if (!(view instanceof VulkanGpuTextureView vulkanView)) {
            this.textureBindings.put(name, TerrainTextureBinding.unavailable(name, view, null));
            return;
        }
        this.textureBindings.put(name, new TerrainTextureBinding(name, vulkanView.vkImageView(), 0L, view.getWidth(0), view.getHeight(0), view.baseMipLevel(), view.mipLevels(), 0, "none", "none", "none", view.isClosed(), false));
    }
    
    public boolean imageBindingReady(String name) {
        TerrainTextureBinding binding = this.textureBindings.get(name);
        return binding != null && !binding.unavailable() && !binding.closed() && binding.imageView() != 0L;
    }
    
    public void recordTextureBinding(String name, GpuTextureView view, GpuSampler sampler) {
        if (!(view instanceof VulkanGpuTextureView vulkanView) || !(sampler instanceof VulkanGpuSampler vulkanSampler)) {
            this.textureBindings.put(name, TerrainTextureBinding.unavailable(name, view, sampler));
            return;
        }
        
        // Minecraft binds the same atlas and lightmap for every group of every frame.
        TerrainTextureBinding known = this.textureBindings.get(name);
        if (known != null && !known.unavailable() && known.imageView() == vulkanView.vkImageView() && known.sampler() == vulkanSampler.vkSampler() && known.closed() == view.isClosed()) {
            return;
        }
        this.textureBindings.put(name, new TerrainTextureBinding(name, vulkanView.vkImageView(), vulkanSampler.vkSampler(), view.getWidth(0), view.getHeight(0), view.baseMipLevel(), view.mipLevels(), sampler.getMaxAnisotropy(), String.valueOf(sampler.getMinFilter()), String.valueOf(sampler.getMagFilter()), String.valueOf(sampler.getMaxLod()), view.isClosed(), false));
        writeTerrainMaterialRecords(this.resources, this.textureBindings.get("Sampler0"), this.textureBindings.get("Sampler2"));
    }
    
    private boolean textureBindingMissing(String name) {
        TerrainTextureBinding binding = this.textureBindings.get(name);
        return binding == null || binding.unavailable() || binding.closed() || binding.imageView() == 0L || binding.sampler() == 0L;
    }
    
    public long textureImageView(String name) {
        TerrainTextureBinding binding = this.textureBindings.get(name);
        return binding == null ? 0L : binding.imageView();
    }
    
    public void markTerrainDataDirty() {
        this.terrainDataDirty.set(true);
    }
    
    /**
     * Copies the pages of section layers that changed to the GPU. Called while no render pass is
     * open. A page that did not change is not touched.
     */
    public synchronized void uploadDirtyTerrainData() {
        if (!this.terrainDataDirty.compareAndSet(true, false)) {
            return;
        }
        TerrainGpuResources activeResources = this.resources;
        VulkanDevice activeDevice = this.device;
        if (activeDevice == null || !activeResources.readyForTerrainMetadata()) {
            this.terrainDataDirty.set(true);
            return;
        }
        VulkanCommandEncoder encoder = activeDevice.createCommandEncoder();
        if (TerrainRenderPassAccess.insideRenderPass(encoder)) {
            this.terrainDataDirty.set(true);
            return;
        }
        
        long startedNanos = System.nanoTime();
        List<MeshletPageUpload> uploads = SectionMeshletStore.collectPageUploads(PAGE_UPLOAD_BYTES_PER_FRAME, PAGE_UPLOADS_PER_FRAME);
        ResidentPageTableChanges tableChanges = SectionMeshletStore.takePageTableChanges();
        if (!uploads.isEmpty() || tableChanges.any()) {
            waitIdleIfRequested(activeDevice);
            copyPagesToGpu(activeDevice, encoder, activeResources, uploads, tableChanges);
            this.pageUploads.add(uploads.size());
        }
        ResidentPageStats pages = SectionMeshletStore.residentPageStats();
        this.uploadStats = this.uploadStats.afterPageUpload(SectionMeshletStore.liveSectionCount(), pages, System.nanoTime() - startedNanos);
        GpuWorldDatabase.get().recordResidentTerrainUsage(pages.livePages(), pages.vertexBytesUsed() + pages.indexBytesUsed() + pages.meshletsUsed() * TerrainGpuLayout.MESHLET_HEADER_STRIDE + pages.slotsUsed() * TerrainGpuLayout.SECTION_METADATA_STRIDE, activeResources.residentTerrainBytes(), pages.allocationRefusals());
        if (pages.pendingPages() > 0) {
            this.terrainDataDirty.set(true);
        }
        growTerrainResourcesIfNeeded(SectionMeshletStore.takePageShortage());
    }
    
    private void waitIdleIfRequested(VulkanDevice activeDevice) {
        if (!TerrainRendererDebugConfig.waitIdleBeforeTerrainUpload()) {
            return;
        }
        VulkanUtils.crashIfFailure(activeDevice, VK12.vkDeviceWaitIdle(activeDevice.vkDevice()), "Failed to wait for Vulkan Improvement terrain upload safety");
        this.terrainUploadDeviceIdleWaits.increment();
    }
    
    /**
     * Writes the pages into a staging buffer and records the copies into the GPU buffers, after
     * clearing the page-table records of pages given back, in the command buffer Minecraft records
     * the frame into. The copies run in command order, after every draw recorded before them, so a
     * range that was given back and handed out again is never overwritten while an earlier draw
     * still reads it.
     */
    private void copyPagesToGpu(VulkanDevice activeDevice, VulkanCommandEncoder encoder, TerrainGpuResources target, List<MeshletPageUpload> uploads, ResidentPageTableChanges tableChanges) {
        long stagingBytes = 0L;
        for (MeshletPageUpload upload : uploads) {
            stagingBytes += upload.bytes();
        }
        DeviceBuffer staging = stagingBytes > 0L ? new DeviceBuffer(activeDevice, "VIM Terrain Page Staging", stagingBytes, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT, DeviceBufferPlacement.HOST) : null;
        VkBufferCopy.Buffer vertexRegions = VkBufferCopy.calloc(uploads.size());
        VkBufferCopy.Buffer indexRegions = VkBufferCopy.calloc(uploads.size());
        VkBufferCopy.Buffer headerRegions = VkBufferCopy.calloc(uploads.size());
        VkBufferCopy.Buffer recordRegions = VkBufferCopy.calloc(uploads.size());
        try {
            if (staging != null) {
                MemorySegment staged = staging.mapped();
                long cursor = 0L;
                for (MeshletPageUpload upload : uploads) {
                    ResidentTerrainPage page = upload.page();
                    cursor = stage(staged, cursor, upload.vertices(), vertexRegions, page.vertexOffset());
                    cursor = stage(staged, cursor, upload.indices(), indexRegions, page.indexOffset());
                    cursor = stage(staged, cursor, upload.meshletHeaders(), headerRegions, (long) page.firstMeshlet() * TerrainGpuLayout.MESHLET_HEADER_STRIDE);
                    cursor = stage(staged, cursor, upload.pageRecord(), recordRegions, (long) page.slot() * TerrainGpuLayout.SECTION_METADATA_STRIDE);
                }
                staging.flush();
            }
            
            VkCommandBuffer commandBuffer = Objects.requireNonNull(TerrainRenderPassAccess.frameCommandBuffer(encoder), "no render pass is open");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VulkanCommandEncoder.memoryBarrier(commandBuffer, stack);
                clearPageRecords(commandBuffer, target.sectionMetadata(), tableChanges, stack);
                if (staging != null) {
                    copy(commandBuffer, staging, target.meshletVertices(), vertexRegions);
                    copy(commandBuffer, staging, target.meshletIndices(), indexRegions);
                    copy(commandBuffer, staging, target.meshletHeaders(), headerRegions);
                    copy(commandBuffer, staging, target.sectionMetadata(), recordRegions);
                }
                VulkanCommandEncoder.memoryBarrier(commandBuffer, stack);
            }
            this.pageUploadBytes.add(stagingBytes);
        } finally {
            vertexRegions.free();
            indexRegions.free();
            headerRegions.free();
            recordRegions.free();
            if (staging != null) {
                staging.close();
            }
        }
    }
    
    /**
     * Whether a ring may not start over yet: the frame that began its current lap has not finished
     * on the GPU, after a short wait for it.
     */
    private boolean ringLapUnfinished(TerrainDescriptorRing ring) {
        GpuFence fence = this.ringLaps[ring.ordinal()];
        if (fence == null) {
            return false;
        }
        try {
            if (!fence.awaitCompletion(5000L)) {
                this.terrainUploadFenceDeferrals.increment();
                return true;
            }
            this.terrainUploadFenceWaits.increment();
            return false;
        } catch (IllegalStateException ex) {
            this.terrainUploadFenceDeferrals.increment();
            return true;
        } catch (RuntimeException ex) {
            this.terrainUploadFenceFailures.increment();
            throw ex;
        }
    }
    
    /**
     * Called when a ring hands out its first record again: the frame being recorded starts a lap.
     */
    private void beginRingLap(TerrainDescriptorRing ring) {
        VulkanDevice activeDevice = this.device;
        GpuFence previous = this.ringLaps[ring.ordinal()];
        if (previous != null) {
            previous.close();
        }
        this.ringLaps[ring.ordinal()] = activeDevice == null ? null : activeDevice.createCommandEncoder().createFence();
    }
    
    private void forgetRingLaps() {
        for (int i = 0; i < this.ringLaps.length; i++) {
            if (this.ringLaps[i] != null) {
                this.ringLaps[i].close();
                this.ringLaps[i] = null;
            }
        }
    }
    
    /**
     * Replaces the terrain buffers by larger ones when a page did not fit. Every page is then
     * uploaded again, from the copies the section store keeps.
     */
    private void growTerrainResourcesIfNeeded(ResidentPageShortage shortage) {
        if (!shortage.any()) {
            return;
        }
        VulkanDevice activeDevice = this.device;
        if (activeDevice == null) {
            this.terrainDataDirty.set(true);
            return;
        }
        TerrainDescriptorHeapLayout grownLayout = this.layout.growFor(shortage);
        if (grownLayout.equals(this.layout)) {
            this.terrainDataDirty.set(true);
            return;
        }
        
        this.lastCapacityGrowthReason = "short of pages=" + shortage.pages() + ", meshlets=" + shortage.meshlets() + ", vertexBytes=" + shortage.vertexBytes() + ", indexBytes=" + shortage.indexBytes();
        retireResources(this.resources);
        this.layout = grownLayout;
        this.resources = TerrainGpuResources.create(activeDevice, grownLayout);
        this.resources.clearDebugCounters();
        this.resources.clearWorkQueueCounters();
        this.uploadStats = TerrainUploadStats.empty();
        SectionMeshletStore.resetResidentPages(grownLayout.sectionCapacity(), grownLayout.meshletCapacity(), grownLayout.vertexPayloadBytes(), grownLayout.indexPayloadBytes());
        this.visibleMeshletCursor = 0;
        this.workQueueCursor = 0;
        this.meshTaskCommandCursor = 0;
        forgetRingLaps();
        this.terrainCapacityGrowths.increment();
        this.terrainDataDirty.set(true);
    }
    
    public boolean hasGpuTerrainData() {
        return this.resources.readyForTerrainMetadata() && this.uploadStats.meshletsUploaded() > 0;
    }
    
    public int meshletsUploaded() {
        return this.uploadStats.meshletsUploaded();
    }
    
    public int meshletCountForLayer(int layerOrdinal) {
        return this.uploadStats.meshletCountForLayer(layerOrdinal);
    }
    
    public int customIndexMeshletCountForLayer(int layerOrdinal) {
        return this.uploadStats.customIndexMeshletCountForLayer(layerOrdinal);
    }
    
    public long sectionMetadataAddress() {
        DeviceBuffer buffer = this.resources.sectionMetadata();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long meshletHeaderAddress() {
        DeviceBuffer buffer = this.resources.meshletHeaders();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long meshletVertexPayloadAddress() {
        DeviceBuffer buffer = this.resources.meshletVertices();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long meshletIndexPayloadAddress() {
        DeviceBuffer buffer = this.resources.meshletIndices();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long visibleMeshletListAddress() {
        DeviceBuffer buffer = this.resources.visibleMeshlets();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long terrainWorkQueueAddress() {
        DeviceBuffer buffer = this.resources.workQueue();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long terrainMeshTaskCommandAddress() {
        DeviceBuffer buffer = this.resources.meshTaskCommands();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long terrainDescriptorBufferAddress() {
        DeviceBuffer buffer = this.resources.resourceDescriptorBuffer();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long materialTableAddress() {
        DeviceBuffer buffer = this.resources.materialTable();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    public long terrainDebugCounterAddress() {
        DeviceBuffer buffer = this.resources.debugCounters();
        return buffer == null ? 0L : buffer.deviceAddress();
    }
    
    /**
     * @param improvedFog whether the draw reads the sky and cloud images, which then have to be
     *                    known; otherwise their descriptors are left as they are and not read
     */
    public boolean writeTerrainTextureDescriptors(VulkanDevice device, TerrainTextureDescriptorLayout textureLayout, boolean improvedFog) {
        TerrainGpuResources activeResources = this.resources;
        if (!this.layout.descriptorBufferEnabled() || !activeResources.readyForDescriptorBuffer() || textureBindingMissing("Sampler0") || textureBindingMissing("Sampler2")) {
            this.descriptorBufferMissing.increment();
            return false;
        }
        if (improvedFog && !(imageBindingReady(SKY_IMAGE) && imageBindingReady(CLOUDS_IMAGE) && imageBindingReady(CLOUDS_DEPTH_IMAGE))) {
            this.descriptorBufferMissing.increment();
            return false;
        }
        
        TerrainTextureBinding blockAtlas = this.textureBindings.get("Sampler0");
        TerrainTextureBinding lightmap = this.textureBindings.get("Sampler2");
        // The descriptors stay in the buffer; they are written again only when what they describe changes.
        TerrainDescriptorWrite write = new TerrainDescriptorWrite(activeResources, textureLayout, blockAtlas.imageView(), blockAtlas.sampler(), lightmap.imageView(), lightmap.sampler(), improvedFog, improvedFog ? textureImageView(SKY_IMAGE) : 0L, improvedFog ? textureImageView(CLOUDS_IMAGE) : 0L, improvedFog ? textureImageView(CLOUDS_DEPTH_IMAGE) : 0L);
        if (write.equals(this.lastDescriptorWrite)) {
            return true;
        }
        MemorySegment descriptors = activeResources.resourceDescriptorBuffer().mapped();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            writeSampledImageDescriptor(device, stack, descriptors, textureLayout.blockAtlasImageOffset(), textureLayout.sampledImageDescriptorBytes(), blockAtlas);
            writeSamplerDescriptor(device, stack, descriptors, textureLayout.blockAtlasSamplerOffset(), textureLayout.samplerDescriptorBytes(), blockAtlas);
            writeSampledImageDescriptor(device, stack, descriptors, textureLayout.lightmapImageOffset(), textureLayout.sampledImageDescriptorBytes(), lightmap);
            writeSamplerDescriptor(device, stack, descriptors, textureLayout.lightmapSamplerOffset(), textureLayout.samplerDescriptorBytes(), lightmap);
            if (improvedFog) {
                writeSampledImageDescriptor(device, stack, descriptors, textureLayout.skyImageOffset(), textureLayout.sampledImageDescriptorBytes(), this.textureBindings.get(SKY_IMAGE));
                writeSampledImageDescriptor(device, stack, descriptors, textureLayout.cloudsImageOffset(), textureLayout.sampledImageDescriptorBytes(), this.textureBindings.get(CLOUDS_IMAGE));
                writeSampledImageDescriptor(device, stack, descriptors, textureLayout.cloudsDepthImageOffset(), textureLayout.sampledImageDescriptorBytes(), this.textureBindings.get(CLOUDS_DEPTH_IMAGE));
            }
        }
        activeResources.resourceDescriptorBuffer().flush();
        writeTerrainMaterialRecords(activeResources, blockAtlas, lightmap);
        this.descriptorBufferWrites.increment();
        this.lastDescriptorWrite = write;
        return true;
    }
    
    private void writeTerrainMaterialRecords(TerrainGpuResources activeResources, TerrainTextureBinding blockAtlas, TerrainTextureBinding lightmap) {
        DeviceBuffer materialTable = activeResources.materialTable();
        if (materialTable == null || !materialTable.hostVisible() || materialTable.size() < TerrainGpuLayout.MATERIAL_RECORD_STRIDE) {
            this.materialTableMissing.increment();
            return;
        }
        
        TerrainMaterialClassifier.writeTerrainLayerRecords(materialTable.mapped(), textureInfo(blockAtlas), textureInfo(lightmap));
        materialTable.flush();
        this.materialTableWrites.increment();
    }
    
    public long terrainVisibilityCounterAddress() {
        DeviceBuffer buffer = this.resources.debugCounters();
        return buffer == null ? 0L : buffer.deviceAddress() + TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET;
    }
    
    /**
     * How many pages the GPU visibility pass has looked at, dropped as out of view, and kept,
     * since the buffers were created. The GPU may still be counting the last frames.
     */
    public synchronized long[] visibilityCounters() {
        DeviceBuffer buffer = this.resources.debugCounters();
        if (buffer == null) {
            return new long[3];
        }
        buffer.invalidate();
        MemorySegment counters = buffer.mapped();
        long base = TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET;
        return new long[]{Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, base)), Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, base + Integer.BYTES)), Integer.toUnsignedLong(counters.get(TerrainGpuRecords.INT, base + 2 * Integer.BYTES))};
    }
    
    /**
     * Uploads the pages a layer may draw this frame, one record for each of a page's tasks, and
     * reserves as much room again behind them for the list of those the GPU keeps. This short list
     * is all a frame uploads for a layer.
     */
    public synchronized TerrainVisiblePageUpload writeVisiblePageList(MeshletPageList pages, int from, int to) {
        DeviceBuffer ring = this.resources.visibleMeshlets();
        int count = 0;
        for (int page = from; page < to; page++) {
            count += pages.tasks(page);
        }
        if (count <= 0 || ring == null || ring.deviceAddress() == 0L || !ring.hostVisible()) {
            return TerrainVisiblePageUpload.unavailable();
        }
        TerrainVisibleMeshletRing visibleRing = new TerrainVisibleMeshletRing(visibleMeshletRecordCapacity(ring), this.visibleMeshletCursor);
        VisibleMeshletAllocation allocation = visibleRing.allocate(count * 2, false);
        if (allocation.wrapBlocked()) {
            if (ringLapUnfinished(TerrainDescriptorRing.VISIBLE)) {
                this.visibleMeshletListDeferrals.increment();
                return TerrainVisiblePageUpload.unavailable();
            }
            allocation = visibleRing.allocate(count * 2, true);
        }
        if (!allocation.ready()) {
            this.visibleMeshletRecordsDropped.add(count);
            return TerrainVisiblePageUpload.unavailable();
        }
        this.visibleMeshletCursor = allocation.nextOffset();
        if (allocation.offset() == 0) {
            beginRingLap(TerrainDescriptorRing.VISIBLE);
        }
        if (allocation.wrapped()) {
            this.visibleMeshletRingWraps.increment();
        }
        MemorySegment records = ring.mapped();
        long record = (long) allocation.offset() * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE;
        for (int page = from; page < to; page++) {
            int slot = pages.slot(page);
            float visibility = Math.clamp(pages.visibility(page), 0.0F, 1.0F);
            for (int task = 0, tasks = pages.tasks(page); task < tasks; task++, record += TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE) {
                records.set(TerrainGpuRecords.INT, record + TerrainVisibleRecord.MESHLET_INDEX, slot | task << TerrainGpuLayout.TASK_RECORD_PAGE_BITS);
                records.set(TerrainGpuRecords.FLOAT, record + TerrainVisibleRecord.VISIBILITY, visibility);
            }
        }
        ring.flush((long) allocation.offset() * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE, (long) count * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
        this.visiblePageListUploads.increment();
        this.visiblePageRecordsUploaded.add(count);
        return new TerrainVisiblePageUpload(allocation.offset(), allocation.offset() + count, count, ring.deviceAddress(), true);
    }
    
    /**
     * Uploads what a GPU-driven frame tells the GPU: the counters the GPU-driven pass sorts tasks
     * with, cleared, and behind them the sections in view, one bit each. Behind those it reserves
     * room for each layer's task records, as many as all the layer's pages would launch, which the
     * pass writes. A frame uploads a few kilobytes this way, however much terrain is in view.
     */
    synchronized TerrainGpuDrivenFrame writeGpuDrivenFrame(VisibleSectionGrid sections, int[] layerTasks) {
        DeviceBuffer ring = this.resources.visibleMeshlets();
        if (ring == null || ring.deviceAddress() == 0L || !ring.hostVisible()) {
            return TerrainGpuDrivenFrame.unavailable();
        }
        int written = TerrainGpuLayout.SECTION_PAGES_COUNTER_RECORDS + sections.wordCount();
        long count = written;
        for (int tasks : layerTasks) {
            count += tasks;
        }
        if (count > Integer.MAX_VALUE) {
            return TerrainGpuDrivenFrame.unavailable();
        }
        TerrainVisibleMeshletRing visibleRing = new TerrainVisibleMeshletRing(visibleMeshletRecordCapacity(ring), this.visibleMeshletCursor);
        VisibleMeshletAllocation allocation = visibleRing.allocate((int) count, false);
        if (allocation.wrapBlocked()) {
            if (ringLapUnfinished(TerrainDescriptorRing.VISIBLE)) {
                this.visibleMeshletListDeferrals.increment();
                return TerrainGpuDrivenFrame.unavailable();
            }
            allocation = visibleRing.allocate((int) count, true);
        }
        if (!allocation.ready()) {
            this.visibleMeshletRecordsDropped.add(count);
            return TerrainGpuDrivenFrame.unavailable();
        }
        this.visibleMeshletCursor = allocation.nextOffset();
        if (allocation.offset() == 0) {
            beginRingLap(TerrainDescriptorRing.VISIBLE);
        }
        if (allocation.wrapped()) {
            this.visibleMeshletRingWraps.increment();
        }
        long offsetBytes = (long) allocation.offset() * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE;
        long counterBytes = (long) TerrainGpuLayout.SECTION_PAGES_COUNTER_RECORDS * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE;
        MemorySegment frame = ring.mapped().asSlice(offsetBytes, (long) written * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE);
        frame.asSlice(0L, counterBytes).fill((byte) 0);
        MemorySegment.copy(sections.words(), 0, frame, TerrainGpuRecords.LONG, counterBytes, sections.wordCount());
        ring.flush(offsetBytes, frame.byteSize());
        this.gpuDrivenFrameUploads.increment();
        return new TerrainGpuDrivenFrame(ring.deviceAddress(), allocation.offset(), allocation.offset() + written, true);
    }
    
    public synchronized TerrainVisibleMeshletUpload writeVisibleMeshletList(List<MeshletRange> ranges) {
        int requiredRecords = visibleMeshletRecordCount(ranges);
        if (requiredRecords <= 0) {
            VisibleMeshletAllocation allocation = new TerrainVisibleMeshletRing(visibleMeshletRecordCapacity(), this.visibleMeshletCursor).allocate(requiredRecords, true);
            this.lastVisibleMeshletUpload = TerrainVisibleMeshletStats.fromAllocation(0, 0, allocation);
            return TerrainVisibleMeshletUpload.unavailable(0);
        }
        
        TerrainGpuResources activeResources = this.resources;
        DeviceBuffer visibleMeshlets = activeResources.visibleMeshlets();
        if (visibleMeshlets == null || visibleMeshlets.deviceAddress() == 0L || !visibleMeshlets.hostVisible()) {
            VisibleMeshletAllocation allocation = new TerrainVisibleMeshletRing(0, 0).allocate(requiredRecords, true);
            this.lastVisibleMeshletUpload = TerrainVisibleMeshletStats.fromAllocation(0, requiredRecords, allocation);
            this.visibleMeshletRecordsDropped.add(requiredRecords);
            return TerrainVisibleMeshletUpload.unavailable(requiredRecords);
        }
        
        int capacity = visibleMeshletRecordCapacity(visibleMeshlets);
        TerrainVisibleMeshletRing ring = new TerrainVisibleMeshletRing(capacity, this.visibleMeshletCursor);
        VisibleMeshletAllocation allocation = ring.allocate(requiredRecords, false);
        if (allocation.wrapBlocked()) {
            if (ringLapUnfinished(TerrainDescriptorRing.VISIBLE)) {
                this.visibleMeshletListDeferrals.increment();
                this.lastVisibleMeshletUpload = TerrainVisibleMeshletStats.fromAllocation(0, 0, allocation);
                return TerrainVisibleMeshletUpload.unavailable(requiredRecords);
            }
            allocation = ring.allocate(requiredRecords, true);
        }
        if (!allocation.ready()) {
            this.lastVisibleMeshletUpload = TerrainVisibleMeshletStats.fromAllocation(0, requiredRecords, allocation);
            this.visibleMeshletRecordsDropped.add(requiredRecords);
            return TerrainVisibleMeshletUpload.unavailable(requiredRecords);
        }
        
        int firstRecord = allocation.offset();
        this.visibleMeshletCursor = allocation.nextOffset();
        if (firstRecord == 0) {
            beginRingLap(TerrainDescriptorRing.VISIBLE);
        }
        if (allocation.wrapped()) {
            this.visibleMeshletRingWraps.increment();
        }
        MemorySegment visibleRecords = visibleMeshlets.mapped();
        long record = (long) firstRecord * TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE;
        for (MeshletRange range : ranges) {
            float visibility = Math.clamp(range.visibility(), 0.0F, 1.0F);
            for (int i = 0; i < range.count(); i++, record += TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE) {
                visibleRecords.set(TerrainGpuRecords.INT, record + TerrainVisibleRecord.MESHLET_INDEX, range.offset() + i);
                visibleRecords.set(TerrainGpuRecords.FLOAT, record + TerrainVisibleRecord.VISIBILITY, visibility);
            }
        }
        visibleMeshlets.flush();
        this.visibleMeshletListUploads.increment();
        this.visibleMeshletRecordsUploaded.add(requiredRecords);
        this.lastVisibleMeshletUpload = TerrainVisibleMeshletStats.fromAllocation(requiredRecords, 0, allocation);
        return new TerrainVisibleMeshletUpload(firstRecord, requiredRecords, visibleMeshlets.deviceAddress(), 0, true);
    }
    
    public synchronized TerrainWorkQueueUpload writeVisibleWorkQueue(List<MeshletRange> ranges, int layerOrdinal) {
        int requiredRecords = visibleMeshletRecordCount(ranges);
        if (requiredRecords <= 0) {
            this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(0, workQueueRecordCapacity(), this.workQueueCursor, "no visible terrain work queue records requested", false);
            return TerrainWorkQueueUpload.unavailable(0);
        }
        
        DeviceBuffer workQueue = this.resources.workQueue();
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
            if (ringLapUnfinished(TerrainDescriptorRing.WORK_QUEUE)) {
                this.terrainWorkQueueDeferrals.increment();
                this.lastWorkQueueUpload = TerrainWorkQueueStats.unavailable(requiredRecords, capacity, this.workQueueCursor, "visible terrain work queue wrap requires the frame that began the lap to finish", true);
                return TerrainWorkQueueUpload.unavailable(requiredRecords);
            }
            this.workQueueCursor = 0;
            wrapped = true;
        }
        
        int firstRecord = this.workQueueCursor;
        if (firstRecord == 0) {
            beginRingLap(TerrainDescriptorRing.WORK_QUEUE);
        }
        this.workQueueCursor += requiredRecords;
        MemorySegment queue = workQueue.mapped();
        queue.asSlice(0L, TerrainGpuLayout.TERRAIN_WORK_QUEUE_COUNTER_BYTES).fill((byte) 0);
        queue.set(TerrainGpuRecords.INT, 0L, requiredRecords);
        long record = TerrainWorkQueueLayout.recordOffset(firstRecord);
        for (MeshletRange range : ranges) {
            for (int i = 0; i < range.count(); i++, record += TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE) {
                queue.set(TerrainGpuRecords.INT, record + TerrainWorkQueueRecord.MESHLET_INDEX, range.offset() + i);
                queue.set(TerrainGpuRecords.INT, record + TerrainWorkQueueRecord.LAYER_ORDINAL, layerOrdinal);
                queue.set(TerrainGpuRecords.INT, record + TerrainWorkQueueRecord.LOD_LEVEL, 0);
                queue.set(TerrainGpuRecords.INT, record + TerrainWorkQueueRecord.FLAGS, 0);
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
        
        DeviceBuffer meshTaskCommands = this.resources.meshTaskCommands();
        writeMeshTaskCommand(meshTaskCommands.mapped(), upload.offsetBytes(), taskCount);
        meshTaskCommands.flush();
        this.terrainMeshTaskCommandUploads.increment();
        return upload;
    }
    
    /**
     * Reserves a command for the GPU visibility pass to fill and writes its starting value, no tasks
     * in one row of one layer, which the pass counts up. Written here rather than by a copy in the
     * frame's commands, the command needs no barrier before the pass.
     */
    public synchronized TerrainMeshTaskCommandUpload reserveMeshTaskCommand(int taskCount) {
        TerrainMeshTaskCommandUpload upload = allocateMeshTaskCommand(taskCount, true);
        if (upload.ready()) {
            DeviceBuffer meshTaskCommands = this.resources.meshTaskCommands();
            writeMeshTaskCommand(meshTaskCommands.mapped(), upload.offsetBytes(), 0);
            meshTaskCommands.flush();
            this.terrainMeshTaskCommandReservations.increment();
        }
        return upload;
    }
    
    private TerrainMeshTaskCommandUpload allocateMeshTaskCommand(int taskCount, boolean gpuGenerated) {
        if (taskCount <= 0) {
            this.lastMeshTaskCommandUpload = TerrainMeshTaskCommandStats.unavailable(0, meshTaskCommandCapacity(), this.meshTaskCommandCursor, "no terrain mesh-task command requested", false, gpuGenerated);
            return TerrainMeshTaskCommandUpload.unavailable(0);
        }
        
        DeviceBuffer meshTaskCommands = this.resources.meshTaskCommands();
        if (meshTaskCommands == null || meshTaskCommands.vkBuffer() == 0L || !meshTaskCommands.hostVisible()) {
            this.lastMeshTaskCommandUpload = TerrainMeshTaskCommandStats.unavailable(1, 0, this.meshTaskCommandCursor, "terrain mesh-task command buffer unavailable", false, gpuGenerated);
            this.terrainMeshTaskCommandDrops.increment();
            return TerrainMeshTaskCommandUpload.unavailable(1);
        }
        
        int capacity = meshTaskCommandCapacity(meshTaskCommands);
        if (capacity <= 0) {
            this.lastMeshTaskCommandUpload = TerrainMeshTaskCommandStats.unavailable(1, capacity, this.meshTaskCommandCursor, "terrain mesh-task command buffer has no capacity", false, gpuGenerated);
            this.terrainMeshTaskCommandDrops.increment();
            return TerrainMeshTaskCommandUpload.unavailable(1);
        }
        
        boolean wrapped = false;
        if (this.meshTaskCommandCursor + 1 > capacity) {
            if (ringLapUnfinished(TerrainDescriptorRing.COMMANDS)) {
                this.terrainMeshTaskCommandDeferrals.increment();
                this.lastMeshTaskCommandUpload = TerrainMeshTaskCommandStats.unavailable(1, capacity, this.meshTaskCommandCursor, "terrain mesh-task command wrap requires the frame that began the lap to finish", true, gpuGenerated);
                return TerrainMeshTaskCommandUpload.unavailable(1);
            }
            this.meshTaskCommandCursor = 0;
            wrapped = true;
        }
        
        int commandIndex = this.meshTaskCommandCursor;
        if (commandIndex == 0) {
            beginRingLap(TerrainDescriptorRing.COMMANDS);
        }
        this.meshTaskCommandCursor++;
        long offsetBytes = TerrainMeshTaskCommandLayout.commandOffset(commandIndex);
        if (wrapped) {
            this.terrainMeshTaskCommandRingWraps.increment();
        }
        this.lastMeshTaskCommandUpload = new TerrainMeshTaskCommandStats(1, 0, commandIndex, capacity, this.meshTaskCommandCursor, wrapped, false, "", gpuGenerated);
        return new TerrainMeshTaskCommandUpload(commandIndex, taskCount, meshTaskCommands.vkBuffer(), offsetBytes, TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, meshTaskCommands.deviceAddress() + offsetBytes, 0, true, gpuGenerated);
    }
    
    private int visibleMeshletRecordCapacity() {
        DeviceBuffer visibleMeshlets = this.resources.visibleMeshlets();
        return visibleMeshlets == null ? 0 : visibleMeshletRecordCapacity(visibleMeshlets);
    }
    
    private int workQueueRecordCapacity() {
        DeviceBuffer workQueue = this.resources.workQueue();
        return workQueue == null ? 0 : workQueueRecordCapacity(workQueue);
    }
    
    private int meshTaskCommandCapacity() {
        DeviceBuffer meshTaskCommands = this.resources.meshTaskCommands();
        return meshTaskCommands == null ? 0 : meshTaskCommandCapacity(meshTaskCommands);
    }
    
    public TerrainDescriptorHeapLayout layout() {
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
        map.put("waitIdleBeforeTerrainUpload", TerrainRendererDebugConfig.waitIdleBeforeTerrainUpload());
        map.put("terrainUploadDeviceIdleWaits", this.terrainUploadDeviceIdleWaits.sum());
        map.put("terrainUploadFenceWaits", this.terrainUploadFenceWaits.sum());
        map.put("terrainUploadFenceDeferrals", this.terrainUploadFenceDeferrals.sum());
        map.put("terrainUploadFenceFailures", this.terrainUploadFenceFailures.sum());
        map.put("ringLapFences", Arrays.stream(this.ringLaps).filter(Objects::nonNull).count());
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
        map.put("terrainWorkQueueLayout", TerrainWorkQueueLayout.asMap(this.layout.meshletCapacity()));
        map.put("terrainMeshTaskCommandUploads", this.terrainMeshTaskCommandUploads.sum());
        map.put("terrainMeshTaskCommandReservations", this.terrainMeshTaskCommandReservations.sum());
        map.put("terrainMeshTaskCommandDrops", this.terrainMeshTaskCommandDrops.sum());
        map.put("terrainMeshTaskCommandDeferrals", this.terrainMeshTaskCommandDeferrals.sum());
        map.put("terrainMeshTaskCommandRingWraps", this.terrainMeshTaskCommandRingWraps.sum());
        map.put("lastTerrainMeshTaskCommandUpload", this.lastMeshTaskCommandUpload.asMap());
        map.put("terrainMeshTaskCommandLayout", TerrainMeshTaskCommandLayout.asMap());
        map.put("lastCapacityGrowthReason", this.lastCapacityGrowthReason);
        map.put("debugCounters", this.resources.debugCounterMap());
        map.put("pageUploads", this.pageUploads.sum());
        map.put("pageUploadBytes", this.pageUploadBytes.sum());
        map.put("visiblePageListUploads", this.visiblePageListUploads.sum());
        map.put("gpuDrivenFrameUploads", this.gpuDrivenFrameUploads.sum());
        map.put("visiblePageRecordsUploaded", this.visiblePageRecordsUploaded.sum());
        map.put("textureBindings", textureBindingMap());
        return map;
    }
    
    private Map<String, Object> textureBindingMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        this.textureBindings.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> map.put(entry.getKey(), entry.getValue().asMap()));
        return map;
    }
}