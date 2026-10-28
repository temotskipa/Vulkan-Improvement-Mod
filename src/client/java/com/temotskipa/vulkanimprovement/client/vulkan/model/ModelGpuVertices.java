package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputeCommands;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ComputePipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompiler;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.slf4j.Logger;

import java.lang.foreign.MemorySegment;
import java.util.*;

/**
 * Model vertices written by the GPU instead of the CPU (step 2 of the GPU-driven pipeline plan).
 * While Minecraft prepares the level's features, a model submit whose vertices the GPU can write
 * exactly as Minecraft would is rendered into a {@link ModelVertexSink}: Minecraft animates and
 * poses the model as always, and each posed part's cubes become records instead of vertices. Once
 * Minecraft has uploaded its own vertices, one compute pass writes the cubes' vertices into a
 * Minecraft buffer, and Minecraft draws them with its own render types as draws of its staged
 * vertex buffer. With {@code vim.gpuModelMeshShaders} on as well, a draw whose pipelines all have a
 * mesh pipeline ({@link ModelMeshPipelines}) is drawn by the mesh stage instead, straight from the
 * records, and no vertices are written for it.
 *
 * <p>The path is switched with {@code vim.gpuModelVertices}, between frames; its vertex pass is
 * built the first time it is on. A submit stays on Minecraft's path when the GPU could not write the same bytes: a decal or a
 * remapped texture, another vertex format, quads Minecraft sorts on upload. So does every model
 * while another mod changes a class the path stands in for (see {@link #REPLACED_CLASSES}). Used
 * from the render thread only.
 */
public final class ModelGpuVertices {
    static final String SHADER = "assets/vulkanimprovement/shaders/model/model_vertices.comp.glsl";
    static final String SHADER_SPIRV = "assets/vulkanimprovement/shaders/model/spirv/model_vertices.comp.spv";
    /**
     * The classes whose work the path does in their place. A mod that changes one of them may change
     * what a model looks like in a way the GPU path would not reproduce.
     */
    static final Set<String> REPLACED_CLASSES = Set.of("net.minecraft.client.model.geom.ModelPart", "net.minecraft.client.model.geom.ModelPart$Cube", "net.minecraft.client.model.geom.ModelPart$Polygon", "net.minecraft.client.model.geom.ModelPart$Vertex", "net.minecraft.client.renderer.feature.ModelFeatureRenderer", "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group", "com.mojang.blaze3d.vertex.BufferBuilder");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModelGpuVertices INSTANCE = new ModelGpuVertices();
    
    private final ModelVertexSink sink = new ModelVertexSink(this);
    private @Nullable VulkanDevice device;
    private @Nullable ComputePipeline pipeline;
    private @Nullable ModelCubeStore store;
    private @Nullable ModelVertexFrame frame;
    private @Nullable ModelBufferPool inputBuffers;
    private @Nullable ModelBufferPool vertexBuffers;
    /**
     * Whether the device has mesh shaders, so that GPU model draws may be drawn by them. Read on the
     * threads Minecraft compiles pipelines on.
     */
    private volatile boolean meshShadersAvailable;
    /**
     * The vertex buffer Minecraft draws a mesh draw from, which marks the draw as one.
     */
    private @Nullable GpuBuffer meshMarker;
    private @Nullable GpuBuffer meshConversions;
    private @Nullable List<String> otherModMixins;
    private @Nullable RenderType offeredRenderType;
    private @Nullable Object stagedBufferUsed;
    private boolean preparingLevel;
    private boolean frameWritten;
    private long gpuSubmits;
    private long cpuSubmits;
    private long writtenFrames;
    private long lastFrameParts;
    private long lastFrameCubes;
    private long lastFrameVertices;
    private long lastFrameDraws;
    private long lastFrameMeshDraws;
    private long lastFrameMeshCubes;
    private long meshDraws;
    private long meshDrawsLeftOut;
    /**
     * Frames whose GPU model draws Minecraft ended without the GPU having written them, so that it
     * skipped them. Should stay 0.
     */
    private long framesLeftOut;
    private boolean warnedLeftOut;
    private long storesRenewed;
    
    private ModelGpuVertices() {
    }
    
    public static ModelGpuVertices get() {
        return INSTANCE;
    }
    
    private static boolean insideRenderPass() {
        return ComputeCommands.insideRenderPass(RenderSystem.getDevice().createCommandEncoder());
    }
    
    /**
     * Whether the GPU writes the bytes Minecraft would: no decal or texture remapping wraps the
     * builder, the render type's vertices are Minecraft's entity format in quads, and Minecraft
     * does not sort them as it uploads them.
     */
    private static boolean writesExactly(Submit<?> submit) {
        if (submit.sheetedDecalPose() != null || submit.uvMapping() != null) {
            return false;
        }
        RenderType renderType = submit.renderType();
        if (renderType.format() != DefaultVertexFormat.ENTITY || renderType.primitiveTopology() != PrimitiveTopology.QUADS) {
            return false;
        }
        return !renderType.sortOnUpload() || Minecraft.getInstance().gameRenderer.useImprovedTransparency();
    }
    
    /**
     * Takes Minecraft's new device, and builds the vertex pass for it at once when the path is on.
     */
    public synchronized void configure(VulkanDevice vulkanDevice) {
        shutdownNow();
        this.device = vulkanDevice;
        this.meshShadersAvailable = VulkanImprovementCapabilities.snapshot().meshShaderExtension();
        if (TerrainRendererDebugConfig.gpuModelVerticesEnabled()) {
            ready();
        }
    }
    
    /**
     * Whether the vertex pass is built, building it the first time the path is on.
     */
    private synchronized boolean ready() {
        if (this.pipeline != null) {
            return true;
        }
        VulkanDevice vulkanDevice = this.device;
        if (vulkanDevice == null) {
            return false;
        }
        try (ShaderCompiler compiler = new ShaderCompiler()) {
            long module = compiler.compile(vulkanDevice, SHADER, SHADER_SPIRV, Shaderc.shaderc_compute_shader);
            try {
                this.pipeline = ComputePipeline.create(vulkanDevice, module, ModelVertexFrame.PUSH_CONSTANT_BYTES, "VIM Model Vertices");
            } finally {
                VK10.vkDestroyShaderModule(vulkanDevice.vkDevice(), module, null);
            }
            LOGGER.info("[Vulkan Improvement] GPU model vertices ready ({})", compiler.lastCompileSource());
        }
        this.store = new ModelCubeStore();
        this.frame = new ModelVertexFrame();
        this.inputBuffers = new ModelBufferPool(() -> "VIM Model Records", GpuBuffer.USAGE_MAP_WRITE | ShaderStorage.USAGE_SHADER_STORAGE);
        this.vertexBuffers = new ModelBufferPool(() -> "VIM Model Vertices", GpuBuffer.USAGE_VERTEX | ShaderStorage.USAGE_SHADER_STORAGE);
        if (this.meshShadersAvailable) {
            this.meshMarker = RenderSystem.getDevice().createBuffer(() -> "VIM Model Mesh Marker", GpuBuffer.USAGE_VERTEX, 16L);
            this.meshConversions = createConversionTable();
        }
        return true;
    }
    
    /**
     * The mesh stage's 8-bit conversion table, in a buffer it reads by address.
     */
    private static GpuBuffer createConversionTable() {
        float[] table = ModelMeshShader.conversionTable();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "VIM Model Mesh Conversions", GpuBuffer.USAGE_MAP_WRITE | ShaderStorage.USAGE_SHADER_STORAGE, (long) table.length * Float.BYTES);
        try (MappedView view = buffer.map(false, true)) {
            MemorySegment target = MemorySegment.ofBuffer(view.data());
            for (int i = 0; i < table.length; i++) {
                target.set(ModelGpuRecords.FLOAT, (long) i * Float.BYTES, table[i]);
            }
        }
        return buffer;
    }
    
    public synchronized void shutdownNow() {
        this.preparingLevel = false;
        this.offeredRenderType = null;
        if (this.frame != null) {
            this.frame.close();
            this.frame = null;
        }
        if (this.store != null) {
            this.store.close();
            this.store = null;
        }
        if (this.inputBuffers != null) {
            this.inputBuffers.close();
            this.inputBuffers = null;
        }
        if (this.vertexBuffers != null) {
            this.vertexBuffers.close();
            this.vertexBuffers = null;
        }
        if (this.meshMarker != null) {
            this.meshMarker.close();
            this.meshMarker = null;
        }
        if (this.meshConversions != null) {
            this.meshConversions.close();
            this.meshConversions = null;
        }
        this.meshShadersAvailable = false;
        if (this.pipeline != null && this.device != null) {
            this.pipeline.destroy(this.device);
        }
        this.pipeline = null;
        this.device = null;
        this.stagedBufferUsed = null;
    }
    
    /**
     * Minecraft is about to prepare the level's features, outside any render pass. The path takes
     * submits until {@link #endLevelPrepare}, if it is on, no other mod changes what it replaces,
     * and its vertex pass is built.
     */
    public void beginLevelPrepare() {
        this.preparingLevel = TerrainRendererDebugConfig.gpuModelVerticesEnabled() && !insideRenderPass() && untouchedByOtherMods() && ready();
        if (this.preparingLevel && frame().isEmpty() && store().mostlyCollected()) {
            renewStore();
        }
    }
    
    /**
     * Starts a new cube store in place of one whose faces are mostly those of cubes Minecraft
     * dropped, as after a resource reload. The cubes still drawn upload their faces to it as they
     * are drawn; the old store's buffer goes back to Minecraft, which destroys it once the frames
     * that read it are done.
     */
    private void renewStore() {
        store().close();
        this.store = new ModelCubeStore();
        this.storesRenewed++;
    }
    
    public void endLevelPrepare() {
        this.preparingLevel = false;
        this.offeredRenderType = null;
    }
    
    private boolean untouchedByOtherMods() {
        if (this.otherModMixins == null) {
            this.otherModMixins = OtherModMixins.targeting();
            if (!this.otherModMixins.isEmpty()) {
                LOGGER.warn("[Vulkan Improvement] GPU model vertices stay off: other mods change what they replace: {}", this.otherModMixins);
            }
        }
        return this.otherModMixins.isEmpty();
    }
    
    /**
     * Whether the GPU writes the vertices of a model submit Minecraft is about to render. When it
     * does, the vertex builder Minecraft asks for next is the GPU path's.
     */
    public boolean offer(Submit<?> submit) {
        if (!this.preparingLevel) {
            return false;
        }
        if (!writesExactly(submit)) {
            this.cpuSubmits++;
            return false;
        }
        this.offeredRenderType = submit.renderType();
        return true;
    }
    
    public void clearOffer() {
        this.offeredRenderType = null;
    }
    
    /**
     * The GPU path's vertex builder, when Minecraft's draw group is asked for the builder of the
     * submit just offered; otherwise null, and Minecraft hands out its own.
     */
    public @Nullable VertexConsumer takeOffer(ModelDrawGroup group, RenderType renderType) {
        if (this.offeredRenderType != renderType) {
            return null;
        }
        this.offeredRenderType = null;
        this.gpuSubmits++;
        this.sink.begin(group, renderType);
        return this.sink;
    }
    
    /**
     * Takes a part's cubes where Minecraft would compile them, when it renders into the GPU path's
     * builder; false otherwise.
     */
    public boolean compileOnGpu(VertexConsumer buffer, ModelPartResidency part, List<Cube> cubes, PoseStack.Pose pose, int lightCoords, int overlayCoords, int color) {
        if (buffer != this.sink) {
            return false;
        }
        this.sink.part(part, cubes, pose, lightCoords, overlayCoords, color);
        return true;
    }
    
    /**
     * Makes a draw Minecraft's draw group just added a GPU draw of the frame.
     */
    public void adopt(Object stagedVertexBuffer, Draw draw, PreparedRenderType renderType) {
        ((ModelVertexDrawHolder) draw).vim$setModelDraw(frame().addDraw(stagedVertexBuffer, renderType));
        this.stagedBufferUsed = stagedVertexBuffer;
    }
    
    /**
     * Minecraft has uploaded the staged vertex buffer's own vertices, if it has any: records the
     * pass that writes the GPU draws' vertices, which runs before the draws as Minecraft's uploads
     * do.
     */
    public void upload(Object stagedVertexBuffer) {
        ModelVertexFrame current = this.frame;
        if (current == null || !current.belongsTo(stagedVertexBuffer) || current.isEmpty()) {
            return;
        }
        GpuBuffer conversions = TerrainRendererDebugConfig.gpuModelMeshShadersEnabled() ? this.meshConversions : null;
        current.write(Objects.requireNonNull(this.inputBuffers), Objects.requireNonNull(this.vertexBuffers), Objects.requireNonNull(this.pipeline), store().address(), conversions);
        this.frameWritten = true;
        this.writtenFrames++;
        this.lastFrameParts = current.partCount();
        this.lastFrameCubes = current.jobCount();
        this.lastFrameVertices = current.vertexCount();
        this.lastFrameDraws = current.drawCount();
        this.lastFrameMeshDraws = current.meshDrawCount();
        this.lastFrameMeshCubes = current.meshJobCount();
    }
    
    /**
     * Counts a frame whose GPU model draws Minecraft ended without their vertices having been
     * written, and says once what the frame held: Minecraft skipped those draws, and the models went
     * missing.
     */
    private void leftOut(ModelVertexFrame current) {
        this.framesLeftOut++;
        if (!this.warnedLeftOut) {
            this.warnedLeftOut = true;
            LOGGER.warn("[Vulkan Improvement] GPU model draws were never written, so Minecraft skipped them. Frame: {}", current.describe());
        }
    }
    
    /**
     * What Minecraft draws a GPU draw with, or null to skip it when its vertices were never written.
     */
    public StagedVertexBuffer.@Nullable ExecuteInfo executeInfo(ModelVertexDraw draw) {
        ModelVertexFrame current = this.frame;
        return current == null || !this.frameWritten ? null : current.executeInfo(draw, this.meshMarker);
    }
    
    /**
     * Whether a vertex buffer is the marker Minecraft draws a mesh draw from.
     */
    public boolean isMeshMarker(GpuBuffer buffer) {
        return this.meshMarker != null && buffer == this.meshMarker;
    }
    
    /**
     * Whether GPU model draws can be drawn by mesh shaders on Minecraft's device.
     */
    public boolean meshShadersAvailable() {
        return this.meshShadersAvailable;
    }
    
    /**
     * Draws a mesh draw with the mesh pipeline of the pipeline Minecraft bound, where Minecraft
     * records its indexed draw. One the mesh pipeline cannot draw is left out, and counted.
     */
    public void drawMesh(VkCommandBuffer commandBuffer, VulkanRenderPipeline pipeline, int drawIndex) {
        ModelVertexFrame current = this.frame;
        if (current != null && current.drawMesh(commandBuffer, pipeline, drawIndex)) {
            this.meshDraws++;
        }
        else {
            this.meshDrawsLeftOut++;
        }
    }
    
    /**
     * Minecraft has drawn everything the staged vertex buffer held.
     */
    public void endDraw(Object stagedVertexBuffer) {
        ModelVertexFrame current = this.frame;
        if (current != null && current.belongsTo(stagedVertexBuffer)) {
            if (!this.frameWritten && !current.isEmpty()) {
                leftOut(current);
            }
            current.reset();
            this.frameWritten = false;
        }
    }
    
    /**
     * The frame is over for a staged vertex buffer. When it is the one GPU draws went into, the
     * buffers the frame used go back to their pools once the GPU is done with them. Minecraft ends
     * the frame of its GUI's staged vertex buffer too, which must not recycle them twice.
     */
    public void endFrame(Object stagedVertexBuffer) {
        if (stagedVertexBuffer != this.stagedBufferUsed) {
            return;
        }
        if (this.inputBuffers != null) {
            this.inputBuffers.endFrame();
        }
        if (this.vertexBuffers != null) {
            this.vertexBuffers.endFrame();
        }
    }
    
    ModelCubeStore store() {
        return Objects.requireNonNull(this.store, "GPU model vertices are not configured");
    }
    
    ModelVertexFrame frame() {
        return Objects.requireNonNull(this.frame, "GPU model vertices are not configured");
    }
    
    public synchronized Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", TerrainRendererDebugConfig.gpuModelVerticesEnabled());
        map.put("ready", this.pipeline != null);
        map.put("otherModMixins", this.otherModMixins == null ? List.of() : this.otherModMixins);
        map.put("gpuSubmits", this.gpuSubmits);
        map.put("cpuSubmits", this.cpuSubmits);
        map.put("writtenFrames", this.writtenFrames);
        map.put("lastFrameParts", this.lastFrameParts);
        map.put("lastFrameCubes", this.lastFrameCubes);
        map.put("lastFrameVertices", this.lastFrameVertices);
        map.put("lastFrameDraws", this.lastFrameDraws);
        map.put("meshShaders", TerrainRendererDebugConfig.gpuModelMeshShadersEnabled());
        map.put("meshShadersAvailable", this.meshShadersAvailable);
        map.put("lastFrameMeshDraws", this.lastFrameMeshDraws);
        map.put("lastFrameMeshCubes", this.lastFrameMeshCubes);
        map.put("meshDraws", this.meshDraws);
        map.put("meshDrawsLeftOut", this.meshDrawsLeftOut);
        map.put("framesLeftOut", this.framesLeftOut);
        map.put("meshPipelines", ModelMeshPipelines.asMap());
        if (this.store != null) {
            map.put("residentCubes", this.store.residentCubes());
            map.put("cpuOnlyCubes", this.store.cpuOnlyCubes());
            map.put("residentPolygonBytes", this.store.residentBytes());
            map.put("collectedPolygons", this.store.collectedPolygons());
        }
        map.put("storesRenewed", this.storesRenewed);
        return map;
    }
}