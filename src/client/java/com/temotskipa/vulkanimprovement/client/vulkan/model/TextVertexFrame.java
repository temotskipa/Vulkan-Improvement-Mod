package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommands;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The text of a frame: a pose and light per text, and jobs that point at the quads each one draws.
 * The records go to the GPU in one copy, as a frame's model records do.
 */
final class TextVertexFrame {
	static final int WORKGROUP_SIZE = 64;
	/**
	 * Five addresses, then the first job and the job count.
	 */
	static final int PUSH_CONSTANT_BYTES = 5 * Long.BYTES + 2 * Integer.BYTES;
	private static final int MAX_WORKGROUPS = 65_535;
	private static final int MAX_JOBS_PER_DISPATCH = MAX_WORKGROUPS * WORKGROUP_SIZE / TextQuadRecord.JOB_QUADS;
	
	private final List<TextVertexDraw> draws = new ArrayList<>();
	private int[] instances = new int[1024 * TextQuadRecord.INSTANCE_WORDS];
	private int[] jobs = new int[4096 * TextQuadRecord.JOB_WORDS];
	private int instanceCount;
	private int jobCount;
	private long vertexCount;
	private @Nullable Object stagedBuffer;
	private @Nullable GpuBuffer vertices;
	private boolean written;
	
	boolean isEmpty() {
		return this.draws.isEmpty();
	}
	
	boolean belongsTo(Object stagedVertexBuffer) {
		return this.stagedBuffer == stagedVertexBuffer;
	}
	
	boolean written() {
		return this.written;
	}
	
	int instanceCount() {
		return this.instanceCount;
	}
	
	long vertexCount() {
		return this.vertexCount;
	}
	
	int jobCount() {
		return this.jobCount;
	}
	
	int[] instanceWords() {
		return this.instances;
	}
	
	int[] jobWords() {
		return this.jobs;
	}
	
	TextVertexDraw addDraw(Object stagedVertexBuffer) {
		this.stagedBuffer = stagedVertexBuffer;
		if (this.draws.size() >= TextQuadRecord.MAX_DRAWS) {
			throw new IllegalStateException("More than " + TextQuadRecord.MAX_DRAWS + " GPU text draws in one frame");
		}
		TextVertexDraw draw = new TextVertexDraw(this.draws.size());
		this.draws.add(draw);
		return draw;
	}
	
	int addInstance(Matrix4fc pose, int light) {
		int offset = this.instanceCount * TextQuadRecord.INSTANCE_WORDS;
		int[] records = this.instances = reserve(this.instances, offset + TextQuadRecord.INSTANCE_WORDS);
		records[offset] = Float.floatToRawIntBits(pose.m00());
		records[offset + 1] = Float.floatToRawIntBits(pose.m01());
		records[offset + 2] = Float.floatToRawIntBits(pose.m02());
		records[offset + 3] = Float.floatToRawIntBits(pose.m10());
		records[offset + 4] = Float.floatToRawIntBits(pose.m11());
		records[offset + 5] = Float.floatToRawIntBits(pose.m12());
		records[offset + 6] = Float.floatToRawIntBits(pose.m20());
		records[offset + 7] = Float.floatToRawIntBits(pose.m21());
		records[offset + 8] = Float.floatToRawIntBits(pose.m22());
		records[offset + 9] = Float.floatToRawIntBits(pose.m30());
		records[offset + 10] = Float.floatToRawIntBits(pose.m31());
		records[offset + 11] = Float.floatToRawIntBits(pose.m32());
		records[offset + TextQuadRecord.INSTANCE_LIGHT_WORD] = light;
		return this.instanceCount++;
	}
	
	private static int[] reserve(int[] array, int words) {
		return words <= array.length ? array : Arrays.copyOf(array, Math.max(words, array.length * 2));
	}
	
	/**
	 * Adds a run of a text's quads, in jobs of as many quads as a job holds.
	 */
	void addRun(int instance, int firstQuad, int quads, TextVertexDraw draw) {
		int count = Math.ceilDiv(quads, TextQuadRecord.JOB_QUADS);
		int offset = this.jobCount * TextQuadRecord.JOB_WORDS;
		int[] records = this.jobs = reserve(this.jobs, offset + count * TextQuadRecord.JOB_WORDS);
		int firstVertex = draw.vertexCount;
		for (int job = 0; job < count; job++) {
			int first = job * TextQuadRecord.JOB_QUADS;
			int held = Math.min(TextQuadRecord.JOB_QUADS, quads - first);
			records[offset] = instance;
			records[offset + 1] = firstQuad + first;
			records[offset + 2] = draw.index | held << TextQuadRecord.DRAW_BITS;
			records[offset + 3] = firstVertex;
			firstVertex += held * TextQuadRecord.QUAD_VERTICES;
			offset += TextQuadRecord.JOB_WORDS;
		}
		this.vertexCount += firstVertex - draw.vertexCount;
		draw.vertexCount = firstVertex;
		this.jobCount += count;
	}
	
	/**
	 * Copies the records to the GPU and records the pass that writes the frame's vertices.
	 */
	void write(ModelBufferPool inputs, ModelBufferPool outputs, ComputePipeline pipeline, long quadAddress) {
		long base = 0L;
		for (TextVertexDraw draw : this.draws) {
			draw.baseVertex = Math.toIntExact(base);
			base += draw.vertexCount;
		}
		long instanceBytes = (long) this.instanceCount * TextQuadRecord.INSTANCE_WORDS * Integer.BYTES;
		long jobOffset = align(instanceBytes);
		long drawBaseOffset = align(jobOffset + (long) this.jobCount * TextQuadRecord.JOB_WORDS * Integer.BYTES);
		GpuBuffer input = inputs.acquire(drawBaseOffset + (long) this.draws.size() * Integer.BYTES);
		try (MappedView view = input.map(false, true)) {
			MemorySegment target = MemorySegment.ofBuffer(view.data());
			MemorySegment.copy(this.instances, 0, target, ModelGpuRecords.INT, 0L, this.instanceCount * TextQuadRecord.INSTANCE_WORDS);
			MemorySegment.copy(this.jobs, 0, target, ModelGpuRecords.INT, jobOffset, this.jobCount * TextQuadRecord.JOB_WORDS);
			for (TextVertexDraw draw : this.draws) {
				target.set(ModelGpuRecords.INT, drawBaseOffset + (long) draw.index * Integer.BYTES, draw.baseVertex);
			}
		}
		GpuBuffer output = outputs.acquire(base * TextQuadRecord.VERTEX_WORDS * Integer.BYTES);
		long inputAddress = ShaderStorage.address(input);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer pushConstants = stack.malloc(PUSH_CONSTANT_BYTES);
			pushConstants.putLong(0, quadAddress);
			pushConstants.putLong(8, inputAddress);
			pushConstants.putLong(16, inputAddress + jobOffset);
			pushConstants.putLong(24, inputAddress + drawBaseOffset);
			pushConstants.putLong(32, ShaderStorage.address(output));
			for (int firstJob = 0; firstJob < this.jobCount; firstJob += MAX_JOBS_PER_DISPATCH) {
				int jobs = Math.min(MAX_JOBS_PER_DISPATCH, this.jobCount - firstJob);
				pushConstants.putInt(40, firstJob);
				pushConstants.putInt(44, jobs);
				ComputeCommands.dispatch(encoder, () -> "VIM Text Vertices", pipeline, pushConstants, Math.ceilDiv(jobs * TextQuadRecord.JOB_QUADS, WORKGROUP_SIZE), 1, 1);
			}
		}
		this.vertices = output;
		this.written = true;
	}
	
	private static long align(long offset) {
		return offset + 15L & ~15L;
	}
	
	@Nullable ExecuteInfo executeInfo(TextVertexDraw draw) {
		if (!this.written || draw.vertexCount == 0) {
			return null;
		}
		int indexCount = PrimitiveTopology.QUADS.indexCount(draw.vertexCount);
		AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		autoIndices.requestIndexCount(indexCount);
		return new ExecuteInfo(Objects.requireNonNull(this.vertices), null, autoIndices.type(), draw.baseVertex, 0, indexCount, PrimitiveTopology.QUADS);
	}
	
	void reset() {
		this.draws.clear();
		this.instanceCount = 0;
		this.jobCount = 0;
		this.vertexCount = 0L;
		this.stagedBuffer = null;
		this.vertices = null;
		this.written = false;
	}
}