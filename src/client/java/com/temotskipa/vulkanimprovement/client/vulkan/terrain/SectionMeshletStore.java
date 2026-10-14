package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDatabase;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldTerrainLayerDirectory;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SectionMeshletStore {
    private static final Map<CompiledSectionMesh, CapturedSection> SECTIONS = new ConcurrentHashMap<>();
    private static final VisibleFrame VISIBLE_FRAME = new VisibleFrame();
    private static final ResidentTerrainPages<SectionLayerKey> PAGES = new ResidentTerrainPages<>();
    private static final AtomicLong INVALIDATION_COUNT = new AtomicLong();
    private static final AtomicLong REJECTED_CAPTURES = new AtomicLong();
    private static volatile PageCapacity pageCapacity = new PageCapacity(0, 0, 0L, 0L);
    private static volatile String lastInvalidationReason = "";
    private static volatile String lastRejectedCapture = "";
    
    private SectionMeshletStore() {
    }
    
    public static void capture(long sectionNode, CompiledSectionMesh mesh, ChunkSectionLayer layer, @Nullable ByteBuffer vertexBuffer, @Nullable ByteBuffer indexBuffer) {
        long startedNanos = System.nanoTime();
        int vertexBytes = remaining(vertexBuffer);
        int indexBytes = remaining(indexBuffer);
        var draw = mesh.getSectionDraw(layer);
        int indexCount = draw == null ? 0 : draw.indexCount();
        boolean customIndexBuffer = draw != null && draw.hasCustomIndexBuffer();
        int indexElementBytes = draw == null ? 0 : draw.indexType().bytes;
        int estimatedVertices = customIndexBuffer ? estimateIndexedVertices(indexCount) : estimateVertices(indexCount);
        int estimatedMeshlets = customIndexBuffer ? estimateIndexedMeshlets(indexCount) : estimateMeshlets(estimatedVertices);
        List<TerrainMeshlet> meshlets = customIndexBuffer ? buildIndexedMeshlets(sectionNode, layer, indexCount, estimatedMeshlets) : buildMeshlets(sectionNode, layer, estimatedVertices, indexCount, estimatedMeshlets);
        ByteBuffer copiedVertices = copy(vertexBuffer);
        ByteBuffer copiedIndices = customIndexBuffer ? copyIndexBuffer(indexBuffer, indexElementBytes) : copy(indexBuffer);
        int gpuIndexElementBytes = customIndexBuffer && copiedIndices.remaining() > 0 ? TerrainGpuLayout.NORMALIZED_INDEX_STRIDE : indexElementBytes;
        CaptureResult result = new CaptureResult();
        SECTIONS.compute(mesh, (ignored, existing) -> {
            CapturedSection section = existing == null ? new CapturedSection(sectionNode) : existing;
            LayerCapture previous = section.layers.get(layer);
            ByteBuffer vertices = vertexBuffer == null && previous != null ? previous.buffers.vertices() : copiedVertices;
            ByteBuffer indices = indexBuffer == null && previous != null ? previous.buffers.indices() : copiedIndices;
            int capturedVertexBytes = vertexBuffer == null && previous != null ? previous.vertexBytes : vertexBytes;
            int capturedIndexBytes = indexBuffer == null && previous != null ? previous.indexBytes : indexBytes;
            int capturedIndexElementBytes = gpuIndexElementBytes == 0 && previous != null ? previous.indexElementBytes : gpuIndexElementBytes;
            CaptureValidation validation = validateCapture(layer, vertices, indices, capturedVertexBytes, capturedIndexBytes, indexCount, customIndexBuffer, capturedIndexElementBytes);
            if (!validation.valid()) {
                section.layers.remove(layer);
                removeLayerMappings(section, mesh, sectionNode, layer.ordinal());
                result.reason = validation.reason();
                CapturedSection kept = section.layers.isEmpty() ? null : section;
                holdCapture(mesh, kept);
                return kept;
            }
            MeshLayerBuffers buffers = new MeshLayerBuffers(vertices, indices);
            section.layers.put(layer, new LayerCapture(capturedVertexBytes, capturedIndexBytes, estimatedVertices, estimatedMeshlets, customIndexBuffer, capturedIndexElementBytes, meshlets, buffers));
            result.accepted = true;
            holdCapture(mesh, section);
            return section;
        });
        if (result.accepted) {
            MeshTerrainRenderer.get().recordSectionCapture(vertexBytes, indexBytes, estimatedMeshlets, System.nanoTime() - startedNanos);
            GpuWorldDatabase.get().recordTerrainLayerCapture(sectionNode, layer.ordinal(), TerrainMaterialClassifier.materialIdForLayer(layer));
            PAGES.markPending(new SectionLayerKey(mesh, sectionNode, layer.ordinal()));
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
        else {
            GpuWorldTerrainLayerDirectory.get().removeLayer(sectionNode, layer.ordinal());
            recordRejectedCapture(sectionNode, layer, result.reason);
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
    }
    
    public static void release(CompiledSectionMesh mesh) {
        CapturedSection removed = SECTIONS.remove(mesh);
        holdCapture(mesh, null);
        if (removed != null) {
            for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                PAGES.release(new SectionLayerKey(mesh, removed.sectionNode, layer.ordinal()));
                removed.binding(layer.ordinal()).page = null;
            }
            MeshTerrainRenderer.get().recordSectionRelease();
            GpuWorldDatabase.get().recordSectionRelease(removed.sectionNode);
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
    }
    
    public static void clearAll(String reason) {
        boolean hadTerrainData = !SECTIONS.isEmpty() || VISIBLE_FRAME.sectionLayerCount() > 0 || PAGES.stats().livePages() > 0;
        for (Map.Entry<CompiledSectionMesh, CapturedSection> section : SECTIONS.entrySet()) {
            section.getValue().clearBindings();
            holdCapture(section.getKey(), null);
        }
        SECTIONS.clear();
        VISIBLE_FRAME.clear();
        PageCapacity capacity = pageCapacity;
        PAGES.reset(capacity.pages(), capacity.meshlets(), capacity.vertexBytes(), capacity.indexBytes());
        lastInvalidationReason = reason;
        INVALIDATION_COUNT.incrementAndGet();
        GpuWorldDatabase.get().clear(reason);
        if (hadTerrainData) {
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
    }
    
    /**
     * Starts collecting the section layers of a frame. Minecraft collects them once per frame, for
     * its indirect and its separate draw path alike.
     *
     * @param takeover whether the mesh path takes the section layers it can draw out of Minecraft's
     *                 draw lists this frame; otherwise every section layer Minecraft draws is listed
     */
    public static void beginVisibleFrame(boolean takeover) {
        VISIBLE_FRAME.begin(takeover);
    }

    /**
     * Called for each section layer Minecraft looks at while it collects a frame, before it looks
     * the layer's buffers up. In a takeover frame a layer the mesh path stands in for, whose
     * section has a page on the GPU, is listed for the mesh path and left out of Minecraft's
     * draws: Minecraft neither looks it up nor builds a draw for it.
     *
     * @return whether the mesh path draws this section layer
     */
    public static boolean takeSectionLayer(SectionMesh sectionMesh, ChunkSectionLayer layer, SectionRenderDispatcher.RenderSection section, long now, long fadeDuration) {
        if (!VISIBLE_FRAME.takeover() || !TerrainLayerReplacementPolicy.canReplaceLayer(layer) || !(sectionMesh instanceof CompiledSectionMesh compiledSectionMesh)) {
            return false;
        }
        CapturedSection capturedSection = (Object) compiledSectionMesh instanceof CaptureHolder holder ? (CapturedSection) holder.vim$capturedSection() : SECTIONS.get(compiledSectionMesh);
        if (capturedSection == null) {
            return false;
        }
        PageBinding binding = capturedSection.binding(layer.ordinal());
        ResidentTerrainPages.Page page = binding.page;
        if (page == null || page.meshletCount() <= 0) {
            return false;
        }
        float visibility = TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled() ? Math.clamp(section.getVisibility(now, fadeDuration), 0.0F, 1.0F) : 1.0F;
        return VISIBLE_FRAME.take(layer.ordinal(), binding, capturedSection.sectionNode, visibility);
    }
    
    public static void endVisibleFrame() {
        VISIBLE_FRAME.end();
    }
    
    /**
     * Called for every section layer Minecraft looks up while it collects a frame. A layer it goes
     * on to draw is remembered in its draw order, so the mesh path covers exactly what the vanilla
     * draw would; one the mod has not captured makes the frame's layer uncovered.
     */
    public static void recordDrawSlice(SectionMesh sectionMesh, ChunkSectionLayer layer, SectionRenderDispatcher.@Nullable RenderSectionBufferSlice slice) {
        // In a takeover frame the frame lists only what the mesh path took; Minecraft draws the rest.
        if (slice == null || VISIBLE_FRAME.takeover() || !(sectionMesh instanceof CompiledSectionMesh compiledSectionMesh)) {
            return;
        }
        SectionMesh.SectionDraw draw = compiledSectionMesh.getSectionDraw(layer);
        if (draw == null || draw.hasCustomIndexBuffer() && slice.indexBuffer() == null) {
            return;
        }
        CapturedSection capturedSection = (Object) compiledSectionMesh instanceof CaptureHolder holder ? (CapturedSection) holder.vim$capturedSection() : SECTIONS.get(compiledSectionMesh);
        if (capturedSection == null) {
            VISIBLE_FRAME.addUncaptured(layer.ordinal());
        }
        else {
            VISIBLE_FRAME.add(layer.ordinal(), capturedSection.binding(layer.ordinal()), capturedSection.sectionNode);
        }
    }
    
    /**
     * The meshlet ranges of the section layers Minecraft draws this frame for a layer, in its draw
     * order, or {@code null} when one of them has no meshlets on the GPU yet.
     */
    public static @Nullable List<MeshletRange> visibleMeshletRanges(int layerOrdinal) {
        return VISIBLE_FRAME.meshletRanges(layerOrdinal);
    }

    /**
     * Called as Minecraft makes the draw data of a section it collects for the frame, right after
     * the first of the section's layers it draws; the fade-in applies to all of them.
     */
    private static void holdCapture(CompiledSectionMesh mesh, @Nullable CapturedSection section) {
        if ((Object) mesh instanceof CaptureHolder holder) {
            holder.vim$setCapturedSection(section);
        }
    }

    public static void recordSectionVisibility(int blockX, int blockY, int blockZ, float visibility) {
        long sectionNode = net.minecraft.core.SectionPos.asLong(net.minecraft.core.SectionPos.blockToSectionCoord(blockX), net.minecraft.core.SectionPos.blockToSectionCoord(blockY), net.minecraft.core.SectionPos.blockToSectionCoord(blockZ));
        VISIBLE_FRAME.sectionVisibility(sectionNode, TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled() ? Math.clamp(visibility, 0.0F, 1.0F) : 1.0F);
    }

    /**
     * The pages of the section layers Minecraft draws this frame for a layer, or {@code null} when
     * one of them has no page on the GPU yet.
     */
    public static @Nullable List<PageRef> visiblePages(int layerOrdinal) {
        return VISIBLE_FRAME.pages(layerOrdinal);
    }

    /**
     * Every page a layer has on the GPU as a range of meshlets, whether or not Minecraft draws
     * its section this frame.
     */
    public static List<MeshletRange> meshletRangesForLayer(int layerOrdinal) {
        ArrayList<MeshletRange> ranges = new ArrayList<>();
        for (ResidentTerrainPages.Page page : PAGES.snapshot().values()) {
            if (page.layerOrdinal() == layerOrdinal && page.meshletCount() > 0) {
                ranges.add(new MeshletRange(page.sectionNode(), page.firstMeshlet(), page.meshletCount(), 1.0F));
            }
        }
        ranges.sort(Comparator.comparingLong(MeshletRange::sectionNode).thenComparingInt(MeshletRange::offset));
        return ranges;
    }

    /**
     * Called when the GPU buffers are created or replaced: no section layer has a page in the new
     * buffers, and every captured one waits for one again.
     */
    static void resetResidentPages(int pages, int meshlets, long vertexBytes, long indexBytes) {
        pageCapacity = new PageCapacity(pages, meshlets, vertexBytes, indexBytes);
        PAGES.reset(pages, meshlets, vertexBytes, indexBytes);
        GpuWorldTerrainLayerDirectory.get().clear("resident terrain pages reset");
        for (Map.Entry<CompiledSectionMesh, CapturedSection> section : SECTIONS.entrySet()) {
            section.getValue().clearBindings();
            for (ChunkSectionLayer layer : section.getValue().layers.keySet()) {
                PAGES.markPending(new SectionLayerKey(section.getKey(), section.getValue().sectionNode, layer.ordinal()));
            }
        }
    }

    static ResidentTerrainPages.Stats residentPageStats() {
        return PAGES.stats();
    }

    static ResidentTerrainPages.Shortage takePageShortage() {
        return PAGES.takeShortage();
    }

    /**
     * Gives pages to the section layers that wait for one and returns what has to be copied to the
     * GPU for them, up to the frame's budget. A section layer that does not fit keeps waiting, and
     * {@link #takePageShortage()} then says which buffer was too small and by how much.
     */
    static List<PageUpload> collectPageUploads(long byteBudget, int pageBudget) {
        List<PageUpload> uploads = new ArrayList<>();
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        long bytes = 0L;
        for (SectionLayerKey key : PAGES.pendingKeys()) {
            if (uploads.size() >= pageBudget || !uploads.isEmpty() && bytes >= byteBudget) {
                break;
            }
            CapturedSection section = SECTIONS.get(key.mesh());
            ChunkSectionLayer chunkLayer = layers[key.layerOrdinal()];
            LayerCapture layer = section == null ? null : section.layers.get(chunkLayer);
            if (layer == null) {
                PAGES.release(key);
                if (section != null) {
                    section.binding(key.layerOrdinal()).page = null;
                }
                continue;
            }
            // Allocating gives back the page the section layer had, so nothing may refer to it after this.
            section.binding(key.layerOrdinal()).page = null;
            ResidentTerrainPages.Page page = PAGES.allocate(key, key.sectionNode(), key.layerOrdinal(), layer.buffers.vertexBytes(), layer.buffers.indexBytes(), layer.meshlets.size(), layer.customIndexBuffer);
            if (page == null) {
                break;
            }
            section.binding(key.layerOrdinal()).page = page;
            int vertexByteOffset = Math.toIntExact(page.vertexOffset());
            int indexByteOffset = Math.toIntExact(page.indexOffset());
            ByteBuffer headers = ByteBuffer.allocate(layer.meshlets.size() * TerrainGpuLayout.MESHLET_HEADER_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
            for (TerrainMeshlet meshlet : layer.meshlets) {
                writeMeshlet(headers, meshlet, vertexByteOffset, indexByteOffset, layer.vertexBytes, layer.buffers.indexBytes());
            }
            headers.flip();
            ByteBuffer record = ByteBuffer.allocate(TerrainGpuLayout.SECTION_METADATA_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
            writeSectionLayer(record, key.sectionNode(), chunkLayer, page.firstMeshlet(), page.meshletCount(), layer, vertexByteOffset, indexByteOffset);
            record.flip();
            PageUpload upload = new PageUpload(page, layer.buffers.vertices().duplicate(), layer.buffers.indices().duplicate(), headers, record);
            uploads.add(upload);
            bytes += upload.bytes();
            GpuWorldDatabase.get().currentTerrainLayerUpdate(key.sectionNode(), key.layerOrdinal()).ifPresent(update -> GpuWorldTerrainLayerDirectory.get().publish(update, page.firstMeshlet(), page.meshletCount(), 1.0F));
        }
        return uploads;
    }
    
    
    public static int liveSectionCount() {
        return SECTIONS.size();
    }
    
    public static Map<String, Object> asMap() {
        int layers = 0;
        int meshlets = 0;
        long stagedVertexBytes = 0L;
        long stagedIndexBytes = 0L;
        for (CapturedSection section : SECTIONS.values()) {
            layers += section.layers.size();
            for (LayerCapture layer : section.layers.values()) {
                meshlets += layer.meshlets.size();
                stagedVertexBytes += layer.buffers.vertexBytes();
                stagedIndexBytes += layer.buffers.indexBytes();
            }
        }
        
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("liveSections", SECTIONS.size());
        map.put("capturedLayers", layers);
        map.put("meshletDescriptors", meshlets);
        map.put("stagedVertexBytes", stagedVertexBytes);
        map.put("stagedIndexBytes", stagedIndexBytes);
        map.put("targetVerticesPerMeshlet", TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET);
        map.put("targetTrianglesPerIndexedMeshlet", TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET);
        map.put("blockVertexStride", TerrainGpuLayout.BLOCK_VERTEX_STRIDE);
        map.put("normalizedIndexStride", TerrainGpuLayout.NORMALIZED_INDEX_STRIDE);
        map.put("terrainMaterialClassification", TerrainMaterialClassifier.asMap());
        Map<String, Object> frame = VISIBLE_FRAME.published();
        map.put("visibleSectionLayers", frame.get("entries"));
        map.put("visibleSectionLayersWithoutCapture", frame.get("uncaptured"));
        map.put("residentPages", PAGES.stats().asMap());
        map.put("vanillaChunkVisibilityFadeEnabled", TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled());
        map.put("sectionVisibility", Map.of("entries", frame.get("entries"), "belowOne", frame.get("belowOne"), "min", frame.get("min")));
        map.put("invalidationCount", INVALIDATION_COUNT.get());
        map.put("lastInvalidationReason", lastInvalidationReason);
        map.put("rejectedCaptures", REJECTED_CAPTURES.get());
        map.put("lastRejectedCapture", lastRejectedCapture);
        return map;
    }
    
    
    public static List<Map<String, Object>> sampleMeshlets(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CapturedSection section : SECTIONS.values()) {
            for (LayerCapture layer : section.layers.values()) {
                for (TerrainMeshlet meshlet : layer.meshlets) {
                    result.add(meshlet.asMap());
                    if (result.size() >= limit) {
                        return result;
                    }
                }
            }
        }
        return result;
    }
    
    private static int remaining(@Nullable ByteBuffer buffer) {
        return buffer == null ? 0 : buffer.remaining();
    }
    
    private static ByteBuffer copy(@Nullable ByteBuffer source) {
        if (source == null || source.remaining() == 0) {
            return ByteBuffer.allocateDirect(0);
        }
        ByteBuffer duplicate = source.duplicate();
        ByteBuffer copy = ByteBuffer.allocateDirect(duplicate.remaining());
        copy.put(duplicate);
        copy.flip();
        return copy;
    }
    
    private static ByteBuffer copyIndexBuffer(@Nullable ByteBuffer source, int sourceElementBytes) {
        if (source == null || source.remaining() == 0 || sourceElementBytes <= 0) {
            return ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN);
        }
        ByteBuffer duplicate = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int indexCount = duplicate.remaining() / sourceElementBytes;
        ByteBuffer copy = ByteBuffer.allocateDirect(indexCount * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        if (sourceElementBytes == Short.BYTES) {
            for (int i = 0; i < indexCount; i++) {
                copy.putInt(Short.toUnsignedInt(duplicate.getShort()));
            }
        }
        else if (sourceElementBytes == Integer.BYTES) {
            for (int i = 0; i < indexCount; i++) {
                copy.putInt(duplicate.getInt());
            }
        }
        copy.flip();
        return copy;
    }
    
    static CaptureValidation validateCapture(ChunkSectionLayer layer, ByteBuffer vertices, ByteBuffer indices, int vertexBytes, int indexBytes, int indexCount, boolean customIndexBuffer, int indexElementBytes) {
        if (indexCount <= 0) {
            return CaptureValidation.invalid("non-positive index count for " + layer.label() + ": " + indexCount);
        }
        if (vertexBytes <= 0) {
            return CaptureValidation.invalid("missing vertex payload for " + layer.label());
        }
        if (vertexBytes % TerrainGpuLayout.BLOCK_VERTEX_STRIDE != 0) {
            return CaptureValidation.invalid("vertex payload is not block-vertex aligned for " + layer.label() + ": bytes=" + vertexBytes);
        }
        if (vertices.remaining() != vertexBytes) {
            return CaptureValidation.invalid("vertex payload byte count mismatch for " + layer.label() + ": remaining=" + vertices.remaining() + ", expected=" + vertexBytes);
        }
        
        int vertexCount = vertexBytes / TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
        CaptureValidation vertexValidation = validateVertexSamples(layer, vertices, vertexCount);
        if (!vertexValidation.valid()) {
            return vertexValidation;
        }
        
        if (!customIndexBuffer) {
            if (indexCount % 6 != 0) {
                return CaptureValidation.invalid("non-indexed quad index count is not divisible by 6 for " + layer.label() + ": " + indexCount);
            }
            int expectedVertexCount = estimateVertices(indexCount);
            if (expectedVertexCount != vertexCount) {
                return CaptureValidation.invalid("non-indexed vertex count mismatch for " + layer.label() + ": vertices=" + vertexCount + ", expected=" + expectedVertexCount + ", indexCount=" + indexCount);
            }
            return CaptureValidation.ok();
        }
        
        if (indexElementBytes != TerrainGpuLayout.NORMALIZED_INDEX_STRIDE) {
            return CaptureValidation.invalid("custom index payload is not normalized for " + layer.label() + ": indexElementBytes=" + indexElementBytes);
        }
        if (indexBytes <= 0 || indices.remaining() <= 0) {
            return CaptureValidation.invalid("missing custom index payload for " + layer.label());
        }
        if (indices.remaining() != indexCount * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE) {
            return CaptureValidation.invalid("custom index payload byte count mismatch for " + layer.label() + ": remaining=" + indices.remaining() + ", expected=" + (indexCount * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE));
        }
        if (indexCount % 3 != 0) {
            return CaptureValidation.invalid("custom index count is not triangle-aligned for " + layer.label() + ": " + indexCount);
        }
        
        ByteBuffer indexView = indices.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < indexCount; i++) {
            int sourceVertex = indexView.getInt();
            if (sourceVertex < 0 || sourceVertex >= vertexCount) {
                return CaptureValidation.invalid("custom index out of range for " + layer.label() + ": index=" + i + ", sourceVertex=" + sourceVertex + ", vertexCount=" + vertexCount);
            }
        }
        return CaptureValidation.ok();
    }
    
    private static CaptureValidation validateVertexSamples(ChunkSectionLayer layer, ByteBuffer vertices, int vertexCount) {
        ByteBuffer view = vertices.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int sampleCount = Math.min(vertexCount, 512);
        int step = Math.max(vertexCount / Math.max(sampleCount, 1), 1);
        for (int vertex = 0; vertex < vertexCount; vertex += step) {
            int offset = vertex * TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
            float x = view.getFloat(offset);
            float y = view.getFloat(offset + Float.BYTES);
            float z = view.getFloat(offset + Float.BYTES * 2);
            float u = view.getFloat(offset + 16);
            float v = view.getFloat(offset + 20);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(u) || !Float.isFinite(v)) {
                return CaptureValidation.invalid("non-finite vertex payload for " + layer.label() + ": vertex=" + vertex);
            }
            if (Math.abs(x) > 256.0F || Math.abs(y) > 256.0F || Math.abs(z) > 256.0F) {
                return CaptureValidation.invalid("vertex position outside conservative section-local bounds for " + layer.label() + ": vertex=" + vertex + ", position=" + x + "," + y + "," + z);
            }
        }
        return CaptureValidation.ok();
    }
    
    private static void removeLayerMappings(CapturedSection section, CompiledSectionMesh mesh, long sectionNode, int layerOrdinal) {
        PAGES.release(new SectionLayerKey(mesh, sectionNode, layerOrdinal));
        section.binding(layerOrdinal).page = null;
    }
    
    private static void recordRejectedCapture(long sectionNode, ChunkSectionLayer layer, String reason) {
        REJECTED_CAPTURES.incrementAndGet();
        lastRejectedCapture = "sectionNode=" + sectionNode + ", layer=" + layer.label() + ", reason=" + reason;
    }
    
    private static int estimateVertices(int indexCount) {
        return Math.max(1, indexCount / 6 * 4);
    }
    
    private static int estimateIndexedVertices(int indexCount) {
        return Math.max(1, indexCount / 6 * 4);
    }
    
    private static int estimateMeshlets(int estimatedVertices) {
        return Math.max(1, (estimatedVertices + TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET - 1) / TerrainGpuLayout.TARGET_VERTICES_PER_MESHLET);
    }
    
    private static int estimateIndexedMeshlets(int indexCount) {
        int triangles = Math.max(1, indexCount / 3);
        return Math.max(1, (triangles + TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET - 1) / TerrainGpuLayout.TARGET_TRIANGLES_PER_INDEXED_MESHLET);
    }
    
    private static List<TerrainMeshlet> buildMeshlets(long sectionNode, ChunkSectionLayer layer, int estimatedVertexCount, int indexCount, int meshletCount) {
        List<TerrainMeshlet> meshlets = new ArrayList<>(meshletCount);
        for (int i = 0; i < meshletCount; i++) {
            meshlets.add(TerrainMeshlet.from(sectionNode, layer, i, meshletCount, estimatedVertexCount, indexCount));
        }
        return List.copyOf(meshlets);
    }
    
    private static List<TerrainMeshlet> buildIndexedMeshlets(long sectionNode, ChunkSectionLayer layer, int indexCount, int meshletCount) {
        List<TerrainMeshlet> meshlets = new ArrayList<>(meshletCount);
        for (int i = 0; i < meshletCount; i++) {
            meshlets.add(TerrainMeshlet.fromIndexedTriangles(sectionNode, layer, i, meshletCount, indexCount));
        }
        return List.copyOf(meshlets);
    }
    
    private static void writeSectionLayer(ByteBuffer target, long sectionNode, ChunkSectionLayer layer, int firstMeshlet, int meshletCount, LayerCapture capture, int vertexByteOffset, int indexByteOffset) {
        target.putLong(sectionNode);
        target.putInt(net.minecraft.core.SectionPos.x(sectionNode));
        target.putInt(net.minecraft.core.SectionPos.y(sectionNode));
        target.putInt(net.minecraft.core.SectionPos.z(sectionNode));
        target.putInt(layer.ordinal());
        target.putInt(firstMeshlet);
        target.putInt(meshletCount);
        target.putInt(capture.estimatedVertices);
        target.putInt(capture.estimatedMeshlets);
        target.putInt(capture.vertexBytes);
        target.putInt(capture.indexBytes);
        target.putInt(vertexByteOffset);
        target.putInt(indexByteOffset);
        target.putInt(0);
        target.putInt(capture.customIndexBuffer ? 1 : 0);
        target.putInt(0);
        target.putInt(0);
        target.putLong(0L);
    }
    
    private static void writeMeshlet(ByteBuffer target, TerrainMeshlet meshlet, int vertexByteOffset, int indexByteOffset, int vertexBytes, int indexBytes) {
        target.putLong(meshlet.sectionNode());
        target.putInt(meshlet.sectionX());
        target.putInt(meshlet.sectionY());
        target.putInt(meshlet.sectionZ());
        target.putInt(meshlet.layer().ordinal());
        target.putInt(meshlet.firstVertex());
        target.putInt(meshlet.vertexCount());
        target.putInt(meshlet.firstIndex());
        target.putInt(meshlet.indexCount());
        target.putInt(vertexByteOffset);
        target.putInt(indexByteOffset);
        target.putInt(vertexBytes);
        target.putInt(indexBytes);
        target.putInt(meshlet.indexCount() > 0 && indexBytes > 0 ? TerrainGpuLayout.INDEXED_TRIANGLES_FLAG : 0);
        target.putInt(meshlet.materialId());
    }
    
    /**
     * Implemented on Minecraft's compiled section mesh by a mixin: the section the mod captured from
     * the mesh, so a frame finds it without looking the mesh up.
     */
    public interface CaptureHolder {
        @Nullable Object vim$capturedSection();

        void vim$setCapturedSection(@Nullable Object section);
    }

    private static final class CapturedSection {
        private final long sectionNode;
        private final EnumMap<ChunkSectionLayer, LayerCapture> layers = new EnumMap<>(ChunkSectionLayer.class);
        private final PageBinding[] bindings = new PageBinding[ChunkSectionLayer.values().length];

        private CapturedSection(long sectionNode) {
            this.sectionNode = sectionNode;
            for (int layer = 0; layer < this.bindings.length; layer++) {
                this.bindings[layer] = new PageBinding();
            }
        }

        private PageBinding binding(int layerOrdinal) {
            return this.bindings[layerOrdinal];
        }

        private void clearBindings() {
            for (PageBinding binding : this.bindings) {
                binding.page = null;
            }
        }
    }

    /**
     * The page a section layer has on the GPU now, or {@code null}. Set when the page is handed out
     * and cleared when it is given back, so a frame reads it without looking the section layer up.
     */
    private static final class PageBinding {
        private volatile ResidentTerrainPages.@Nullable Page page;
    }
    
    private record LayerCapture(int vertexBytes, int indexBytes, int estimatedVertices, int estimatedMeshlets, boolean customIndexBuffer, int indexElementBytes, List<TerrainMeshlet> meshlets, MeshLayerBuffers buffers) {
    }
    
    private record MeshLayerBuffers(ByteBuffer vertices, ByteBuffer indices) {
        private int vertexBytes() {
            return this.vertices.remaining();
        }
        
        private int indexBytes() {
            return this.indices.remaining();
        }
    }
    
    public record MeshletRange(long sectionNode, int offset, int count, float visibility) {
    }
    
    /**
     * A page as a frame refers to it: its slot in the GPU page table and how far its section has
     * faded in.
     */
    public record PageRef(int slot, float visibility) {
    }
    
    /**
     * What has to be copied to the GPU for one page: its payload, the headers of its meshlets and
     * its record in the page table.
     */
    record PageUpload(ResidentTerrainPages.Page page, ByteBuffer vertices, ByteBuffer indices, ByteBuffer meshletHeaders, ByteBuffer pageRecord) {
        long bytes() {
            return (long) this.vertices.remaining() + this.indices.remaining() + this.meshletHeaders.remaining() + this.pageRecord.remaining();
        }
    }
    
    private record PageCapacity(int pages, int meshlets, long vertexBytes, long indexBytes) {
    }
    
    private record SectionLayerKey(CompiledSectionMesh mesh, long sectionNode, int layerOrdinal) {
    }
    
    /**
     * The section layers Minecraft collected for the frame being prepared, per layer and in its
     * draw order, each with how far its section has faded in. Minecraft makes a section's draw
     * data right after it collects the first of the section's layers, so that entry learns its
     * fade-in after it is added and the section's later layers when they are. Written and read on
     * the render thread only, without a lock: it is touched for every section layer of a frame.
     * Diagnostics read the counts it publishes when a frame's collection ends.
     */
    private static final class VisibleFrame {
        private final LayerEntries[] layers = new LayerEntries[ChunkSectionLayer.values().length];
        private final int[] uncaptured = new int[ChunkSectionLayer.values().length];
        private boolean open;
        private volatile boolean takeover;
        private volatile Map<String, Object> published = Map.of("entries", 0, "uncaptured", 0, "belowOne", 0, "min", 1.0F);
        private long currentSection = Long.MIN_VALUE;
        private float currentVisibility = 1.0F;
        private int lastLayer = -1;

        private VisibleFrame() {
            for (int layer = 0; layer < this.layers.length; layer++) {
                this.layers[layer] = new LayerEntries();
            }
        }

        private void begin(boolean takeoverFrame) {
            clear();
            this.takeover = takeoverFrame;
            this.open = true;
        }

        private boolean takeover() {
            return this.takeover;
        }

        private boolean take(int layerOrdinal, PageBinding binding, long sectionNode, float visibility) {
            if (!this.open || !this.takeover) {
                return false;
            }
            this.layers[layerOrdinal].add(binding, sectionNode, visibility);
            return true;
        }

        private void end() {
            this.open = false;
            this.published = Map.of("entries", sectionLayerCount(), "uncaptured", uncapturedCount(), "belowOne", belowOne(), "min", minVisibility());
        }
        
        private Map<String, Object> published() {
            return this.published;
        }
        
        private int belowOne() {
            int count = 0;
            for (LayerEntries layer : this.layers) {
                for (int i = 0; i < layer.size; i++) {
                    if (layer.visibility[i] < 0.999F) {
                        count++;
                    }
                }
            }
            return count;
        }
        
        private float minVisibility() {
            float min = 1.0F;
            for (LayerEntries layer : this.layers) {
                for (int i = 0; i < layer.size; i++) {
                    min = Math.min(min, layer.visibility[i]);
                }
            }
            return min;
        }

        private void clear() {
            for (LayerEntries layer : this.layers) {
                layer.clear();
            }
            Arrays.fill(this.uncaptured, 0);
            this.currentSection = Long.MIN_VALUE;
            this.currentVisibility = 1.0F;
            this.lastLayer = -1;
        }

        private void addUncaptured(int layerOrdinal) {
            if (this.open) {
                this.uncaptured[layerOrdinal]++;
            }
        }

        private void add(int layerOrdinal, PageBinding binding, long sectionNode) {
            if (!this.open) {
                return;
            }
            this.layers[layerOrdinal].add(binding, sectionNode, sectionNode == this.currentSection ? this.currentVisibility : 1.0F);
            this.lastLayer = layerOrdinal;
        }

        private void sectionVisibility(long sectionNode, float visibility) {
            if (!this.open || this.takeover) {
                return;
            }
            this.currentSection = sectionNode;
            this.currentVisibility = visibility;
            if (this.lastLayer >= 0) {
                this.layers[this.lastLayer].setLastVisibility(sectionNode, visibility);
            }
        }

        private @Nullable List<PageRef> pages(int layerOrdinal) {
            if (layerOrdinal < 0 || layerOrdinal >= this.layers.length || !this.takeover && this.uncaptured[layerOrdinal] > 0) {
                return null;
            }
            LayerEntries entries = this.layers[layerOrdinal];
            List<PageRef> pages = new ArrayList<>(entries.size);
            for (int i = 0; i < entries.size; i++) {
                ResidentTerrainPages.Page page = entries.bindings[i].page;
                if (page == null || page.meshletCount() <= 0) {
                    // A taken section layer whose page went away this frame is left out: Minecraft
                    // no longer draws it either, which is a hole for one frame, not a stale page.
                    if (this.takeover) {
                        continue;
                    }
                    return null;
                }
                pages.add(new PageRef(page.slot(), entries.visibility[i]));
            }
            return pages;
        }

        private @Nullable List<MeshletRange> meshletRanges(int layerOrdinal) {
            if (layerOrdinal < 0 || layerOrdinal >= this.layers.length || !this.takeover && this.uncaptured[layerOrdinal] > 0) {
                return null;
            }
            LayerEntries entries = this.layers[layerOrdinal];
            List<MeshletRange> ranges = new ArrayList<>(entries.size);
            for (int i = 0; i < entries.size; i++) {
                ResidentTerrainPages.Page page = entries.bindings[i].page;
                if (page == null || page.meshletCount() <= 0) {
                    if (this.takeover) {
                        continue;
                    }
                    return null;
                }
                ranges.add(new MeshletRange(page.sectionNode(), page.firstMeshlet(), page.meshletCount(), entries.visibility[i]));
            }
            return ranges;
        }

        private int sectionLayerCount() {
            int count = 0;
            for (LayerEntries layer : this.layers) {
                count += layer.size;
            }
            return count;
        }

        private int uncapturedCount() {
            return Arrays.stream(this.uncaptured).sum();
        }
    }

    /**
     * One layer's entries of a frame, in parallel arrays that are reused from frame to frame.
     */
    private static final class LayerEntries {
        private PageBinding[] bindings = new PageBinding[256];
        private long[] sectionNodes = new long[256];
        private float[] visibility = new float[256];
        private int size;

        private void add(PageBinding binding, long sectionNode, float sectionVisibility) {
            if (this.size == this.bindings.length) {
                int capacity = this.size * 2;
                this.bindings = Arrays.copyOf(this.bindings, capacity);
                this.sectionNodes = Arrays.copyOf(this.sectionNodes, capacity);
                this.visibility = Arrays.copyOf(this.visibility, capacity);
            }
            this.bindings[this.size] = binding;
            this.sectionNodes[this.size] = sectionNode;
            this.visibility[this.size] = sectionVisibility;
            this.size++;
        }

        private void setLastVisibility(long sectionNode, float sectionVisibility) {
            if (this.size > 0 && this.sectionNodes[this.size - 1] == sectionNode) {
                this.visibility[this.size - 1] = sectionVisibility;
            }
        }

        private void clear() {
            Arrays.fill(this.bindings, 0, this.size, null);
            this.size = 0;
        }
    }

    private static final class CaptureResult {
        private boolean accepted;
        private String reason = "";
    }
    
    record CaptureValidation(boolean valid, String reason) {
        private static CaptureValidation ok() {
            return new CaptureValidation(true, "");
        }
        
        private static CaptureValidation invalid(String reason) {
            return new CaptureValidation(false, reason);
        }
    }
}