package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBuffer;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.DeviceBufferPlacement;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.lang.foreign.MemorySegment;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Leaves out the entities and block entities that the GPU found hidden behind what Minecraft drew
 * ({@code vim.entityOcclusionCulling}, off by default). As Minecraft looks at the entities in view
 * and the block entities of the sections in view, each one's box is noted; once Minecraft closes
 * its opaque pass, the GPU tests them all against the depth pyramid of what that pass drew, the
 * entities in it included. The CPU never waits for the GPU: it reads a frame's results once that
 * frame has finished on the GPU, a few frames later, and leaves out what they found hidden until
 * newer ones come. What is left out is still noted and tested every frame, so it comes back once a
 * frame's test sees it.
 * <p>
 * So the culling lags: an entity that comes out from behind a hill shows a few frames late, which
 * the two phases of terrain culling never let happen, and that is why this is off by default. The
 * lag is kept short. A box is grown by the ground its entity covers in a few ticks, a box that
 * reaches past the edge of the screen is never found hidden, and results are used only while they
 * are younger than a fifth of a second and the camera is within a block of where it was. An entity
 * whose outline or name shows through what hides it is never left out, nor is a fishing hook, whose
 * line reaches back to the rod, and Minecraft still draws a leashed entity whose leash holder is in
 * view.
 * <p>
 * The test runs where Minecraft closes its opaque pass before anything translucent is drawn, which
 * it does with improved transparency, whether the mesh path draws the terrain or Minecraft does.
 * Render thread only.
 */
public final class EntityOcclusion {
	/**
	 * How many boxes one frame tests; any past them are drawn untested.
	 */
	static final int BOX_CAPACITY = 16384;
	private static final EntityOcclusion INSTANCE = new EntityOcclusion();
	// Enough slots for the frames the GPU may be behind by, and one being recorded.
	private static final int SLOT_COUNT = 4;
	private static final long BOX_BYTES = (long) EntityOcclusionSlot.BOX_FLOATS * Float.BYTES;
	private static final long SLOT_BYTES = BOX_CAPACITY * (BOX_BYTES + Integer.BYTES);
	private static final long MAX_RESULT_AGE_NANOS = 200_000_000L;
	private static final double MAX_CAMERA_MOVE_SQUARED = 1.0;
	// An entity's box is grown by as far as it moved in its last tick, this many times over: the
	// ticks a result may be used in.
	private static final double MOTION_TICKS = 4.0;
	private static final double MAX_MOTION_GROWTH = 4.0;
	// Minecraft draws a shadow no wider than this, and only nearer the camera than this.
	private static final double MAX_SHADOW_RADIUS = 32.0;
	private static final double SHADOW_DISTANCE_SQUARED = 256.0;
	private final EntityOcclusionSlot[] slots = new EntityOcclusionSlot[SLOT_COUNT];
	private final IntOpenHashSet hiddenEntities = new IntOpenHashSet();
	private final LongOpenHashSet hiddenBlockEntities = new LongOpenHashSet();
	private final LongAdder testedFrames = new LongAdder();
	private final LongAdder busyFrames = new LongAdder();
	private final LongAdder refusedTests = new LongAdder();
	private final LongAdder staleFrames = new LongAdder();
	private final LongAdder testedBoxes = new LongAdder();
	private final LongAdder hiddenBoxes = new LongAdder();
	private final LongAdder untestedBoxes = new LongAdder();
	private final LongAdder culledEntities = new LongAdder();
	private final LongAdder culledBlockEntities = new LongAdder();
	private WeakReference<@Nullable Level> level = new WeakReference<>(null);
	// Counts the levels seen, so that results from one are never used in another.
	private int levelGeneration;
	private @Nullable DeviceBuffer ring;
	private @Nullable EntityOcclusionSlot recording;
	private int nextSlot;
	private long nextSequence = 1L;
	// What the results in use came from, sequence 0 for none.
	private long resultSequence;
	private long resultNanos;
	private int resultGeneration;
	private Vec3 resultCamera = Vec3.ZERO;
	private boolean applying;
	private boolean lookingAtEntities;
	private boolean shadows;

	private EntityOcclusion() {
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			this.slots[slot] = new EntityOcclusionSlot(slot);
		}
	}

	public static EntityOcclusion get() {
		return INSTANCE;
	}

	/**
	 * Called as Minecraft begins extracting a frame of the level, before it looks at any entity:
	 * takes the results of the newest frame the GPU has finished, decides whether they are used in
	 * this frame, and readies a slot for this frame's boxes.
	 */
	public void beginFrame(Camera camera, @Nullable Level frameLevel) {
		this.recording = null;
		this.applying = false;
		if (frameLevel != this.level.get()) {
			this.level = new WeakReference<>(frameLevel);
			this.levelGeneration++;
			clearResults();
		}
		collect();
		if (!enabled() || frameLevel == null || !camera.isInitialized()) {
			clearResults();
			return;
		}
		Vec3 position = camera.position();
		long now = System.nanoTime();
		if (this.resultSequence != 0L) {
			this.applying = this.resultGeneration == this.levelGeneration && now - this.resultNanos <= MAX_RESULT_AGE_NANOS && position.distanceToSqr(this.resultCamera) <= MAX_CAMERA_MOVE_SQUARED;
			if (!this.applying) {
				this.staleFrames.increment();
			}
		}
		this.shadows = Minecraft.getInstance().options.entityShadows().get();
		this.recording = freeSlot();
		if (this.recording == null) {
			this.busyFrames.increment();
			return;
		}
		this.recording.begin(position, now, this.levelGeneration);
	}

	private static boolean enabled() {
		MeshShaderTerrainProgram program = MeshShaderTerrainProgram.get();
		return TerrainRendererDebugConfig.entityOcclusionCullingEnabled() && program.device() != null && program.depthPyramidPipeline() != null && Minecraft.getInstance().gameRenderer.useImprovedTransparency();
	}

	/**
	 * Called as Minecraft starts and stops looking at the entities in view, which is when the
	 * entity renderers' view tests are for this frame's entities.
	 */
	public void lookAtEntities(boolean looking) {
		this.lookingAtEntities = looking;
	}

	/**
	 * Whether Minecraft is looking at the entities in view in a frame that notes or leaves them out.
	 */
	public boolean testingEntities() {
		return this.lookingAtEntities && (this.recording != null || this.applying);
	}

	/**
	 * Notes the box of an entity Minecraft found in view, for this frame's test, and says whether
	 * the results in use found it hidden.
	 *
	 * @param box          the box Minecraft tested against the view, which it grew by half a block
	 * @param shadowRadius how far the entity's shadow reaches out from it
	 */
	public boolean noteEntity(Entity entity, AABB box, float shadowRadius) {
		EntityOcclusionSlot slot = this.recording;
		if (slot != null && slot.full()) {
			this.untestedBoxes.increment();
		}
		else if (slot != null) {
			double motion = Math.min(MAX_MOTION_GROWTH, MOTION_TICKS * Math.sqrt(Mth.lengthSquared(entity.getX() - entity.xOld, entity.getY() - entity.yOld, entity.getZ() - entity.zOld)));
			// A shadow lies on the tops of the blocks below the entity, as deep as its radius.
			double shadow = this.shadows && slot.cameraDistanceSquared(entity.position()) < SHADOW_DISTANCE_SQUARED ? Math.clamp(shadowRadius, 0.0, MAX_SHADOW_RADIUS) : 0.0;
			slot.add(entity.getId(), false, box.minX - motion - shadow, box.minY - motion - shadow - 1.0, box.minZ - motion - shadow, box.maxX + motion + shadow, box.maxY + motion, box.maxZ + motion + shadow);
		}
		return this.applying && this.hiddenEntities.contains(entity.getId());
	}

	/**
	 * Called for an entity left out of the frame.
	 */
	public void culledEntity() {
		this.culledEntities.increment();
	}

	/**
	 * Whether this frame notes or leaves out the block entities of the sections in view.
	 */
	public boolean testingBlockEntities() {
		return this.recording != null || this.applying;
	}

	/**
	 * Whether a block entity of a section in view is left out of the frame, as the results in use
	 * found it hidden. One left out is noted for this frame's test.
	 */
	public boolean cullsBlockEntity(BlockPos position) {
		if (!this.applying || !this.hiddenBlockEntities.contains(position.asLong())) {
			return false;
		}
		noteBlockEntity(position);
		this.culledBlockEntities.increment();
		return true;
	}

	/**
	 * Notes the box of a block entity Minecraft draws, for this frame's test: its block grown by one
	 * each way, which holds all that Minecraft draws for a block entity drawn with its section, such
	 * as a bed's other half, a banner or a moving piston.
	 */
	public void noteBlockEntity(BlockPos position) {
		EntityOcclusionSlot slot = this.recording;
		if (slot != null && slot.full()) {
			this.untestedBoxes.increment();
		}
		else if (slot != null) {
			slot.add(position.asLong(), true, position.getX() - 1.0, position.getY() - 1.0, position.getZ() - 1.0, position.getX() + 2.0, position.getY() + 2.0, position.getZ() + 2.0);
		}
	}

	/**
	 * Whether this frame noted boxes for the GPU to test where Minecraft closes its opaque pass.
	 */
	boolean wantsTest() {
		EntityOcclusionSlot slot = this.recording;
		return slot != null && slot.count() > 0;
	}

	/**
	 * Records the test of this frame's boxes against the depth pyramid just built, outside any
	 * render pass.
	 */
	void recordTest(VkCommandBuffer commandBuffer, VulkanDevice device, MeshShaderTerrainProgram program, TerrainDepthPyramid pyramid) {
		EntityOcclusionSlot slot = this.recording;
		this.recording = null;
		if (slot == null || slot.count() == 0) {
			return;
		}
		DeviceBuffer buffer = ring(device);
		long boxes = boxesOffset(slot);
		slot.writeBoxes(buffer.mapped(), boxes);
		if (!program.prepareBoxTest(commandBuffer, buffer.deviceAddress() + boxes, buffer.deviceAddress() + resultsOffset(slot), slot.count(), pyramid)) {
			this.refusedTests.increment();
			return;
		}
		slot.submit(device.createCommandEncoder().createFence(), this.nextSequence++);
		this.nextSlot = (slot.index() + 1) % SLOT_COUNT;
		this.testedFrames.increment();
	}

	private DeviceBuffer ring(VulkanDevice device) {
		DeviceBuffer buffer = this.ring;
		if (buffer == null) {
			buffer = new DeviceBuffer(device, "VIM Entity Occlusion Boxes", SLOT_COUNT * SLOT_BYTES, ShaderStorage.USAGE_SHADER_STORAGE, DeviceBufferPlacement.HOST);
			this.ring = buffer;
		}
		return buffer;
	}

	private static long boxesOffset(EntityOcclusionSlot slot) {
		return slot.index() * SLOT_BYTES;
	}

	private static long resultsOffset(EntityOcclusionSlot slot) {
		return slot.index() * SLOT_BYTES + BOX_CAPACITY * BOX_BYTES;
	}

	/**
	 * The slot the next frame's boxes go in: the oldest the GPU is done with, or null while it has
	 * every slot's test still to run.
	 */
	private @Nullable EntityOcclusionSlot freeSlot() {
		for (int step = 0; step < SLOT_COUNT; step++) {
			EntityOcclusionSlot slot = this.slots[(this.nextSlot + step) % SLOT_COUNT];
			if (!slot.pending()) {
				return slot;
			}
		}
		return null;
	}

	/**
	 * Frees every slot whose frame has finished on the GPU, and takes the results of the newest.
	 * Submits finish in order, so no older frame finishes after it.
	 */
	private void collect() {
		EntityOcclusionSlot newest = null;
		for (EntityOcclusionSlot slot : this.slots) {
			if (!slot.finished()) {
				continue;
			}
			if (newest == null || slot.sequence() > newest.sequence()) {
				if (newest != null) {
					newest.release();
				}
				newest = slot;
			}
			else {
				slot.release();
			}
		}
		if (newest != null) {
			if (newest.sequence() > this.resultSequence) {
				takeResults(newest);
			}
			newest.release();
		}
	}

	private void takeResults(EntityOcclusionSlot slot) {
		DeviceBuffer buffer = this.ring;
		if (buffer == null) {
			return;
		}
		MemorySegment mapped = buffer.mapped();
		long results = resultsOffset(slot);
		this.hiddenEntities.clear();
		this.hiddenBlockEntities.clear();
		int hidden = 0;
		for (int box = 0; box < slot.count(); box++) {
			if (mapped.get(TerrainGpuRecords.INT, results + (long) box * Integer.BYTES) == 0) {
				continue;
			}
			hidden++;
			if (slot.blockEntity(box)) {
				this.hiddenBlockEntities.add(slot.key(box));
			}
			else {
				this.hiddenEntities.add((int) slot.key(box));
			}
		}
		this.testedBoxes.add(slot.count());
		this.hiddenBoxes.add(hidden);
		this.resultSequence = slot.sequence();
		this.resultNanos = slot.recordedNanos();
		this.resultGeneration = slot.levelGeneration();
		this.resultCamera = slot.camera();
	}

	private void clearResults() {
		this.hiddenEntities.clear();
		this.hiddenBlockEntities.clear();
		this.resultSequence = 0L;
		this.applying = false;
	}

	private void releaseSlots() {
		for (EntityOcclusionSlot slot : this.slots) {
			slot.release();
		}
		this.recording = null;
		this.nextSlot = 0;
		clearResults();
	}

	/**
	 * Called when the renderer is configured for a device: the ring of the last one is given back.
	 */
	void reset() {
		releaseSlots();
		DeviceBuffer buffer = this.ring;
		if (buffer != null) {
			buffer.close();
		}
		this.ring = null;
	}

	/**
	 * Called when the renderer shuts down at once, with the GPU done with the ring.
	 */
	void shutdownNow() {
		releaseSlots();
		DeviceBuffer buffer = this.ring;
		if (buffer != null) {
			buffer.destroyNow();
		}
		this.ring = null;
	}

	public String describe() {
		return "enabled=" + TerrainRendererDebugConfig.entityOcclusionCullingEnabled() + ", applying=" + this.applying + ", testedFrames=" + this.testedFrames.sum() + ", busyFrames=" + this.busyFrames.sum() + ", refusedTests=" + this.refusedTests.sum() + ", staleFrames=" + this.staleFrames.sum() + ", testedBoxes=" + this.testedBoxes.sum() + ", hiddenBoxes=" + this.hiddenBoxes.sum() + ", untestedBoxes=" + this.untestedBoxes.sum() + ", culledEntities=" + this.culledEntities.sum() + ", culledBlockEntities=" + this.culledBlockEntities.sum() + ", hiddenNow=" + this.hiddenEntities.size() + "/" + this.hiddenBlockEntities.size();
	}
}
