package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.model.geom.ModelPart.Polygon;
import net.minecraft.client.model.geom.ModelPart.Vertex;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The faces of every model cube the GPU path has drawn, each uploaded once, into a Minecraft buffer
 * the vertex pass reads by address. A cube never changes once its model is baked, and a cube
 * remembers where its faces are (see {@link ModelCubeResidency}), so this is looked at once per
 * cube. Faces are only ever added; a buffer that runs out is replaced by one twice its size, filled
 * from a copy kept in native memory, so a face keeps its index for the store's life. A cube
 * Minecraft drops, as every cube is when a resource reload bakes the models again, leaves its faces
 * behind; once such faces are most of the store, the GPU path starts a new one
 * ({@link #mostlyCollected}). Used from the render thread only.
 */
final class ModelCubeStore {
    /**
     * A cube the GPU path leaves to Minecraft: a subclass, which may write its faces its own way,
     * or one with a face that is not a quad facing one of the six directions.
     */
    static final int CPU_ONLY = -1;
    private static final int INITIAL_POLYGONS = 16_384;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Direction[] DIRECTIONS = Direction.values();
    
    private final int id = NEXT_ID.getAndIncrement();
    private final GrowingSegment copy = new GrowingSegment(INITIAL_POLYGONS * ModelPolygonRecord.BYTES);
    /**
     * The cubes whose faces are in the store, each held weakly, so that one Minecraft drops shows
     * up in {@link #collected} once it is collected.
     */
    private final Set<ResidentCube> residentCubes = new HashSet<>();
    private final ReferenceQueue<Cube> collected = new ReferenceQueue<>();
    private int polygonCount;
    private int collectedPolygons;
    private int cpuOnlyCubes;
    private @Nullable GpuBuffer buffer;
    private @Nullable MappedView mapping;
    private @Nullable MemorySegment mapped;
    private long capacity;
    
    /**
     * What a mixin adds to every cube.
     */
    private static ModelCubeResidency residency(Object cube) {
        return (ModelCubeResidency) cube;
    }
    
    /**
     * One bit for the direction of each face, or -1 when a face is not a quad facing one of the six
     * directions.
     */
    private static int directionMask(Polygon[] polygons) {
        if (polygons.length > ModelGpuRecords.MAX_CUBE_POLYGONS) {
            return -1;
        }
        int mask = 0;
        for (Polygon polygon : polygons) {
            int direction = direction(polygon.normal());
            if (direction < 0 || polygon.vertices().length != ModelGpuRecords.POLYGON_VERTICES) {
                return -1;
            }
            mask |= 1 << direction;
        }
        return mask;
    }
    
    /**
     * The index of the direction whose unit vector the normal is. A vanilla face's normal is that
     * vector; anything else is a face the GPU path does not know the normal of.
     */
    static int direction(Vector3fc normal) {
        for (Direction direction : DIRECTIONS) {
            Vector3fc unit = direction.getUnitVec3f();
            if (normal.x() == unit.x() && normal.y() == unit.y() && normal.z() == unit.z()) {
                return direction.ordinal();
            }
        }
        return -1;
    }
    
    static void writePolygon(MemorySegment records, long offset, Polygon polygon) {
        Vertex[] vertices = polygon.vertices();
        for (int corner = 0; corner < ModelGpuRecords.POLYGON_VERTICES; corner++) {
            Vertex vertex = vertices[corner];
            long base = offset + ModelPolygonRecord.CORNERS + corner * ModelPolygonRecord.CORNER_BYTES;
            records.set(ModelGpuRecords.FLOAT, base + ModelPolygonRecord.CORNER_X, vertex.worldX());
            records.set(ModelGpuRecords.FLOAT, base + ModelPolygonRecord.CORNER_Y, vertex.worldY());
            records.set(ModelGpuRecords.FLOAT, base + ModelPolygonRecord.CORNER_Z, vertex.worldZ());
            records.set(ModelGpuRecords.FLOAT, base + ModelPolygonRecord.CORNER_U, vertex.u());
            records.set(ModelGpuRecords.FLOAT, base + ModelPolygonRecord.CORNER_V, vertex.v());
        }
        records.set(ModelGpuRecords.INT, offset + ModelPolygonRecord.DIRECTION, direction(polygon.normal()));
    }
    
    /**
     * The cube's first face in the store, uploaded first if it is not there yet, or
     * {@link #CPU_ONLY}.
     */
    int firstPolygon(Cube cube) {
        if (cube.getClass() != Cube.class) {
            return CPU_ONLY;
        }
        ModelCubeResidency residency = residency(cube);
        if (residency.vim$residentStore() == this.id) {
            return residency.vim$firstPolygon();
        }
        int directionMask = directionMask(cube.polygons);
        if (directionMask < 0) {
            residency.vim$makeResident(this.id, CPU_ONLY, 0);
            this.cpuOnlyCubes++;
            return CPU_ONLY;
        }
        int first = this.polygonCount;
        int count = cube.polygons.length;
        long start = first * ModelPolygonRecord.BYTES;
        long end = (long) (first + count) * ModelPolygonRecord.BYTES;
        ensureCapacity(first + count);
        MemorySegment records = this.copy.reserve(start, end);
        for (int i = 0; i < count; i++) {
            writePolygon(records, start + i * ModelPolygonRecord.BYTES, cube.polygons[i]);
        }
        MemorySegment.copy(records, start, Objects.requireNonNull(this.mapped), start, end - start);
        this.polygonCount += count;
        this.residentCubes.add(new ResidentCube(cube, count, this.collected));
        residency.vim$makeResident(this.id, first, directionMask);
        return first;
    }
    
    /**
     * The device address of the faces, valid until the next cube is added.
     */
    long address() {
        ensureCapacity(this.polygonCount);
        return ShaderStorage.address(Objects.requireNonNull(this.buffer));
    }
    
    /**
     * Whether most of the faces belong to cubes Minecraft no longer has, so that a new store would
     * hold only the faces still drawn.
     */
    boolean mostlyCollected() {
        for (Reference<? extends Cube> reference = this.collected.poll(); reference != null; reference = this.collected.poll()) {
            if (reference instanceof ResidentCube cube && this.residentCubes.remove(cube)) {
                this.collectedPolygons += cube.polygons();
            }
        }
        return this.collectedPolygons * 2 > this.polygonCount;
    }
    
    int residentCubes() {
        return this.residentCubes.size();
    }
    
    int collectedPolygons() {
        return this.collectedPolygons;
    }
    
    int cpuOnlyCubes() {
        return this.cpuOnlyCubes;
    }
    
    long residentBytes() {
        return this.polygonCount * ModelPolygonRecord.BYTES;
    }
    
    /**
     * Makes room for the given number of faces in the buffer. A new buffer is filled from the copy;
     * the old one is given back to Minecraft, which destroys it once the frames that read it are
     * done.
     */
    private void ensureCapacity(int polygons) {
        if (this.buffer != null && polygons <= this.capacity) {
            return;
        }
        long grown = Math.max(Math.max(polygons, INITIAL_POLYGONS), this.capacity * 2L);
        long bytes = grown * ModelPolygonRecord.BYTES;
        GpuBuffer next = RenderSystem.getDevice().createBuffer(() -> "VIM Model Polygons", GpuBuffer.USAGE_MAP_WRITE | ShaderStorage.USAGE_SHADER_STORAGE, bytes);
        MappedView nextMapping = next.map(false, true);
        MemorySegment nextMapped = MemorySegment.ofBuffer(nextMapping.data());
        long used = this.polygonCount * ModelPolygonRecord.BYTES;
        if (used > 0L) {
            MemorySegment.copy(this.copy.segment(), 0L, nextMapped, 0L, used);
        }
        releaseBuffer();
        this.buffer = next;
        this.mapping = nextMapping;
        this.mapped = nextMapped;
        this.capacity = grown;
    }
    
    private void releaseBuffer() {
        if (this.mapping != null) {
            this.mapping.close();
            this.mapping = null;
        }
        this.mapped = null;
        if (this.buffer != null) {
            this.buffer.close();
            this.buffer = null;
        }
    }
    
    void close() {
        releaseBuffer();
        this.copy.close();
    }
}