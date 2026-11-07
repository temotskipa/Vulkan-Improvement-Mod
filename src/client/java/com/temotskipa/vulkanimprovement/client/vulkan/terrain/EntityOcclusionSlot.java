package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.api.commands.GpuFence;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;

/**
 * One frame's boxes for {@link EntityOcclusion}, in one slot of its ring: the boxes Minecraft's
 * entities and block entities were noted with, relative to the camera, and what each belongs to,
 * kept until the GPU has tested them and the CPU has read which it found hidden. Render thread only.
 */
final class EntityOcclusionSlot {
	/**
	 * The floats of one box: its low corner, then its high one.
	 */
	static final int BOX_FLOATS = 6;
	private final int index;
	private final float[] boxes = new float[EntityOcclusion.BOX_CAPACITY * BOX_FLOATS];
	// An entity's id, or a block entity's position packed as Minecraft packs block positions.
	private final long[] keys = new long[EntityOcclusion.BOX_CAPACITY];
	private final boolean[] blockEntities = new boolean[EntityOcclusion.BOX_CAPACITY];
	private int count;
	private double cameraX;
	private double cameraY;
	private double cameraZ;
	private long recordedNanos;
	private int levelGeneration;
	// Set while the GPU has the slot's test to run.
	private @Nullable GpuFence fence;
	private long sequence;

	EntityOcclusionSlot(int index) {
		this.index = index;
	}

	int index() {
		return this.index;
	}

	/**
	 * Starts the slot over for a frame seen from the given camera position.
	 */
	void begin(Vec3 camera, long nanos, int generation) {
		this.count = 0;
		this.cameraX = camera.x();
		this.cameraY = camera.y();
		this.cameraZ = camera.z();
		this.recordedNanos = nanos;
		this.levelGeneration = generation;
	}

	/**
	 * Whether the slot has no room for another box, which then goes untested.
	 */
	boolean full() {
		return this.count >= this.keys.length;
	}

	/**
	 * Notes a box, given in the level's coordinates, in a slot that is not full.
	 */
	void add(long key, boolean blockEntity, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		int at = this.count * BOX_FLOATS;
		this.boxes[at] = (float) (minX - this.cameraX);
		this.boxes[at + 1] = (float) (minY - this.cameraY);
		this.boxes[at + 2] = (float) (minZ - this.cameraZ);
		this.boxes[at + 3] = (float) (maxX - this.cameraX);
		this.boxes[at + 4] = (float) (maxY - this.cameraY);
		this.boxes[at + 5] = (float) (maxZ - this.cameraZ);
		this.keys[this.count] = key;
		this.blockEntities[this.count] = blockEntity;
		this.count++;
	}

	int count() {
		return this.count;
	}

	long key(int box) {
		return this.keys[box];
	}

	boolean blockEntity(int box) {
		return this.blockEntities[box];
	}

	/**
	 * Where the camera was in the slot's frame.
	 */
	Vec3 camera() {
		return new Vec3(this.cameraX, this.cameraY, this.cameraZ);
	}

	double cameraDistanceSquared(Vec3 position) {
		return position.distanceToSqr(this.cameraX, this.cameraY, this.cameraZ);
	}

	long recordedNanos() {
		return this.recordedNanos;
	}

	int levelGeneration() {
		return this.levelGeneration;
	}

	long sequence() {
		return this.sequence;
	}

	/**
	 * Writes the boxes where the GPU reads them, as the test pass lays them out.
	 */
	void writeBoxes(MemorySegment target, long offset) {
		MemorySegment.copy(this.boxes, 0, target, TerrainGpuRecords.FLOAT, offset, this.count * BOX_FLOATS);
	}

	/**
	 * Called once the slot's test is recorded into a frame, with a fence of that frame's submit.
	 */
	void submit(GpuFence submitted, long submitSequence) {
		this.fence = submitted;
		this.sequence = submitSequence;
	}

	/**
	 * Whether the GPU has the slot's test to run or has run it and the CPU has not read it.
	 */
	boolean pending() {
		return this.fence != null;
	}

	/**
	 * Whether the frame the slot's test was recorded into has finished on the GPU, without waiting.
	 */
	boolean finished() {
		GpuFence active = this.fence;
		return active != null && active.awaitCompletion(0L);
	}

	/**
	 * Frees the slot for a later frame.
	 */
	void release() {
		GpuFence active = this.fence;
		if (active != null) {
			active.close();
		}
		this.fence = null;
		this.count = 0;
	}
}
