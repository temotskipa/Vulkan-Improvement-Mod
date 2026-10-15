package com.temotskipa.vulkanimprovement.client.vulkan.terrain.util;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * The records the terrain path writes for the GPU, laid out as the shaders declare them: little
 * endian and packed, as the shaders read them with scalar layout. Every value may sit at any byte
 * offset, so the same layouts serve mapped GPU memory and copies kept on the Java heap. Each
 * record's member names are the names its shader struct gives them, and its offsets are taken
 * from the layout, not counted by hand.
 */
public final class TerrainGpuRecords {
    public static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    public static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
    
    private TerrainGpuRecords() {
    }
    
    private static long offset(StructLayout layout, String member) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(member));
    }
    
    /**
     * A section layer's record in the page table.
     */
    public static final class ResidentPage {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(LONG.withName("sectionNode"), INT.withName("sectionX"), INT.withName("sectionY"), INT.withName("sectionZ"), INT.withName("layer"), INT.withName("firstMeshlet"), INT.withName("meshletCount"), INT.withName("estimatedVertices"), INT.withName("estimatedMeshlets"), INT.withName("vertexBytes"), INT.withName("indexBytes"), LONG.withName("vertexByteOffset"), LONG.withName("indexByteOffset"), INT.withName("fadeStart"), INT.withName("customIndexBuffer"), INT.withName("reserved1"), INT.withName("reserved2")).withName("ResidentPage");
        public static final long SECTION_NODE = offset(LAYOUT, "sectionNode");
        public static final long SECTION_X = offset(LAYOUT, "sectionX");
        public static final long SECTION_Y = offset(LAYOUT, "sectionY");
        public static final long SECTION_Z = offset(LAYOUT, "sectionZ");
        public static final long LAYER = offset(LAYOUT, "layer");
        public static final long FIRST_MESHLET = offset(LAYOUT, "firstMeshlet");
        public static final long MESHLET_COUNT = offset(LAYOUT, "meshletCount");
        public static final long ESTIMATED_VERTICES = offset(LAYOUT, "estimatedVertices");
        public static final long ESTIMATED_MESHLETS = offset(LAYOUT, "estimatedMeshlets");
        public static final long VERTEX_BYTES = offset(LAYOUT, "vertexBytes");
        public static final long INDEX_BYTES = offset(LAYOUT, "indexBytes");
        public static final long VERTEX_BYTE_OFFSET = offset(LAYOUT, "vertexByteOffset");
        public static final long INDEX_BYTE_OFFSET = offset(LAYOUT, "indexByteOffset");
        public static final long FADE_START = offset(LAYOUT, "fadeStart");
        public static final long CUSTOM_INDEX_BUFFER = offset(LAYOUT, "customIndexBuffer");
        
        private ResidentPage() {
        }
    }
    
    /**
     * A meshlet's header: where its vertices and indices are, its box and the way its faces point.
     * Where they are is a byte offset into the payload buffers, 64 bits wide like the addresses
     * the shaders add it to.
     */
    public static final class MeshletHeader {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(INT.withName("sectionX"), INT.withName("sectionY"), INT.withName("sectionZ"), INT.withName("layer"), INT.withName("firstVertex"), INT.withName("vertexCount"), INT.withName("firstIndex"), INT.withName("indexCount"), LONG.withName("vertexByteOffset"), LONG.withName("indexByteOffset"), INT.withName("vertexBytes"), INT.withName("indexBytes"), INT.withName("flags0"), INT.withName("materialId"), MemoryLayout.sequenceLayout(3, FLOAT).withName("boundsMin"), MemoryLayout.sequenceLayout(3, FLOAT).withName("boundsMax"), INT.withName("facing"), INT.withName("reserved0")).withName("MeshletHeader");
        public static final long SECTION_X = offset(LAYOUT, "sectionX");
        public static final long SECTION_Y = offset(LAYOUT, "sectionY");
        public static final long SECTION_Z = offset(LAYOUT, "sectionZ");
        public static final long LAYER = offset(LAYOUT, "layer");
        public static final long FIRST_VERTEX = offset(LAYOUT, "firstVertex");
        public static final long VERTEX_COUNT = offset(LAYOUT, "vertexCount");
        public static final long FIRST_INDEX = offset(LAYOUT, "firstIndex");
        public static final long INDEX_COUNT = offset(LAYOUT, "indexCount");
        public static final long VERTEX_BYTE_OFFSET = offset(LAYOUT, "vertexByteOffset");
        public static final long INDEX_BYTE_OFFSET = offset(LAYOUT, "indexByteOffset");
        public static final long VERTEX_BYTES = offset(LAYOUT, "vertexBytes");
        public static final long INDEX_BYTES = offset(LAYOUT, "indexBytes");
        public static final long FLAGS = offset(LAYOUT, "flags0");
        public static final long MATERIAL_ID = offset(LAYOUT, "materialId");
        public static final long BOUNDS_MIN = offset(LAYOUT, "boundsMin");
        public static final long BOUNDS_MAX = offset(LAYOUT, "boundsMax");
        public static final long FACING = offset(LAYOUT, "facing");
        
        private MeshletHeader() {
        }
    }
    
    /**
     * A material's record in the material table.
     */
    public static final class MaterialRecord {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(INT.withName("materialId"), INT.withName("flags"), INT.withName("blockAtlasWidth"), INT.withName("blockAtlasHeight"), INT.withName("blockAtlasBaseMip"), INT.withName("blockAtlasMipLevels"), INT.withName("lightmapWidth"), INT.withName("lightmapHeight"), INT.withName("normalTextureIndex"), INT.withName("specularTextureIndex"), INT.withName("emissionTextureIndex"), INT.withName("tintFlags"), INT.withName("alphaMode"), INT.withName("renderLayerOrdinal"), INT.withName("materialDomain"), INT.withName("reserved0")).withName("MaterialRecord");
        
        private MaterialRecord() {
        }
    }
    
    /**
     * A task's record in the visible list: which meshlets it draws, and how far its section has
     * faded in.
     */
    public static final class VisibleRecord {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(INT.withName("meshletIndex"), FLOAT.withName("visibility")).withName("VisibleMeshletRecord");
        public static final long MESHLET_INDEX = offset(LAYOUT, "meshletIndex");
        public static final long VISIBILITY = offset(LAYOUT, "visibility");
        
        private VisibleRecord() {
        }
    }
    
    /**
     * A record of the CPU-written work queue: one meshlet of a layer.
     */
    public static final class WorkQueueRecord {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(INT.withName("meshletIndex"), INT.withName("layerOrdinal"), INT.withName("lodLevel"), INT.withName("flags")).withName("TerrainWorkQueueRecord");
        public static final long MESHLET_INDEX = offset(LAYOUT, "meshletIndex");
        public static final long LAYER_ORDINAL = offset(LAYOUT, "layerOrdinal");
        public static final long LOD_LEVEL = offset(LAYOUT, "lodLevel");
        public static final long FLAGS = offset(LAYOUT, "flags");
        
        private WorkQueueRecord() {
        }
    }
    
    /**
     * A mesh-task draw's indirect command, VkDrawMeshTasksIndirectCommandEXT.
     */
    public static final class MeshTaskCommand {
        public static final StructLayout LAYOUT = MemoryLayout.structLayout(INT.withName("groupCountX"), INT.withName("groupCountY"), INT.withName("groupCountZ")).withName("MeshTaskCommand");
        public static final long GROUP_COUNT_X = offset(LAYOUT, "groupCountX");
        public static final long GROUP_COUNT_Y = offset(LAYOUT, "groupCountY");
        public static final long GROUP_COUNT_Z = offset(LAYOUT, "groupCountZ");
        
        private MeshTaskCommand() {
        }
    }
}