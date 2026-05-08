package com.temotskipa.vulkanimprovement.client.vulkan;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.jspecify.annotations.Nullable;

public final class SectionMeshletStore {
    static final int TARGET_VERTICES_PER_MESHLET = 64;
    static final int BLOCK_VERTEX_STRIDE = 28;
    private static final Map<CompiledSectionMesh, CapturedSection> SECTIONS = new ConcurrentHashMap<>();

    private SectionMeshletStore() {
    }

    public static void capture(long sectionNode, CompiledSectionMesh mesh, ChunkSectionLayer layer, @Nullable ByteBuffer vertexBuffer, @Nullable ByteBuffer indexBuffer) {
        int vertexBytes = remaining(vertexBuffer);
        int indexBytes = remaining(indexBuffer);
        var draw = mesh.getSectionDraw(layer);
        int indexCount = draw == null ? 0 : draw.indexCount();
        boolean customIndexBuffer = draw != null && draw.hasCustomIndexBuffer();
        int indexElementBytes = draw == null ? 0 : draw.indexType().bytes;
        int estimatedVertices = estimateVertices(indexCount);
        int estimatedMeshlets = estimateMeshlets(estimatedVertices);
        List<TerrainMeshlet> meshlets = buildMeshlets(sectionNode, layer, estimatedVertices, indexCount, estimatedMeshlets);
        MeshLayerBuffers buffers = new MeshLayerBuffers(copy(vertexBuffer), copy(indexBuffer));
        SECTIONS.compute(mesh, (ignored, existing) -> {
            CapturedSection section = existing == null ? new CapturedSection(sectionNode) : existing;
            section.layers.put(layer, new LayerCapture(vertexBytes, indexBytes, estimatedVertices, estimatedMeshlets, customIndexBuffer, indexElementBytes, meshlets, buffers));
            return section;
        });
        MeshTerrainRenderer.get().recordSectionCapture(vertexBytes, indexBytes, estimatedMeshlets);
        DescriptorHeapTerrainResources.get().markTerrainDataDirty();
    }

    public static void release(CompiledSectionMesh mesh) {
        if (SECTIONS.remove(mesh) != null) {
            MeshTerrainRenderer.get().recordSectionRelease();
            DescriptorHeapTerrainResources.get().markTerrainDataDirty();
        }
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
        map.put("targetVerticesPerMeshlet", TARGET_VERTICES_PER_MESHLET);
        map.put("blockVertexStride", BLOCK_VERTEX_STRIDE);
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

    public static DescriptorHeapTerrainResources.UploadStats writeMetadataSnapshot(
            ByteBuffer sectionMetadata,
            ByteBuffer meshletHeaders,
            ByteBuffer meshletVertices,
            ByteBuffer meshletIndices,
            int maxSectionLayers,
            int maxMeshlets
    ) {
        sectionMetadata.clear();
        meshletHeaders.clear();
        meshletVertices.clear();
        meshletIndices.clear();

        int sectionLayers = 0;
        int liveSections = 0;
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
        liveSections = SECTIONS.size();

        for (ChunkSectionLayer chunkLayer : layers) {
            int layerOrdinal = chunkLayer.ordinal();
            meshletOffsetsByLayer[layerOrdinal] = meshlets;
            for (CapturedSection section : SECTIONS.values()) {
                LayerCapture layer = section.layers.get(chunkLayer);
                if (layer == null) {
                    continue;
                }
                int vertexByteOffset = meshletVertices.position();
                int indexByteOffset = meshletIndices.position();
                if (!copyInto(meshletVertices, layer.buffers.vertices) || !copyInto(meshletIndices, layer.buffers.indices)) {
                    meshletVertices.position(vertexByteOffset);
                    meshletIndices.position(indexByteOffset);
                    droppedSections++;
                    droppedMeshlets += layer.meshlets.size();
                    vertexBytesDropped += layer.buffers.vertexBytes();
                    indexBytesDropped += layer.buffers.indexBytes();
                    continue;
                }
                vertexBytesUploaded += layer.buffers.vertexBytes();
                indexBytesUploaded += layer.buffers.indexBytes();

                int firstMeshlet = meshlets;
                int writtenMeshlets = 0;
                for (TerrainMeshlet meshlet : layer.meshlets) {
                    if (meshlets >= maxMeshlets || meshletHeaders.remaining() < DescriptorHeapTerrainResources.MESHLET_HEADER_STRIDE) {
                        droppedMeshlets++;
                        continue;
                    }
                    writeMeshlet(meshletHeaders, meshlet, vertexByteOffset, indexByteOffset, layer.vertexBytes, layer.indexBytes);
                    meshlets++;
                    writtenMeshlets++;
                }

                if (sectionLayers >= maxSectionLayers || sectionMetadata.remaining() < DescriptorHeapTerrainResources.SECTION_METADATA_STRIDE) {
                    droppedSections++;
                    continue;
                }
                writeSectionLayer(sectionMetadata, section.sectionNode, chunkLayer, firstMeshlet, writtenMeshlets, layer, vertexByteOffset, indexByteOffset);
                if (layer.customIndexBuffer) {
                    customIndexMeshletCountsByLayer[layerOrdinal] += writtenMeshlets;
                }
                sectionLayers++;
            }
            meshletCountsByLayer[layerOrdinal] = meshlets - meshletOffsetsByLayer[layerOrdinal];
        }

        return DescriptorHeapTerrainResources.UploadStats.empty().nextUpload(
                liveSections,
                sectionLayers,
                meshlets,
                droppedSections,
                droppedMeshlets,
                vertexBytesUploaded,
                indexBytesUploaded,
                vertexBytesDropped,
                indexBytesDropped,
                meshletOffsetsByLayer,
                meshletCountsByLayer,
                customIndexMeshletCountsByLayer
        );
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

    private static boolean copyInto(ByteBuffer target, ByteBuffer source) {
        ByteBuffer duplicate = source.duplicate();
        if (target.remaining() < duplicate.remaining()) {
            return false;
        }
        target.put(duplicate);
        return true;
    }

    private static int estimateVertices(int indexCount) {
        return Math.max(1, indexCount / 6 * 4);
    }

    private static int estimateMeshlets(int estimatedVertices) {
        return Math.max(1, (estimatedVertices + TARGET_VERTICES_PER_MESHLET - 1) / TARGET_VERTICES_PER_MESHLET);
    }

    private static List<TerrainMeshlet> buildMeshlets(long sectionNode, ChunkSectionLayer layer, int estimatedVertexCount, int indexCount, int meshletCount) {
        List<TerrainMeshlet> meshlets = new ArrayList<>(meshletCount);
        for (int i = 0; i < meshletCount; i++) {
            meshlets.add(TerrainMeshlet.from(sectionNode, layer, i, meshletCount, estimatedVertexCount, indexCount));
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
        target.putInt(meshlet.indexCount() > 0 && indexBytes > 0 ? 1 : 0);
        target.putInt(indexBytes > 0 ? Math.max(1, meshlet.indexCount()) : 0);
    }

    private static final class CapturedSection {
        private final long sectionNode;
        private final EnumMap<ChunkSectionLayer, LayerCapture> layers = new EnumMap<>(ChunkSectionLayer.class);

        private CapturedSection(long sectionNode) {
            this.sectionNode = sectionNode;
        }
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
}
