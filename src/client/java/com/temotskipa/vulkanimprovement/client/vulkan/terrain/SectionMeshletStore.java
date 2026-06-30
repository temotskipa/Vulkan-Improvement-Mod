package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDatabase;
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

@SuppressWarnings("resource")
public final class SectionMeshletStore {
    private static final Map<CompiledSectionMesh, CapturedSection> SECTIONS = new ConcurrentHashMap<>();
    private static final Map<DrawKey, SectionLayerKey> DRAW_SLICES = new ConcurrentHashMap<>();
    private static final Map<SectionLayerKey, MeshletRange> GPU_MESHLET_RANGES = new ConcurrentHashMap<>();
    private static final Map<Long, Float> SECTION_VISIBILITY = new ConcurrentHashMap<>();
    private static final AtomicLong INVALIDATION_COUNT = new AtomicLong();
    private static final AtomicLong REJECTED_CAPTURES = new AtomicLong();
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
                removeLayerMappings(mesh, layer.ordinal());
                result.reason = validation.reason();
                return section.layers.isEmpty() ? null : section;
            }
            MeshLayerBuffers buffers = new MeshLayerBuffers(vertices, indices);
            section.layers.put(layer, new LayerCapture(capturedVertexBytes, capturedIndexBytes, estimatedVertices, estimatedMeshlets, customIndexBuffer, capturedIndexElementBytes, meshlets, buffers));
            result.accepted = true;
            return section;
        });
        if (result.accepted) {
            MeshTerrainRenderer.get().recordSectionCapture(vertexBytes, indexBytes, estimatedMeshlets, System.nanoTime() - startedNanos);
            GpuWorldDatabase.get().recordTerrainLayerCapture(sectionNode, layer.ordinal(), TerrainMaterialClassifier.materialIdForLayer(layer));
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        } else {
            recordRejectedCapture(sectionNode, layer, result.reason);
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
    }
    
    public static void release(CompiledSectionMesh mesh) {
        CapturedSection removed = SECTIONS.remove(mesh);
        if (removed != null) {
            DRAW_SLICES.entrySet().removeIf(entry -> entry.getValue().mesh() == mesh);
            GPU_MESHLET_RANGES.keySet().removeIf(key -> key.mesh() == mesh);
            MeshTerrainRenderer.get().recordSectionRelease();
            GpuWorldDatabase.get().recordSectionRelease(removed.sectionNode);
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
    }
    
    public static void clearAll(String reason) {
        boolean hadTerrainData = !SECTIONS.isEmpty() || !DRAW_SLICES.isEmpty() || !GPU_MESHLET_RANGES.isEmpty() || !SECTION_VISIBILITY.isEmpty();
        SECTIONS.clear();
        DRAW_SLICES.clear();
        GPU_MESHLET_RANGES.clear();
        SECTION_VISIBILITY.clear();
        lastInvalidationReason = reason;
        INVALIDATION_COUNT.incrementAndGet();
        GpuWorldDatabase.get().clear(reason);
        if (hadTerrainData) {
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
    }
    
    public static void recordDrawSlice(SectionMesh sectionMesh, ChunkSectionLayer layer, SectionRenderDispatcher.RenderSectionBufferSlice slice) {
        if (!(sectionMesh instanceof CompiledSectionMesh compiledSectionMesh)) {
            return;
        }
        CapturedSection capturedSection = SECTIONS.get(compiledSectionMesh);
        SectionMesh.SectionDraw draw = compiledSectionMesh.getSectionDraw(layer);
        if (capturedSection == null || draw == null || slice == null) {
            return;
        }
        int baseVertex = (int) (slice.vertexBufferOffset() / layer.vertexFormat().getVertexSize());
        GpuBuffer indexBuffer = draw.hasCustomIndexBuffer() ? slice.indexBuffer() : null;
        int firstIndex = draw.hasCustomIndexBuffer() ? (int) (slice.indexBufferOffset() / draw.indexType().bytes) : 0;
        DRAW_SLICES.put(new DrawKey(slice.vertexBuffer(), indexBuffer, draw.hasCustomIndexBuffer() ? draw.indexType() : null, baseVertex, firstIndex, draw.indexCount()), new SectionLayerKey(compiledSectionMesh, capturedSection.sectionNode, layer.ordinal()));
    }
    
    public static @Nullable MeshletRange meshletRangeForDraw(RenderPass.Draw<?> draw, int layerOrdinal) {
        SectionLayerKey sectionLayer = DRAW_SLICES.get(new DrawKey(draw.vertexBuffer(), draw.indexBuffer(), draw.indexType(), draw.baseVertex(), draw.firstIndex(), draw.indexCount()));
        if (sectionLayer == null || sectionLayer.layerOrdinal() != layerOrdinal) {
            return null;
        }
        MeshletRange range = GPU_MESHLET_RANGES.get(sectionLayer);
        if (range == null) {
            return null;
        }
        return range.withVisibility(visibilityForSection(sectionLayer.sectionNode()));
    }
    
    @SuppressWarnings("unused")
    public static List<MeshletRange> meshletRangesForLayer(int layerOrdinal) {
        ArrayList<MeshletRange> ranges = new ArrayList<>();
        for (Map.Entry<SectionLayerKey, MeshletRange> entry : GPU_MESHLET_RANGES.entrySet()) {
            if (entry.getKey().layerOrdinal() == layerOrdinal && entry.getValue().count() > 0) {
                MeshletRange range = entry.getValue();
                ranges.add(range.withVisibility(visibilityForSection(range.sectionNode())));
            }
        }
        ranges.sort(Comparator.comparingLong(MeshletRange::sectionNode).thenComparingInt(MeshletRange::offset));
        return ranges;
    }
    
    @SuppressWarnings("unused")
    public static boolean hasGpuMeshletRangeForDraw(RenderPass.Draw<?> draw, int layerOrdinal) {
        SectionLayerKey sectionLayer = DRAW_SLICES.get(new DrawKey(draw.vertexBuffer(), draw.indexBuffer(), draw.indexType(), draw.baseVertex(), draw.firstIndex(), draw.indexCount()));
        if (sectionLayer == null || sectionLayer.layerOrdinal() != layerOrdinal) {
            return false;
        }
        MeshletRange range = GPU_MESHLET_RANGES.get(sectionLayer);
        return range != null && range.count() > 0;
    }
    
    public static void recordSectionVisibility(int blockX, int blockY, int blockZ, float visibility) {
        SECTION_VISIBILITY.put(net.minecraft.core.SectionPos.asLong(net.minecraft.core.SectionPos.blockToSectionCoord(blockX), net.minecraft.core.SectionPos.blockToSectionCoord(blockY), net.minecraft.core.SectionPos.blockToSectionCoord(blockZ)), Math.clamp(visibility, 0.0F, 1.0F));
    }
    
    public static void clearSectionVisibilityFrame() {
        SECTION_VISIBILITY.clear();
    }
    
    static void clearGpuMeshletRanges() {
        GPU_MESHLET_RANGES.clear();
    }
    
    @SuppressWarnings("unused")
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
        map.put("drawSliceMappings", DRAW_SLICES.size());
        map.put("gpuMeshletRanges", GPU_MESHLET_RANGES.size());
        map.put("sectionVisibilityEntries", SECTION_VISIBILITY.size());
        map.put("vanillaChunkVisibilityFadeEnabled", TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled());
        map.put("sectionVisibility", visibilityStats());
        map.put("invalidationCount", INVALIDATION_COUNT.get());
        map.put("lastInvalidationReason", lastInvalidationReason);
        map.put("rejectedCaptures", REJECTED_CAPTURES.get());
        map.put("lastRejectedCapture", lastRejectedCapture);
        return map;
    }
    
    @SuppressWarnings("unused")
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
    
    static DescriptorHeapTerrainResources.UploadStats writeMetadataSnapshot(ByteBuffer sectionMetadata, ByteBuffer meshletHeaders, TerrainGpuBuffer meshletVertices, TerrainGpuBuffer meshletIndices, int maxSectionLayers, int maxMeshlets, DescriptorHeapTerrainResources.UploadStats previousStats) {
        sectionMetadata.clear();
        meshletHeaders.clear();
        int sectionLayers = 0;
        int liveSections;
        int meshlets = 0;
        int droppedSections = 0;
        int droppedMeshlets = 0;
        long vertexBytesUploaded = 0L;
        long indexBytesUploaded = 0L;
        long vertexBytesDropped = 0L;
        long indexBytesDropped = 0L;
        ChunkSectionLayer[] layers = ChunkSectionLayer.values();
        int[] meshletOffsetsByLayer = new int[layers.length];
        int[] meshletCountsByLayer = new int[layers.length];
        int[] customIndexMeshletCountsByLayer = new int[layers.length];
        Map<SectionLayerKey, MeshletRange> meshletRanges = new HashMap<>();
        liveSections = SECTIONS.size();
        for (ChunkSectionLayer chunkLayer : layers) {
            int layerOrdinal = chunkLayer.ordinal();
            meshletOffsetsByLayer[layerOrdinal] = meshlets;
            if (chunkLayer == ChunkSectionLayer.TRANSLUCENT && (!TerrainRendererDebugConfig.replaceVanillaTerrain() || !TerrainRendererDebugConfig.meshTranslucentTerrainEnabled())) {
                meshletCountsByLayer[layerOrdinal] = 0;
                continue;
            }
            for (Map.Entry<CompiledSectionMesh, CapturedSection> sectionEntry : SECTIONS.entrySet()) {
                CapturedSection section = sectionEntry.getValue();
                LayerCapture layer = section.layers.get(chunkLayer);
                if (layer == null) {
                    continue;
                }
                int vertexByteOffset = (int) vertexBytesUploaded;
                int indexByteOffset = (int) indexBytesUploaded;
                int layerVertexBytes = layer.buffers.vertexBytes();
                int layerIndexBytes = layer.buffers.indexBytes();
                if (vertexBytesUploaded + layerVertexBytes > meshletVertices.size() || indexBytesUploaded + layerIndexBytes > meshletIndices.size()) {
                    droppedSections++;
                    droppedMeshlets += layer.meshlets.size();
                    vertexBytesDropped += layerVertexBytes;
                    indexBytesDropped += layerIndexBytes;
                    continue;
                }
                if (layerVertexBytes > 0) {
                    ByteBuffer vertexSlice = meshletVertices.mappedSlice(vertexByteOffset, layerVertexBytes);
                    vertexSlice.put(layer.buffers.vertices().duplicate());
                }
                if (layerIndexBytes > 0) {
                    ByteBuffer indexSlice = meshletIndices.mappedSlice(indexByteOffset, layerIndexBytes);
                    indexSlice.put(layer.buffers.indices().duplicate());
                }
                vertexBytesUploaded += layerVertexBytes;
                indexBytesUploaded += layerIndexBytes;
                int firstMeshlet = meshlets;
                int writtenMeshlets = 0;
                for (TerrainMeshlet meshlet : layer.meshlets) {
                    if (meshlets >= maxMeshlets || meshletHeaders.remaining() < TerrainGpuLayout.MESHLET_HEADER_STRIDE) {
                        droppedMeshlets++;
                        continue;
                    }
                    writeMeshlet(meshletHeaders, meshlet, vertexByteOffset, indexByteOffset, layer.vertexBytes, layer.buffers.indexBytes());
                    meshlets++;
                    writtenMeshlets++;
                }
                if (sectionLayers >= maxSectionLayers || sectionMetadata.remaining() < TerrainGpuLayout.SECTION_METADATA_STRIDE) {
                    droppedSections++;
                    continue;
                }
                writeSectionLayer(sectionMetadata, section.sectionNode, chunkLayer, firstMeshlet, writtenMeshlets, layer, vertexByteOffset, indexByteOffset);
                if (layer.customIndexBuffer) {
                    customIndexMeshletCountsByLayer[layerOrdinal] += writtenMeshlets;
                }
                if (writtenMeshlets > 0) {
                    meshletRanges.put(new SectionLayerKey(sectionEntry.getKey(), section.sectionNode, layerOrdinal), new MeshletRange(section.sectionNode, firstMeshlet, writtenMeshlets, visibilityForSection(section.sectionNode)));
                }
                sectionLayers++;
            }
            meshletCountsByLayer[layerOrdinal] = meshlets - meshletOffsetsByLayer[layerOrdinal];
        }
        GPU_MESHLET_RANGES.clear();
        GPU_MESHLET_RANGES.putAll(meshletRanges);
        return previousStats.nextUpload(liveSections, sectionLayers, meshlets, droppedSections, droppedMeshlets, vertexBytesUploaded, indexBytesUploaded, vertexBytesDropped, indexBytesDropped, meshletOffsetsByLayer, meshletCountsByLayer, customIndexMeshletCountsByLayer);
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
        } else if (sourceElementBytes == Integer.BYTES) {
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
    
    private static void removeLayerMappings(CompiledSectionMesh mesh, int layerOrdinal) {
        DRAW_SLICES.entrySet().removeIf(entry -> entry.getValue().mesh() == mesh && entry.getValue().layerOrdinal() == layerOrdinal);
        GPU_MESHLET_RANGES.keySet().removeIf(key -> key.mesh() == mesh && key.layerOrdinal() == layerOrdinal);
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
    
    private static float visibilityForSection(long sectionNode) {
        if (!TerrainRendererDebugConfig.vanillaChunkVisibilityFadeEnabled()) {
            return 1.0F;
        }
        return SECTION_VISIBILITY.getOrDefault(sectionNode, 1.0F);
    }
    
    private static Map<String, Object> visibilityStats() {
        int belowOne = 0;
        float min = 1.0F;
        float max = SECTION_VISIBILITY.isEmpty() ? 1.0F : 0.0F;
        for (float visibility : SECTION_VISIBILITY.values()) {
            if (visibility < 0.999F) {
                belowOne++;
            }
            min = Math.min(min, visibility);
            max = Math.max(max, visibility);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entries", SECTION_VISIBILITY.size());
        map.put("belowOne", belowOne);
        map.put("min", min);
        map.put("max", max);
        return map;
    }
    
    private static final class CapturedSection {
        private final long sectionNode;
        private final EnumMap<ChunkSectionLayer, LayerCapture> layers = new EnumMap<>(ChunkSectionLayer.class);
        
        private CapturedSection(long sectionNode) {
            this.sectionNode = sectionNode;
        }
    }
    
    private record LayerCapture(int vertexBytes, int indexBytes, int estimatedVertices, int estimatedMeshlets,
                                boolean customIndexBuffer, int indexElementBytes, List<TerrainMeshlet> meshlets,
                                MeshLayerBuffers buffers) {
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
        private MeshletRange withVisibility(float visibility) {
            return new MeshletRange(this.sectionNode, this.offset, this.count, visibility);
        }
    }
    
    private record SectionLayerKey(CompiledSectionMesh mesh, long sectionNode, int layerOrdinal) {
    }
    
    private record DrawKey(GpuBuffer vertexBuffer, @Nullable GpuBuffer indexBuffer, @Nullable IndexType indexType,
                           int baseVertex, int firstIndex, int indexCount) {
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