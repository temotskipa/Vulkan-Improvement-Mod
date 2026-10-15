package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommands;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * What one preparation of Minecraft's features hands the GPU: a record per posed part, one per
 * cube, and the GPU draws they go into. Once Minecraft has uploaded its own vertices, the records
 * go into a Minecraft buffer and one compute pass writes every cube's vertices; the draws then read
 * them until Minecraft ends the draw. Used from the render thread only.
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
    private static final Direction[] DIRECTIONS = Direction.values();
    
    private final GrowingSegment parts = new GrowingSegment(4096L * ModelGpuRecords.Part.BYTES);
    private final GrowingSegment jobs = new GrowingSegment(16_384L * ModelGpuRecords.Job.BYTES);
    private final List<ModelVertexDraw> draws = new ArrayList<>();
    private int partCount;
    private int jobCount;
    private long vertexCount;
    private @Nullable Object stagedBuffer;
    private @Nullable GpuBuffer vertices;
    
    boolean isEmpty() {
        return this.jobCount == 0;
    }
    
    boolean belongsTo(Object stagedVertexBuffer) {
        return this.stagedBuffer == stagedVertexBuffer;
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
    
    long vertexCount() {
        return this.vertexCount;
    }
    
    /**
     * The part records written so far, in order.
     */
    MemorySegment partRecords() {
        return this.parts.segment();
    }
    
    /**
     * The cube records written so far, in order.
     */
    MemorySegment jobRecords() {
        return this.jobs.segment();
    }
    
    /**
     * A new GPU draw of the staged vertex buffer the frame's draws all belong to.
     */
    ModelVertexDraw addDraw(Object stagedVertexBuffer) {
        if (this.stagedBuffer == null) {
            this.stagedBuffer = stagedVertexBuffer;
        } else if (this.stagedBuffer != stagedVertexBuffer) {
            throw new IllegalStateException("GPU model draws of two staged vertex buffers in one frame");
        }
        if (this.draws.size() >= ModelGpuRecords.Job.MAX_DRAWS) {
            throw new IllegalStateException("More than " + ModelGpuRecords.Job.MAX_DRAWS + " GPU model draws in one frame");
        }
        ModelVertexDraw draw = new ModelVertexDraw(this.draws.size());
        this.draws.add(draw);
        return draw;
    }
    
    /**
     * Records a posed part: its pose, and the color, overlay and light Minecraft writes into each of
     * its vertices. Its normals are written as its cubes need them.
     */
    int addPart(PoseStack.Pose pose, int color, int overlay, int light) {
        long offset = (long) this.partCount * ModelGpuRecords.Part.BYTES;
        MemorySegment records = this.parts.reserve(offset, offset + ModelGpuRecords.Part.BYTES);
        Matrix4f matrix = pose.pose();
        long poseOffset = offset + ModelGpuRecords.Part.POSE;
        records.set(ModelGpuRecords.FLOAT, poseOffset, matrix.m00());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 4L, matrix.m01());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 8L, matrix.m02());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 12L, matrix.m10());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 16L, matrix.m11());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 20L, matrix.m12());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 24L, matrix.m20());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 28L, matrix.m21());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 32L, matrix.m22());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 36L, matrix.m30());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 40L, matrix.m31());
        records.set(ModelGpuRecords.FLOAT, poseOffset + 44L, matrix.m32());
        records.set(ModelGpuRecords.INT, offset + ModelGpuRecords.Part.COLOR, ARGB.toABGR(color));
        records.set(ModelGpuRecords.INT, offset + ModelGpuRecords.Part.OVERLAY, overlay);
        records.set(ModelGpuRecords.INT, offset + ModelGpuRecords.Part.LIGHT, light);
        return this.partCount++;
    }
    
    /**
     * Writes a part's normal for each direction given: the face normal transformed by the part's
     * pose as {@code ModelPart.Cube.compile} transforms it, packed as {@code BufferBuilder} packs it.
     */
    void writeNormals(int part, PoseStack.Pose pose, int directions, Vector3f scratch) {
        long offset = (long) part * ModelGpuRecords.Part.BYTES + ModelGpuRecords.Part.NORMALS;
        MemorySegment records = this.parts.segment();
        for (Direction direction : DIRECTIONS) {
            if ((directions & 1 << direction.ordinal()) != 0) {
                Vector3f normal = pose.transformNormal(direction.getUnitVec3f(), scratch);
                records.set(ModelGpuRecords.INT, offset + direction.ordinal() * 4L, packNormal(normal.x(), normal.y(), normal.z()));
            }
        }
    }
    
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
    
    /**
     * Records a cube the GPU writes into the draw, after the draw's vertices so far.
     */
    void addJob(int part, int firstPolygon, int polygons, ModelVertexDraw draw) {
        long offset = (long) this.jobCount * ModelGpuRecords.Job.BYTES;
        MemorySegment records = this.jobs.reserve(offset, offset + ModelGpuRecords.Job.BYTES);
        records.set(ModelGpuRecords.INT, offset + ModelGpuRecords.Job.PART, part);
        records.set(ModelGpuRecords.INT, offset + ModelGpuRecords.Job.FIRST_POLYGON, firstPolygon);
        records.set(ModelGpuRecords.INT, offset + ModelGpuRecords.Job.DRAW_AND_POLYGONS, draw.index | polygons << ModelGpuRecords.Job.DRAW_BITS);
        records.set(ModelGpuRecords.INT, offset + ModelGpuRecords.Job.FIRST_VERTEX, draw.vertexCount);
        int vertices = polygons * ModelGpuRecords.POLYGON_VERTICES;
        draw.vertexCount += vertices;
        this.vertexCount += vertices;
        this.jobCount++;
    }
    
    /**
     * Places the draws one after another in a vertex buffer, puts the records in a buffer of their
     * own, and records the pass that writes the vertices, outside any render pass.
     */
    void write(ModelBufferPool inputs, ModelBufferPool outputs, ComputePipeline pipeline, long polygonAddress) {
        long base = 0L;
        for (ModelVertexDraw draw : this.draws) {
            draw.baseVertex = Math.toIntExact(base);
            base += draw.vertexCount;
        }
        GpuBuffer output = outputs.acquire(base * ModelGpuRecords.EntityVertex.BYTES);
        long partBytes = (long) this.partCount * ModelGpuRecords.Part.BYTES;
        long jobOffset = align(partBytes);
        long jobBytes = (long) this.jobCount * ModelGpuRecords.Job.BYTES;
        long drawBaseOffset = align(jobOffset + jobBytes);
        GpuBuffer input = inputs.acquire(drawBaseOffset + (long) this.draws.size() * Integer.BYTES);
        try (GpuBufferSlice.MappedView view = input.map(false, true)) {
            MemorySegment target = MemorySegment.ofBuffer(view.data());
            MemorySegment.copy(this.parts.segment(), 0L, target, 0L, partBytes);
            MemorySegment.copy(this.jobs.segment(), 0L, target, jobOffset, jobBytes);
            for (ModelVertexDraw draw : this.draws) {
                target.set(ModelGpuRecords.INT, drawBaseOffset + (long) draw.index * Integer.BYTES, draw.baseVertex);
            }
        }
        long inputAddress = ShaderStorage.address(input);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstants = stack.malloc(PUSH_CONSTANT_BYTES);
            pushConstants.putLong(0, polygonAddress);
            pushConstants.putLong(8, inputAddress);
            pushConstants.putLong(16, inputAddress + jobOffset);
            pushConstants.putLong(24, inputAddress + drawBaseOffset);
            pushConstants.putLong(32, ShaderStorage.address(output));
            for (int firstJob = 0; firstJob < this.jobCount; firstJob += MAX_JOBS_PER_DISPATCH) {
                int jobs = Math.min(MAX_JOBS_PER_DISPATCH, this.jobCount - firstJob);
                pushConstants.putInt(40, firstJob);
                pushConstants.putInt(44, jobs);
                int workgroups = Math.ceilDiv(jobs * ModelGpuRecords.MAX_CUBE_POLYGONS, WORKGROUP_SIZE);
                ComputeCommands.dispatch(encoder, () -> "VIM Model Vertices", pipeline, pushConstants, workgroups, 1, 1);
            }
        }
        this.vertices = output;
    }
    
    private static long align(long offset) {
        return Math.ceilDiv(offset, RECORD_ALIGNMENT) * RECORD_ALIGNMENT;
    }
    
    /**
     * What Minecraft draws a GPU draw with: the frame's vertex buffer from the draw's first vertex,
     * and Minecraft's own index buffer for quads, as it draws its unsorted quad draws. Null before
     * the frame is written, and Minecraft then skips the draw.
     */
    StagedVertexBuffer.@Nullable ExecuteInfo executeInfo(ModelVertexDraw draw) {
        if (this.vertices == null || draw.vertexCount == 0) {
            return null;
        }
        int indexCount = PrimitiveTopology.QUADS.indexCount(draw.vertexCount);
        RenderSystem.AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        autoIndices.requestIndexCount(indexCount);
        return new StagedVertexBuffer.ExecuteInfo(this.vertices, null, autoIndices.type(), draw.baseVertex, 0, indexCount, PrimitiveTopology.QUADS);
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
    }
    
    void close() {
        reset();
        this.parts.close();
        this.jobs.close();
    }
}
