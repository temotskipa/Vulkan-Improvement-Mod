package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDatabase;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldTerrainLayerDirectory;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainMeshletHeaderRecord;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainResidentPageRecord;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class SectionMeshletStore {
    static final ChunkSectionLayer[] LAYERS = ChunkSectionLayer.values();
    /**
     * What a frame does with the section of a compiled mesh, kept on the mesh: not yet known,
     * nothing because the mesh draws nothing, leave it to Minecraft because it draws a layer the
     * GPU-driven draw does not, or leave it out of Minecraft's collection because the GPU draws it
     * whole.
     */
    private static final byte ROLE_UNKNOWN = 0;
    private static final byte ROLE_NOTHING = 1;
    static final byte ROLE_MINECRAFT = 2;
    static final byte ROLE_GPU = 3;
    /**
     * The start of the clock the GPU fades sections in by. Minecraft's clock counts milliseconds
     * from an arbitrary start, which an int on the GPU cannot hold; this one starts with the game.
     */
    private static final long FADE_EPOCH_MILLIS = Util.getMillis();
    private static final MemorySegment EMPTY = MemorySegment.ofArray(new byte[0]);
    private static final Map<CompiledSectionMesh, CapturedSection> SECTIONS = new ConcurrentHashMap<>();
    private static final MeshletVisibleFrame VISIBLE_FRAME = new MeshletVisibleFrame();
    private static final ResidentTerrainPages<SectionLayerKey> PAGES = new ResidentTerrainPages<>();
    private static final AtomicLong INVALIDATION_COUNT = new AtomicLong();
    private static final AtomicLong REJECTED_CAPTURES = new AtomicLong();
    private static volatile MeshletPageCapacity pageCapacity = new MeshletPageCapacity(0, 0, 0L, 0L);
    private static volatile String lastInvalidationReason = "";
    private static volatile String lastRejectedCapture = "";
    
    private SectionMeshletStore() {
    }
    
    public static void capture(long sectionNode, CompiledSectionMesh mesh, ChunkSectionLayer layer, @Nullable ByteBuffer vertexBuffer, @Nullable ByteBuffer indexBuffer) {
        long startedNanos = System.nanoTime();
        MemorySegment vertexSource = vertexBuffer == null ? null : MemorySegment.ofBuffer(vertexBuffer);
        MemorySegment indexSource = indexBuffer == null ? null : MemorySegment.ofBuffer(indexBuffer);
        int vertexBytes = bytes(vertexSource);
        int indexBytes = bytes(indexSource);
        var draw = mesh.getSectionDraw(layer);
        int indexCount = draw == null ? 0 : draw.indexCount();
        boolean customIndexBuffer = draw != null && draw.hasCustomIndexBuffer();
        int indexElementBytes = draw == null ? 0 : draw.indexType().bytes;
        int estimatedVertices = customIndexBuffer ? estimateIndexedVertices(indexCount) : estimateVertices(indexCount);
        // Quads are grouped by the way they face, so the task stage can drop a group seen from behind.
        // Grouping copies them out of Minecraft's buffer; other vertices are copied as they are.
        BuiltQuadMeshlets quads = customIndexBuffer || vertexSource == null || vertexBytes == 0 ? null : TerrainQuadMeshlets.build(sectionNode, layer, vertexSource);
        List<TerrainMeshlet> meshlets;
        MemorySegment capturedVertices;
        if (quads != null) {
            meshlets = quads.meshlets();
            capturedVertices = quads.vertices();
        }
        else {
            int countedMeshlets = customIndexBuffer ? estimateIndexedMeshlets(indexCount) : estimateMeshlets(estimatedVertices);
            meshlets = customIndexBuffer ? buildIndexedMeshlets(sectionNode, layer, indexCount, countedMeshlets) : buildMeshlets(sectionNode, layer, estimatedVertices, indexCount, countedMeshlets);
            capturedVertices = copy(vertexSource);
        }
        int estimatedMeshlets = meshlets.size();
        int drawLayers = drawLayers(mesh);
        MemorySegment copiedIndices = customIndexBuffer ? copyIndexBuffer(indexSource, indexElementBytes) : copy(indexSource);
        int gpuIndexElementBytes = customIndexBuffer && copiedIndices.byteSize() > 0L ? TerrainGpuLayout.NORMALIZED_INDEX_STRIDE : indexElementBytes;
        SectionCaptureResult result = new SectionCaptureResult();
        SECTIONS.compute(mesh, (ignored, existing) -> {
            CapturedSection section = existing == null ? new CapturedSection(sectionNode) : existing;
            section.drawLayers = drawLayers;
            SectionLayerCapture previous = section.layers.get(layer);
            MemorySegment vertices = vertexBuffer == null && previous != null ? previous.buffers().vertices() : capturedVertices;
            // The meshlets describe the vertices they were cut from.
            List<TerrainMeshlet> layerMeshlets = vertexBuffer == null && previous != null ? previous.meshlets() : meshlets;
            MemorySegment indices = indexBuffer == null && previous != null ? previous.buffers().indices() : copiedIndices;
            int capturedVertexBytes = vertexBuffer == null && previous != null ? previous.vertexBytes() : vertexBytes;
            int capturedIndexBytes = indexBuffer == null && previous != null ? previous.indexBytes() : indexBytes;
            int capturedIndexElementBytes = gpuIndexElementBytes == 0 && previous != null ? previous.indexElementBytes() : gpuIndexElementBytes;
            SectionCaptureValidation validation = validateCapture(layer, vertices, indices, capturedVertexBytes, capturedIndexBytes, indexCount, customIndexBuffer, capturedIndexElementBytes);
            if (!validation.valid()) {
                section.layers.remove(layer);
                removeLayerMappings(section, mesh, sectionNode, layer.ordinal());
                result.reason = validation.reason();
                CapturedSection kept = section.layers.isEmpty() ? null : section;
                holdCapture(mesh, kept);
                return kept;
            }
            SectionMeshLayerBuffers buffers = new SectionMeshLayerBuffers(vertices, indices);
            section.layers.put(layer, new SectionLayerCapture(capturedVertexBytes, capturedIndexBytes, estimatedVertices, layerMeshlets.size(), customIndexBuffer, capturedIndexElementBytes, layerMeshlets, buffers));
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
        MeshletPageCapacity capacity = pageCapacity;
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
     * @param takeover  whether the mesh path takes the section layers it can draw out of Minecraft's
     *                  draw lists this frame; otherwise every section layer Minecraft draws is listed
     * @param gpuDriven whether the GPU draws every page of the frame's visible sections, so that a
     *                  taken section layer is not listed
     */
    public static void beginVisibleFrame(boolean takeover, boolean gpuDriven) {
        VISIBLE_FRAME.begin(takeover, gpuDriven);
    }
    
    /**
     * Whether a frame's section layers are being collected, between {@link #beginVisibleFrame} and
     * {@link #endVisibleFrame}. Minecraft's collection begins one only while the Vulkan backend is
     * active and terrain capture is on.
     */
    public static boolean collectingFrame() {
        return VISIBLE_FRAME.open;
    }
    
    /**
     * Whether a frame is being collected in which the mesh path takes section layers out of
     * Minecraft's draw lists.
     */
    public static boolean collectingTakeoverFrame() {
        return VISIBLE_FRAME.collectingTakeover();
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
        CapturedSection capturedSection = (Object) compiledSectionMesh instanceof SectionMeshletCaptureHolder holder ? (CapturedSection) holder.vim$capturedSection() : SECTIONS.get(compiledSectionMesh);
        if (capturedSection == null) {
            return false;
        }
        MeshletPageBinding binding = capturedSection.binding(layer.ordinal());
        ResidentTerrainPage page = binding.page;
        if (page == null || page.meshletCount() <= 0 || page.meshletCount() > TerrainGpuLayout.MAX_PAGE_MESHLETS) {
            return false;
        }
        if (VISIBLE_FRAME.gpuDriven()) {
            // The GPU draws every page of the frame's visible sections; nothing is listed.
            return true;
        }
        float visibility = TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled() ? Math.clamp(section.getVisibility(now, fadeDuration), 0.0F, 1.0F) : 1.0F;
        return VISIBLE_FRAME.take(layer.ordinal(), binding, page, capturedSection.sectionNode, visibility);
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
        if (draw == null || draw.hasCustomIndexBuffer() && Objects.isNull(slice.indexBuffer())) {
            return;
        }
        CapturedSection capturedSection = (Object) compiledSectionMesh instanceof SectionMeshletCaptureHolder holder ? (CapturedSection) holder.vim$capturedSection() : SECTIONS.get(compiledSectionMesh);
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
     * Keeps on the mesh the section captured from it, and what a frame does with that section.
     */
    private static void holdCapture(CompiledSectionMesh mesh, @Nullable CapturedSection section) {
        if ((Object) mesh instanceof SectionMeshletCaptureHolder holder) {
            holder.vim$setCapturedSection(section);
            updateFrameRole(holder, section);
        }
    }
    
    /**
     * Works out what a frame does with a mesh's section from the pages its layers have: the GPU
     * draws the section whole when each layer it draws has a page the GPU-driven draw can draw.
     * Called whenever one of those pages is handed out or given back. A mesh nothing was captured
     * from is worked out again when a frame first meets it.
     */
    private static void updateFrameRole(SectionMeshletCaptureHolder holder, @Nullable CapturedSection section) {
        if (section == null) {
            holder.vim$setFrameRole(ROLE_UNKNOWN);
            return;
        }
        section.updateFrameRole(holder);
    }
    
    private static void updateFrameRole(CompiledSectionMesh mesh, CapturedSection section) {
        if ((Object) mesh instanceof SectionMeshletCaptureHolder holder && holder.vim$capturedSection() == section) {
            updateFrameRole(holder, section);
        }
    }
    
    private static int drawLayers(SectionMesh sectionMesh) {
        int layers = 0;
        for (ChunkSectionLayer layer : LAYERS) {
            if (sectionMesh.getSectionDraw(layer) != null) {
                layers |= 1 << layer.ordinal();
            }
        }
        return layers;
    }
    
    /**
     * Whether Minecraft has to collect a visible section in a GPU-driven frame: its mesh draws a
     * layer the GPU does not. Called for every visible section of such a frame, so it reads what
     * the mesh keeps and works it out only for a mesh nothing was captured from.
     */
    public static boolean needsMinecraft(SectionMesh sectionMesh) {
        if (!(sectionMesh instanceof SectionMeshletCaptureHolder holder)) {
            return drawLayers(sectionMesh) != 0;
        }
        byte role = holder.vim$frameRole();
        if (role == ROLE_UNKNOWN) {
            role = drawLayers(sectionMesh) != 0 ? ROLE_MINECRAFT : ROLE_NOTHING;
            holder.vim$setFrameRole(role);
        }
        return role == ROLE_MINECRAFT;
    }
    
    /**
     * Called when Minecraft shows a compiled mesh in its section, with the time the section was
     * first shown, which its fade-in counts from. Until then the mesh it replaces is the one drawn,
     * so the new mesh's layers get pages only from now on.
     */
    public static void meshShown(CompiledSectionMesh mesh, long sectionShownMillis) {
        if ((Object) mesh instanceof SectionMeshletCaptureHolder holder) {
            holder.vim$setShownSince(sectionShownMillis != 0L ? sectionShownMillis : Util.getMillis());
            if (holder.vim$capturedSection() != null) {
                DescriptorHeapTerrainResources.get().markTerrainDataDirty();
            }
        }
    }
    
    private static boolean shown(CompiledSectionMesh mesh) {
        return !((Object) mesh instanceof SectionMeshletCaptureHolder holder) || holder.vim$shownSince() != 0L;
    }
    
    /**
     * When a mesh's section began to fade in, on the GPU's fade-in clock.
     */
    private static int fadeStart(CompiledSectionMesh mesh) {
        long shownSince = (Object) mesh instanceof SectionMeshletCaptureHolder holder ? holder.vim$shownSince() : 0L;
        return shownSince == 0L ? 0 : (int) (shownSince - FADE_EPOCH_MILLIS);
    }
    
    /**
     * The time now on the GPU's fade-in clock.
     */
    static int fadeClockMillis() {
        return (int) (Util.getMillis() - FADE_EPOCH_MILLIS);
    }
    
    /**
     * Called as Minecraft makes the draw data of a section it collects for the frame, right after
     * the first of the section's layers it draws; the fade-in applies to all of them.
     */
    public static void recordSectionVisibility(int blockX, int blockY, int blockZ, float visibility) {
        long sectionNode = net.minecraft.core.SectionPos.asLong(net.minecraft.core.SectionPos.blockToSectionCoord(blockX), net.minecraft.core.SectionPos.blockToSectionCoord(blockY), net.minecraft.core.SectionPos.blockToSectionCoord(blockZ));
        VISIBLE_FRAME.sectionVisibility(sectionNode, TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled() ? Math.clamp(visibility, 0.0F, 1.0F) : 1.0F);
    }
    
    /**
     * The pages of the section layers Minecraft draws this frame for a layer, or {@code null} when
     * one of them has no page on the GPU yet. The list is the layer's own and is filled again by
     * the next call.
     */
    public static @Nullable MeshletPageList visiblePages(int layerOrdinal) {
        return VISIBLE_FRAME.pages(layerOrdinal);
    }
    
    /**
     * Every page a layer has on the GPU as a range of meshlets, whether or not Minecraft draws
     * its section this frame.
     */
    public static List<MeshletRange> meshletRangesForLayer(int layerOrdinal) {
        ArrayList<MeshletRange> ranges = new ArrayList<>();
        for (ResidentTerrainPage page : PAGES.snapshot().values()) {
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
        pageCapacity = new MeshletPageCapacity(pages, meshlets, vertexBytes, indexBytes);
        PAGES.reset(pages, meshlets, vertexBytes, indexBytes);
        GpuWorldTerrainLayerDirectory.get().clear("resident terrain pages reset");
        for (Map.Entry<CompiledSectionMesh, CapturedSection> section : SECTIONS.entrySet()) {
            section.getValue().clearBindings();
            updateFrameRole(section.getKey(), section.getValue());
            for (ChunkSectionLayer layer : section.getValue().layers.keySet()) {
                PAGES.markPending(new SectionLayerKey(section.getKey(), section.getValue().sectionNode, layer.ordinal()));
            }
        }
    }
    
    static ResidentPageStats residentPageStats() {
        return PAGES.stats();
    }
    
    static ResidentPageShortage takePageShortage() {
        return PAGES.takeShortage();
    }
    
    static ResidentPageTableChanges takePageTableChanges() {
        return PAGES.takeTableChanges();
    }
    
    /**
     * One more than the highest page-table slot in use since the buffers were made.
     */
    static int pageSlotHighWater() {
        return PAGES.slotHighWater();
    }
    
    /**
     * How many tasks drawing every page of a layer would launch, at most.
     */
    static int layerTasks(int layerOrdinal) {
        return PAGES.tasks(layerOrdinal);
    }
    
    /**
     * Gives pages to the section layers that wait for one and returns what has to be copied to the
     * GPU for them, up to the frame's budget. A section layer that does not fit keeps waiting, and
     * {@link #takePageShortage()} then says which buffer was too small and by how much. So does one
     * whose mesh Minecraft does not show yet: a GPU-driven frame draws every page of a visible
     * section, so a section has the pages of one mesh only.
     */
    static List<MeshletPageUpload> collectPageUploads(long byteBudget, int pageBudget) {
        List<MeshletPageUpload> uploads = new ArrayList<>();
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        long bytes = 0L;
        for (SectionLayerKey key : PAGES.pendingKeys()) {
            if (uploads.size() >= pageBudget || !uploads.isEmpty() && bytes >= byteBudget) {
                break;
            }
            CapturedSection section = SECTIONS.get(key.mesh());
            ChunkSectionLayer chunkLayer = layers[key.layerOrdinal()];
            SectionLayerCapture layer = section == null ? null : section.layers.get(chunkLayer);
            if (layer == null) {
                PAGES.release(key);
                if (section != null) {
                    section.binding(key.layerOrdinal()).page = null;
                    updateFrameRole(key.mesh(), section);
                }
                continue;
            }
            if (!shown(key.mesh())) {
                continue;
            }
            // Allocating gives back the page the section layer had, so nothing may refer to it after this.
            section.binding(key.layerOrdinal()).page = null;
            ResidentTerrainPage page = PAGES.allocate(key, key.sectionNode(), key.layerOrdinal(), layer.buffers().vertexBytes(), layer.buffers().indexBytes(), layer.meshlets().size(), layer.customIndexBuffer());
            if (page == null) {
                updateFrameRole(key.mesh(), section);
                break;
            }
            section.binding(key.layerOrdinal()).page = page;
            updateFrameRole(key.mesh(), section);
            long vertexByteOffset = page.vertexOffset();
            long indexByteOffset = page.indexOffset();
            MemorySegment headers = MemorySegment.ofArray(new byte[layer.meshlets().size() * TerrainGpuLayout.MESHLET_HEADER_STRIDE]);
            long header = 0L;
            for (TerrainMeshlet meshlet : layer.meshlets()) {
                writeMeshlet(headers.asSlice(header, TerrainGpuLayout.MESHLET_HEADER_STRIDE), meshlet, vertexByteOffset, indexByteOffset, layer.vertexBytes(), layer.buffers().indexBytes());
                header += TerrainGpuLayout.MESHLET_HEADER_STRIDE;
            }
            MemorySegment record = MemorySegment.ofArray(new byte[TerrainGpuLayout.SECTION_METADATA_STRIDE]);
            writeSectionLayer(record, key.sectionNode(), chunkLayer, page.firstMeshlet(), page.meshletCount(), layer, vertexByteOffset, indexByteOffset, fadeStart(key.mesh()));
            MeshletPageUpload upload = new MeshletPageUpload(page, layer.buffers().vertices(), layer.buffers().indices(), headers, record);
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
            for (SectionLayerCapture layer : section.layers.values()) {
                meshlets += layer.meshlets().size();
                stagedVertexBytes += layer.buffers().vertexBytes();
                stagedIndexBytes += layer.buffers().indexBytes();
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
        map.put("gpuDrivenFrame", frame.get("gpuDriven"));
        map.put("residentPages", PAGES.stats().asMap());
        map.put("vanillaChunkVisibilityFadeEnabled", TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled());
        map.put("sectionVisibility", Map.of("entries", frame.get("entries"), "belowOne", frame.get("belowOne"), "min", frame.get("min")));
        map.put("invalidationCount", INVALIDATION_COUNT.get());
        map.put("lastInvalidationReason", lastInvalidationReason);
        map.put("rejectedCaptures", REJECTED_CAPTURES.get());
        map.put("lastRejectedCapture", lastRejectedCapture);
        return map;
    }
    
    static int bytes(@Nullable MemorySegment segment) {
        return segment == null ? 0 : Math.toIntExact(segment.byteSize());
    }
    
    /**
     * A copy of what Minecraft hands over, which it reuses once the upload is done. Copies live
     * outside the Java heap until nothing refers to them any more, as Minecraft's buffers do.
     */
    private static MemorySegment copy(@Nullable MemorySegment source) {
        if (source == null || source.byteSize() == 0L) {
            return EMPTY;
        }
        return Arena.ofAuto().allocate(source.byteSize()).copyFrom(source);
    }
    
    /**
     * A copy of Minecraft's indices, each widened to an int.
     */
    private static MemorySegment copyIndexBuffer(@Nullable MemorySegment source, int sourceElementBytes) {
        if (source == null || source.byteSize() == 0L || sourceElementBytes <= 0) {
            return EMPTY;
        }
        long indexCount = source.byteSize() / sourceElementBytes;
        MemorySegment copy = Arena.ofAuto().allocate(indexCount * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE);
        if (sourceElementBytes == Short.BYTES) {
            for (long i = 0; i < indexCount; i++) {
                copy.setAtIndex(TerrainGpuRecords.INT, i, Short.toUnsignedInt(source.getAtIndex(TerrainGpuRecords.SHORT, i)));
            }
        }
        else if (sourceElementBytes == Integer.BYTES) {
            MemorySegment.copy(source, 0L, copy, 0L, copy.byteSize());
        }
        return copy;
    }
    
    static SectionCaptureValidation validateCapture(ChunkSectionLayer layer, MemorySegment vertices, MemorySegment indices, int vertexBytes, int indexBytes, int indexCount, boolean customIndexBuffer, int indexElementBytes) {
        if (indexCount <= 0) {
            return SectionCaptureValidation.invalid("non-positive index count for " + layer.label() + ": " + indexCount);
        }
        if (vertexBytes <= 0) {
            return SectionCaptureValidation.invalid("missing vertex payload for " + layer.label());
        }
        if (vertexBytes % TerrainGpuLayout.BLOCK_VERTEX_STRIDE != 0) {
            return SectionCaptureValidation.invalid("vertex payload is not block-vertex aligned for " + layer.label() + ": bytes=" + vertexBytes);
        }
        if (vertices.byteSize() != vertexBytes) {
            return SectionCaptureValidation.invalid("vertex payload byte count mismatch for " + layer.label() + ": bytes=" + vertices.byteSize() + ", expected=" + vertexBytes);
        }
        
        int vertexCount = vertexBytes / TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
        SectionCaptureValidation vertexValidation = validateVertexSamples(layer, vertices, vertexCount);
        if (!vertexValidation.valid()) {
            return vertexValidation;
        }
        
        if (!customIndexBuffer) {
            if (indexCount % 6 != 0) {
                return SectionCaptureValidation.invalid("non-indexed quad index count is not divisible by 6 for " + layer.label() + ": " + indexCount);
            }
            int expectedVertexCount = estimateVertices(indexCount);
            if (expectedVertexCount != vertexCount) {
                return SectionCaptureValidation.invalid("non-indexed vertex count mismatch for " + layer.label() + ": vertices=" + vertexCount + ", expected=" + expectedVertexCount + ", indexCount=" + indexCount);
            }
            return SectionCaptureValidation.ok();
        }
        
        if (indexElementBytes != TerrainGpuLayout.NORMALIZED_INDEX_STRIDE) {
            return SectionCaptureValidation.invalid("custom index payload is not normalized for " + layer.label() + ": indexElementBytes=" + indexElementBytes);
        }
        if (indexBytes <= 0 || indices.byteSize() <= 0L) {
            return SectionCaptureValidation.invalid("missing custom index payload for " + layer.label());
        }
        if (indices.byteSize() != (long) indexCount * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE) {
            return SectionCaptureValidation.invalid("custom index payload byte count mismatch for " + layer.label() + ": bytes=" + indices.byteSize() + ", expected=" + (long) indexCount * TerrainGpuLayout.NORMALIZED_INDEX_STRIDE);
        }
        if (indexCount % 3 != 0) {
            return SectionCaptureValidation.invalid("custom index count is not triangle-aligned for " + layer.label() + ": " + indexCount);
        }
        
        for (int i = 0; i < indexCount; i++) {
            int sourceVertex = indices.getAtIndex(TerrainGpuRecords.INT, i);
            if (sourceVertex < 0 || sourceVertex >= vertexCount) {
                return SectionCaptureValidation.invalid("custom index out of range for " + layer.label() + ": index=" + i + ", sourceVertex=" + sourceVertex + ", vertexCount=" + vertexCount);
            }
        }
        return SectionCaptureValidation.ok();
    }
    
    private static SectionCaptureValidation validateVertexSamples(ChunkSectionLayer layer, MemorySegment vertices, int vertexCount) {
        int sampleCount = Math.min(vertexCount, 512);
        int step = Math.max(vertexCount / Math.max(sampleCount, 1), 1);
        for (int vertex = 0; vertex < vertexCount; vertex += step) {
            long offset = (long) vertex * TerrainGpuLayout.BLOCK_VERTEX_STRIDE;
            float x = vertices.get(TerrainGpuRecords.FLOAT, offset);
            float y = vertices.get(TerrainGpuRecords.FLOAT, offset + Float.BYTES);
            float z = vertices.get(TerrainGpuRecords.FLOAT, offset + Float.BYTES * 2);
            float u = vertices.get(TerrainGpuRecords.FLOAT, offset + 16);
            float v = vertices.get(TerrainGpuRecords.FLOAT, offset + 20);
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(u) || !Float.isFinite(v)) {
                return SectionCaptureValidation.invalid("non-finite vertex payload for " + layer.label() + ": vertex=" + vertex);
            }
            if (Math.abs(x) > 256.0F || Math.abs(y) > 256.0F || Math.abs(z) > 256.0F) {
                return SectionCaptureValidation.invalid("vertex position outside conservative section-local bounds for " + layer.label() + ": vertex=" + vertex + ", position=" + x + "," + y + "," + z);
            }
        }
        return SectionCaptureValidation.ok();
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
    
    /**
     * Writes a page's record into zeroed memory, which its reserved members keep.
     */
    private static void writeSectionLayer(MemorySegment target, long sectionNode, ChunkSectionLayer layer, int firstMeshlet, int meshletCount, SectionLayerCapture capture, long vertexByteOffset, long indexByteOffset, int fadeStart) {
        target.set(TerrainGpuRecords.LONG, TerrainResidentPageRecord.SECTION_NODE, sectionNode);
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.SECTION_X, net.minecraft.core.SectionPos.x(sectionNode));
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.SECTION_Y, net.minecraft.core.SectionPos.y(sectionNode));
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.SECTION_Z, net.minecraft.core.SectionPos.z(sectionNode));
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.LAYER, layer.ordinal());
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.FIRST_MESHLET, firstMeshlet);
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.MESHLET_COUNT, meshletCount);
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.ESTIMATED_VERTICES, capture.estimatedVertices());
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.ESTIMATED_MESHLETS, capture.estimatedMeshlets());
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.VERTEX_BYTES, capture.vertexBytes());
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.INDEX_BYTES, capture.indexBytes());
        target.set(TerrainGpuRecords.LONG, TerrainResidentPageRecord.VERTEX_BYTE_OFFSET, vertexByteOffset);
        target.set(TerrainGpuRecords.LONG, TerrainResidentPageRecord.INDEX_BYTE_OFFSET, indexByteOffset);
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.FADE_START, fadeStart);
        target.set(TerrainGpuRecords.INT, TerrainResidentPageRecord.CUSTOM_INDEX_BUFFER, capture.customIndexBuffer() ? 1 : 0);
    }
    
    /**
     * Writes a meshlet's header into zeroed memory, which its reserved member keeps.
     */
    private static void writeMeshlet(MemorySegment target, TerrainMeshlet meshlet, long vertexByteOffset, long indexByteOffset, int vertexBytes, int indexBytes) {
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.SECTION_X, meshlet.sectionX());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.SECTION_Y, meshlet.sectionY());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.SECTION_Z, meshlet.sectionZ());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.LAYER, meshlet.layer().ordinal());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.FIRST_VERTEX, meshlet.firstVertex());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.VERTEX_COUNT, meshlet.vertexCount());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.FIRST_INDEX, meshlet.firstIndex());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.INDEX_COUNT, meshlet.indexCount());
        target.set(TerrainGpuRecords.LONG, TerrainMeshletHeaderRecord.VERTEX_BYTE_OFFSET, vertexByteOffset);
        target.set(TerrainGpuRecords.LONG, TerrainMeshletHeaderRecord.INDEX_BYTE_OFFSET, indexByteOffset);
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.VERTEX_BYTES, vertexBytes);
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.INDEX_BYTES, indexBytes);
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.FLAGS, meshlet.indexCount() > 0 && indexBytes > 0 ? TerrainGpuLayout.INDEXED_TRIANGLES_FLAG : 0);
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.MATERIAL_ID, meshlet.materialId());
        target.set(TerrainGpuRecords.FLOAT, TerrainMeshletHeaderRecord.BOUNDS_MIN, meshlet.minX());
        target.set(TerrainGpuRecords.FLOAT, TerrainMeshletHeaderRecord.BOUNDS_MIN + Float.BYTES, meshlet.minY());
        target.set(TerrainGpuRecords.FLOAT, TerrainMeshletHeaderRecord.BOUNDS_MIN + 2 * Float.BYTES, meshlet.minZ());
        target.set(TerrainGpuRecords.FLOAT, TerrainMeshletHeaderRecord.BOUNDS_MAX, meshlet.maxX());
        target.set(TerrainGpuRecords.FLOAT, TerrainMeshletHeaderRecord.BOUNDS_MAX + Float.BYTES, meshlet.maxY());
        target.set(TerrainGpuRecords.FLOAT, TerrainMeshletHeaderRecord.BOUNDS_MAX + 2 * Float.BYTES, meshlet.maxZ());
        target.set(TerrainGpuRecords.INT, TerrainMeshletHeaderRecord.FACING, meshlet.facing());
    }
    
    /**
     * How many tasks draw a page, one per {@link TerrainGpuLayout#MESHLETS_PER_TASK} of its meshlets.
     */
    public static int tasks(int meshletCount) {
        return (meshletCount + TerrainGpuLayout.MESHLETS_PER_TASK - 1) / TerrainGpuLayout.MESHLETS_PER_TASK;
    }
    
    /**
     * Whether the mesh path can draw a page: it has meshlets, and no more than its task records can
     * name.
     */
    static boolean drawable(@Nullable ResidentTerrainPage page) {
        return page != null && page.meshletCount() > 0 && page.meshletCount() <= TerrainGpuLayout.MAX_PAGE_MESHLETS;
    }
}