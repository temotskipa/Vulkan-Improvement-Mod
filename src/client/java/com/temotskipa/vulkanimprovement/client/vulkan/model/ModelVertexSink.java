package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The vertex consumer a model submit the GPU path takes is rendered into. Minecraft still walks
 * the model and poses its parts; each part's cubes come here instead of being compiled, and become
 * records the GPU writes the vertices from. Anything else written to it, and every cube the GPU
 * path leaves to Minecraft, goes to Minecraft's own vertex builder for the render type, so it is
 * drawn as before. One instance serves every submit in turn, from the render thread.
 */
@NullMarked
final class ModelVertexSink implements VertexConsumer {
    private final ModelGpuVertices owner;
    private final Vector3f scratch = new Vector3f();
    private @Nullable ModelDrawGroup group;
    private @Nullable RenderType renderType;
    private @Nullable VertexConsumer cpu;
    
    ModelVertexSink(ModelGpuVertices owner) {
        this.owner = owner;
    }
    
    void begin(ModelDrawGroup drawGroup, RenderType type) {
        this.group = drawGroup;
        this.renderType = type;
        this.cpu = null;
    }
    
    /**
     * Takes a posed part's cubes where Minecraft would compile them. A cube the GPU path leaves to
     * Minecraft is compiled into Minecraft's builder here, between the GPU draws before and after
     * it, so the geometry keeps Minecraft's order.
     */
    void part(List<Cube> cubes, Pose pose, int lightCoords, int overlayCoords, int color) {
        ModelCubeStore store = this.owner.store();
        ModelVertexFrame frame = this.owner.frame();
        int part = -1;
        int normals = 0;
        StagedVertexBuffer.Draw draw = null;
        ModelVertexDraw modelDraw = null;
        for (Cube cube : cubes) {
            int firstPolygon = store.firstPolygon(cube);
            if (firstPolygon == ModelCubeStore.CPU_ONLY) {
                cube.compile(pose, cpu(), lightCoords, overlayCoords, color);
                draw = null;
                continue;
            }
            int polygons = cube.polygons.length;
            if (polygons == 0) {
                continue;
            }
            if (part < 0) {
                part = frame.addPart(pose, color, overlayCoords, lightCoords);
            }
            int directions = ((ModelCubeResidency) cube).vim$directionMask() & ~normals;
            if (directions != 0) {
                frame.writeNormals(part, pose, directions, this.scratch);
                normals |= directions;
            }
            if (draw == null) {
                draw = Objects.requireNonNull(this.group).vim$modelDraw(Objects.requireNonNull(this.renderType));
                modelDraw = Objects.requireNonNull(((ModelVertexDrawHolder) draw).vim$modelDraw());
                this.cpu = null;
            }
            frame.addJob(part, firstPolygon, polygons, Objects.requireNonNull(modelDraw));
            ((ModelVertexDrawHolder) draw).vim$addQuadVertices(polygons * ModelGpuRecords.POLYGON_VERTICES);
        }
    }
    
    /**
     * Minecraft's builder for the render type, asked for again after GPU geometry, so that
     * geometry after it lands after it.
     */
    private VertexConsumer cpu() {
        if (this.cpu == null) {
            this.cpu = Objects.requireNonNull(this.group).vim$cpuVertexBuilder(Objects.requireNonNull(this.renderType));
        }
        return this.cpu;
    }
    
    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        cpu().addVertex(x, y, z);
        return this;
    }
    
    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
        cpu().addVertex(x, y, z, color, u, v, overlayCoords, lightCoords, nx, ny, nz);
    }
    
    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        cpu().setColor(r, g, b, a);
        return this;
    }
    
    @Override
    public VertexConsumer setColor(int color) {
        cpu().setColor(color);
        return this;
    }
    
    @Override
    public VertexConsumer setUv(float u, float v) {
        cpu().setUv(u, v);
        return this;
    }
    
    @Override
    public VertexConsumer setUv1(int u, int v) {
        cpu().setUv1(u, v);
        return this;
    }
    
    @Override
    public VertexConsumer setUv2(int u, int v) {
        cpu().setUv2(u, v);
        return this;
    }
    
    @Override
    public VertexConsumer setUv3(float u, float v) {
        cpu().setUv3(u, v);
        return this;
    }
    
    @Override
    public VertexConsumer setOverlay(int packedOverlayCoords) {
        cpu().setOverlay(packedOverlayCoords);
        return this;
    }
    
    @Override
    public VertexConsumer setLight(int packedLightCoords) {
        cpu().setLight(packedLightCoords);
        return this;
    }
    
    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        cpu().setNormal(x, y, z);
        return this;
    }
    
    @Override
    public VertexConsumer setLineWidth(float width) {
        cpu().setLineWidth(width);
        return this;
    }
}