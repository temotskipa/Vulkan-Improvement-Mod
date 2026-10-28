package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommands;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTMeshShader;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * What one preparation of Minecraft's features hands the GPU: a record per posed part, one per
 * cube, and the GPU draws they go into. Once Minecraft has uploaded its own vertices, the records
 * go into a Minecraft buffer, the cubes sorted by draw. A draw the mesh stage can draw reads them
 * there when Minecraft draws it ({@link ModelMeshShader}); for the others one compute pass writes
 * the cubes' vertices, which their draws then read until Minecraft ends the draw. Used from the
 * render thread only.
 */
final class ModelVertexFrame {
    static final int WORKGROUP_SIZE = 64;
    static final int PUSH_CONSTANT_BYTES = 48;
    /**
     * The workgroups one dispatch may have along x on every Vulkan device.
     */
    private static final int MAX_WORKGROUPS = 65_535;
    private static final int MAX_JOBS_PER_DISPATCH = MAX_WORKGROUPS * WORKGROUP_SIZE / ModelGpuRecords.MAX_CUBE_POLYGONS;
    private static final long RECORD_ALIGNMENT = 16L;
    /**
     * The directions along the positive axes; each one's opposite points along the negative axis.
     */
    private static final Direction[] POSITIVE_DIRECTIONS = {Direction.EAST, Direction.UP, Direction.SOUTH};
    
    /**
     * The records, a word per element, written into Java arrays, which the render thread writes
     * tens of thousands of times a frame faster than a {@link MemorySegment}; each array goes to
     * the GPU in one copy.
     */
    private int[] parts = new int[4096 * ModelPartRecord.WORDS];
    private int[] jobs = new int[16_384 * ModelJobRecord.WORDS];
    private int[] sortedJobs = new int[16_384 * ModelJobRecord.WORDS];
    private final List<ModelVertexDraw> draws = new ArrayList<>();
    private int partCount;
    private int jobCount;
    private long vertexCount;
    private @Nullable Object stagedBuffer;
    private @Nullable GpuBuffer vertices;
    private boolean written;
    private int meshDraws;
    private int meshJobs;
    /**
     * The device addresses the mesh stage reads: the resident polygons, the frame's parts, its
     * sorted cubes and the 8-bit conversion table.
     */
    private long meshPolygons;
    private long meshParts;
    private long meshJobRecords;
    private long meshConversions;
    
    /**
     * A normal as {@code BufferBuilder} writes it: three signed bytes, x first, then a byte it
     * leaves alone, written here as 0.
     */
    static int packNormal(float x, float y, float z) {
        return normalByte(x) | normalByte(y) << 8 | normalByte(z) << 16;
    }
    
    private static int normalByte(float component) {
        return (int) (Math.clamp(component, -1.0F, 1.0F) * 127.0F) & 0xFF;
    }
    
    private static long align(long offset) {
        return Math.ceilDiv(offset, RECORD_ALIGNMENT) * RECORD_ALIGNMENT;
    }
    
    /**
     * The array, at least {@code words} long, holding what was written to it.
     */
    private static int[] reserve(int[] array, int words) {
        if (words <= array.length) {
            return array;
        }
        int[] grown = new int[Math.max(words, array.length * 2)];
        System.arraycopy(array, 0, grown, 0, array.length);
        return grown;
    }
    
    boolean isEmpty() {
        return this.jobCount == 0;
    }
    
    boolean belongsTo(Object stagedVertexBuffer) {
        return this.stagedBuffer == stagedVertexBuffer;
    }
    
    /**
     * The frame's contents, for a report.
     */
    String describe() {
        return this.draws.size() + " draws, " + this.partCount + " parts, " + this.jobCount + " cubes";
    }
    
    int partCount() {
        return this.partCount;
    }
    
    int jobCount() {
        return this.jobCount;
    }
    
    int drawCount() {
        return this.draws.size();
    }
    
    int meshDrawCount() {
        return this.meshDraws;
    }
    
    int meshJobCount() {
        return this.meshJobs;
    }
    
    long vertexCount() {
        return this.vertexCount;
    }
    
    /**
     * The part records written so far, in order, each word in the platform's byte order.
     */
    MemorySegment partRecords() {
        return MemorySegment.ofArray(this.parts).asSlice(0L, (long) this.partCount * ModelPartRecord.BYTES);
    }
    
    /**
     * The cube records written so far, in order, each word in the platform's byte order.
     */
    MemorySegment jobRecords() {
        return MemorySegment.ofArray(this.jobs).asSlice(0L, (long) this.jobCount * ModelJobRecord.BYTES);
    }
    
    /**
     * A new GPU draw of the staged vertex buffer the frame's draws all belong to.
     */
    ModelVertexDraw addDraw(Object stagedVertexBuffer, PreparedRenderType renderType) {
        if (this.stagedBuffer == null) {
            this.stagedBuffer = stagedVertexBuffer;
        }
        else if (this.stagedBuffer != stagedVertexBuffer) {
            throw new IllegalStateException("GPU model draws of two staged vertex buffers in one frame");
        }
        if (this.draws.size() >= ModelJobRecord.MAX_DRAWS) {
            throw new IllegalStateException("More than " + ModelJobRecord.MAX_DRAWS + " GPU model draws in one frame");
        }
        ModelVertexDraw draw = new ModelVertexDraw(this.draws.size(), renderType);
        this.draws.add(draw);
        return draw;
    }
    
    /**
     * Records a posed part: its pose, and the color, overlay and light Minecraft writes into each of
     * its vertices. Its normals are written as its cubes need them.
     */
    int addPart(PoseStack.Pose pose, int color, int overlay, int light) {
        int offset = this.partCount * ModelPartRecord.WORDS;
        int[] records = this.parts = reserve(this.parts, offset + ModelPartRecord.WORDS);
        Matrix4f matrix = pose.pose();
        int poseOffset = offset + ModelPartRecord.POSE_WORD;
        records[poseOffset] = Float.floatToRawIntBits(matrix.m00());
        records[poseOffset + 1] = Float.floatToRawIntBits(matrix.m01());
        records[poseOffset + 2] = Float.floatToRawIntBits(matrix.m02());
        records[poseOffset + 3] = Float.floatToRawIntBits(matrix.m10());
        records[poseOffset + 4] = Float.floatToRawIntBits(matrix.m11());
        records[poseOffset + 5] = Float.floatToRawIntBits(matrix.m12());
        records[poseOffset + 6] = Float.floatToRawIntBits(matrix.m20());
        records[poseOffset + 7] = Float.floatToRawIntBits(matrix.m21());
        records[poseOffset + 8] = Float.floatToRawIntBits(matrix.m22());
        records[poseOffset + 9] = Float.floatToRawIntBits(matrix.m30());
        records[poseOffset + 10] = Float.floatToRawIntBits(matrix.m31());
        records[poseOffset + 11] = Float.floatToRawIntBits(matrix.m32());
        records[offset + ModelPartRecord.COLOR_WORD] = ARGB.toABGR(color);
        records[offset + ModelPartRecord.OVERLAY_WORD] = overlay;
        records[offset + ModelPartRecord.LIGHT_WORD] = light;
        return this.partCount++;
    }
    
    /**
     * Writes a part's normal for each direction given: the face normal transformed by the part's
     * pose as {@code ModelPart.Cube.compile} transforms it, packed as {@code BufferBuilder} packs it.
     */
    void writeNormals(int part, PoseStack.Pose pose, int directions, Vector3f scratch) {
        int offset = part * ModelPartRecord.WORDS + ModelPartRecord.NORMALS_WORD;
        int[] records = this.parts;
        // A unit vector along an axis has one non-zero component, so the pose turns the opposite
        // vector into exactly the negated normal, which is normalized to exactly the negation too.
        for (Direction direction : POSITIVE_DIRECTIONS) {
            Direction opposite = direction.getOpposite();
            boolean along = (directions & 1 << direction.ordinal()) != 0;
            boolean against = (directions & 1 << opposite.ordinal()) != 0;
            if (along || against) {
                Vector3f normal = pose.transformNormal(direction.getUnitVec3f(), scratch);
                if (along) {
                    records[offset + direction.ordinal()] = packNormal(normal.x(), normal.y(), normal.z());
                }
                if (against) {
                    records[offset + opposite.ordinal()] = packNormal(-normal.x(), -normal.y(), -normal.z());
                }
            }
        }
    }
    
    /**
     * Records a cube the GPU writes into the draw, after the draw's vertices so far.
     */
    void addJob(int part, int firstPolygon, int polygons, ModelVertexDraw draw) {
        int offset = this.jobCount * ModelJobRecord.WORDS;
        int[] records = this.jobs = reserve(this.jobs, offset + ModelJobRecord.WORDS);
        records[offset + ModelJobRecord.PART_WORD] = part;
        records[offset + ModelJobRecord.FIRST_POLYGON_WORD] = firstPolygon;
        records[offset + ModelJobRecord.DRAW_AND_POLYGONS_WORD] = draw.index | polygons << ModelJobRecord.DRAW_BITS;
        records[offset + ModelJobRecord.FIRST_VERTEX_WORD] = draw.vertexCount;
        int vertices = polygons * ModelGpuRecords.POLYGON_VERTICES;
        draw.vertexCount += vertices;
        draw.jobCount++;
        this.vertexCount += vertices;
        this.jobCount++;
    }
    
    /**
     * Records the cubes of a part laid out in the store, which the GPU writes into the draw after
     * the draw's vertices so far.
     */
    void addJobs(int part, ModelPartCubes cubes, ModelVertexDraw draw) {
        int count = cubes.count();
        int offset = this.jobCount * ModelJobRecord.WORDS;
        int[] records = this.jobs = reserve(this.jobs, offset + count * ModelJobRecord.WORDS);
        int firstVertex = draw.vertexCount;
        for (int cube = 0; cube < count; cube++) {
            int polygons = cubes.polygons[cube];
            records[offset + ModelJobRecord.PART_WORD] = part;
            records[offset + ModelJobRecord.FIRST_POLYGON_WORD] = cubes.firstPolygons[cube];
            records[offset + ModelJobRecord.DRAW_AND_POLYGONS_WORD] = draw.index | polygons << ModelJobRecord.DRAW_BITS;
            records[offset + ModelJobRecord.FIRST_VERTEX_WORD] = firstVertex;
            firstVertex += polygons * ModelGpuRecords.POLYGON_VERTICES;
            offset += ModelJobRecord.WORDS;
        }
        draw.jobCount += count;
        this.vertexCount += firstVertex - draw.vertexCount;
        draw.vertexCount = firstVertex;
        this.jobCount += count;
    }
    
    /**
     * Sorts the cubes by draw, the vertex pass's draws first, puts the records in a buffer of their
     * own, places the vertex pass's draws one after another in a vertex buffer, and records the
     * pass that writes their vertices, outside any render pass.
     *
     * @param conversions the mesh stage's 8-bit conversion table, or null when the mesh stage draws
     *                    nothing
     */
    void write(ModelBufferPool inputs, ModelBufferPool outputs, ComputePipeline pipeline, long polygonAddress, @Nullable GpuBuffer conversions) {
        this.meshDraws = 0;
        this.meshJobs = 0;
        for (ModelVertexDraw draw : this.draws) {
            draw.mesh = conversions != null && ModelMeshPipelines.drawsWithMesh(draw.renderType);
            if (draw.mesh) {
                this.meshDraws++;
                this.meshJobs += draw.jobCount;
            }
        }
        int[] jobRecords = this.meshDraws == 0 ? this.jobs : sortJobs();
        long base = 0L;
        for (ModelVertexDraw draw : this.draws) {
            draw.baseVertex = draw.mesh ? 0 : Math.toIntExact(base);
            base += draw.mesh ? 0 : draw.vertexCount;
        }
        long partBytes = (long) this.partCount * ModelPartRecord.BYTES;
        long jobOffset = align(partBytes);
        long jobBytes = (long) this.jobCount * ModelJobRecord.BYTES;
        long drawBaseOffset = align(jobOffset + jobBytes);
        GpuBuffer input = inputs.acquire(drawBaseOffset + (long) this.draws.size() * Integer.BYTES);
        try (MappedView view = input.map(false, true)) {
            MemorySegment target = MemorySegment.ofBuffer(view.data());
            MemorySegment.copy(this.parts, 0, target, ModelGpuRecords.INT, 0L, this.partCount * ModelPartRecord.WORDS);
            MemorySegment.copy(jobRecords, 0, target, ModelGpuRecords.INT, jobOffset, this.jobCount * ModelJobRecord.WORDS);
            for (ModelVertexDraw draw : this.draws) {
                target.set(ModelGpuRecords.INT, drawBaseOffset + (long) draw.index * Integer.BYTES, draw.baseVertex);
            }
        }
        long inputAddress = ShaderStorage.address(input);
        this.meshPolygons = polygonAddress;
        this.meshParts = inputAddress;
        this.meshJobRecords = inputAddress + jobOffset;
        this.meshConversions = conversions == null ? 0L : ShaderStorage.address(conversions);
        this.written = true;
        
        int vertexPassJobs = this.jobCount - this.meshJobs;
        if (vertexPassJobs == 0) {
            return;
        }
        GpuBuffer output = outputs.acquire(base * ModelEntityVertexRecord.BYTES);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstants = stack.malloc(PUSH_CONSTANT_BYTES);
            pushConstants.putLong(0, polygonAddress);
            pushConstants.putLong(8, inputAddress);
            pushConstants.putLong(16, inputAddress + jobOffset);
            pushConstants.putLong(24, inputAddress + drawBaseOffset);
            pushConstants.putLong(32, ShaderStorage.address(output));
            for (int firstJob = 0; firstJob < vertexPassJobs; firstJob += MAX_JOBS_PER_DISPATCH) {
                int jobs = Math.min(MAX_JOBS_PER_DISPATCH, vertexPassJobs - firstJob);
                pushConstants.putInt(40, firstJob);
                pushConstants.putInt(44, jobs);
                int workgroups = Math.ceilDiv(jobs * ModelGpuRecords.MAX_CUBE_POLYGONS, WORKGROUP_SIZE);
                ComputeCommands.dispatch(encoder, () -> "VIM Model Vertices", pipeline, pushConstants, workgroups, 1, 1);
            }
        }
        this.vertices = output;
    }
    
    /**
     * The cube records sorted by draw, keeping each draw's order: first those of the vertex pass's
     * draws, then those of the mesh stage's. Gives each draw the index of its first cube.
     */
    private int[] sortJobs() {
        int next = 0;
        for (ModelVertexDraw draw : this.draws) {
            if (!draw.mesh) {
                draw.firstJob = next;
                next += draw.jobCount;
            }
        }
        for (ModelVertexDraw draw : this.draws) {
            if (draw.mesh) {
                draw.firstJob = next;
                next += draw.jobCount;
            }
        }
        int[] placed = new int[this.draws.size()];
        int[] source = this.jobs;
        int[] sorted = this.sortedJobs = reserve(this.sortedJobs, this.jobCount * ModelJobRecord.WORDS);
        for (int job = 0; job < this.jobCount; job++) {
            int offset = job * ModelJobRecord.WORDS;
            int drawIndex = source[offset + ModelJobRecord.DRAW_AND_POLYGONS_WORD] & ModelJobRecord.MAX_DRAWS - 1;
            ModelVertexDraw draw = this.draws.get(drawIndex);
            System.arraycopy(source, offset, sorted, (draw.firstJob + placed[drawIndex]++) * ModelJobRecord.WORDS, ModelJobRecord.WORDS);
        }
        return sorted;
    }
    
    /**
     * What Minecraft draws a GPU draw with. For a draw of the vertex pass: the frame's vertex buffer
     * from the draw's first vertex, and Minecraft's own index buffer for quads, as it draws its
     * unsorted quad draws. For one of the mesh stage: the marker vertex buffer, with the draw's
     * index as its vertex offset, which the mesh draw takes its place by. Null before the frame is
     * written, and Minecraft then skips the draw.
     */
    @Nullable ExecuteInfo executeInfo(ModelVertexDraw draw, @Nullable GpuBuffer meshMarker) {
        if (!this.written || draw.vertexCount == 0) {
            return null;
        }
        if (draw.mesh) {
            if (meshMarker == null) {
                return null;
            }
            int indexCount = PrimitiveTopology.QUADS.indexCount(ModelGpuRecords.POLYGON_VERTICES);
            RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            autoIndices.requestIndexCount(indexCount);
            return new ExecuteInfo(meshMarker, null, autoIndices.type(), draw.index, 0, indexCount, PrimitiveTopology.QUADS);
        }
        if (this.vertices == null) {
            return null;
        }
        int indexCount = PrimitiveTopology.QUADS.indexCount(draw.vertexCount);
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        autoIndices.requestIndexCount(indexCount);
        return new ExecuteInfo(this.vertices, null, autoIndices.type(), draw.baseVertex, 0, indexCount, PrimitiveTopology.QUADS);
    }
    
    /**
     * Records the mesh draw that takes the place of the indexed draw Minecraft records for a draw of
     * the mesh stage, inside Minecraft's render pass with its pipeline and descriptors bound, and
     * binds Minecraft's pipeline again. Returns false, recording nothing, when the draw is not one
     * of this frame's mesh draws or the bound pipeline has no mesh pipeline.
     */
    boolean drawMesh(VkCommandBuffer commandBuffer, VulkanRenderPipeline pipeline, int drawIndex) {
        if (!this.written || drawIndex < 0 || drawIndex >= this.draws.size()) {
            return false;
        }
        ModelVertexDraw draw = this.draws.get(drawIndex);
        ModelMeshPipeline meshPipeline = ModelMeshPipelines.of(pipeline);
        if (!draw.mesh || meshPipeline == null) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstants = stack.malloc(ModelMeshShader.PUSH_CONSTANT_BYTES);
            pushConstants.putLong(0, this.meshPolygons);
            pushConstants.putLong(8, this.meshParts);
            pushConstants.putLong(16, this.meshJobRecords);
            pushConstants.putLong(24, this.meshConversions);
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.pipeline());
            for (int first = 0; first < draw.jobCount; first += MAX_WORKGROUPS) {
                int cubes = Math.min(MAX_WORKGROUPS, draw.jobCount - first);
                pushConstants.putInt(32, draw.firstJob + first);
                pushConstants.putInt(36, cubes);
                // Minecraft's push constant range covers every stage, so its updates name every stage.
                VK10.vkCmdPushConstants(commandBuffer, pipeline.pipelineLayout(), Integer.MAX_VALUE, meshPipeline.pushConstantOffset(), pushConstants);
                EXTMeshShader.vkCmdDrawMeshTasksEXT(commandBuffer, cubes, 1, 1);
            }
            VK10.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vkPipeline());
        }
        return true;
    }
    
    /**
     * Forgets the frame once Minecraft has ended its draw. Its buffers stay with their pools until
     * the GPU is done with them.
     */
    void reset() {
        this.draws.clear();
        this.partCount = 0;
        this.jobCount = 0;
        this.vertexCount = 0L;
        this.stagedBuffer = null;
        this.vertices = null;
        this.written = false;
        this.meshDraws = 0;
        this.meshJobs = 0;
    }
    
    void close() {
        reset();
    }
}