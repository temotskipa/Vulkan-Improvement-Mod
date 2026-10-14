package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class GpuResidentTerrainArchitectureInvariantCheck {
    private static final Path FABRIC_MOD_JSON = Path.of("src/main/resources/fabric.mod.json");
    private static final Path GITIGNORE = Path.of(".gitignore");
    private static final Path DATABASE_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldDatabase.java");
    private static final Path RESIDENCY_BUDGET_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldResidencyBudget.java");
    private static final Path RESIDENCY_STATS_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldResidencyStats.java");
    private static final Path VISIBILITY_COMMAND_STATS_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldVisibilityCommandStats.java");
    private static final Path PIPELINE_STAGE_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldPipelineStage.java");
    private static final Path LAYER_DIRECTORY_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldTerrainLayerDirectory.java");
    
    private GpuResidentTerrainArchitectureInvariantCheck() {
    }
    
    
    static void main(String[] args) throws IOException {
        String fabricModJson = read(FABRIC_MOD_JSON);
        String gitignore = read(GITIGNORE);
        String databaseSource = read(DATABASE_SOURCE);
        String residencyBudgetSource = read(RESIDENCY_BUDGET_SOURCE);
        String residencyStatsSource = read(RESIDENCY_STATS_SOURCE);
        String visibilityCommandStatsSource = read(VISIBILITY_COMMAND_STATS_SOURCE);
        String pipelineStageSource = read(PIPELINE_STAGE_SOURCE);
        String layerDirectorySource = read(LAYER_DIRECTORY_SOURCE);
        
        checkResidentPagesAndVisibilityPass();
        checkLocalOnlyExternalReferenceBoundary(gitignore, fabricModJson);
        checkPublicDocsOnlyDescribeInferredArchitecture();
        checkCodeContracts(databaseSource, residencyBudgetSource, residencyStatsSource, visibilityCommandStatsSource, pipelineStageSource, layerDirectorySource);
    }
    
    /**
     * Holds the resident terrain path to its design: pages that are
     * copied only when they change, into device memory, in command order; a frame that lists
     * pages and leaves culling and the command to the GPU; and strict replacement still opt-in.
     */
    private static void checkResidentPagesAndVisibilityPass() throws IOException {
        String terrain = "src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/";
        String resources = read(Path.of(terrain + "DescriptorHeapTerrainResources.java"));
        String renderer = read(Path.of(terrain + "MeshTerrainRenderer.java"));
        String store = read(Path.of(terrain + "SectionMeshletStore.java"));
        String config = read(Path.of(terrain + "TerrainRendererDebugConfig.java"));
        String visibilityPass = read(Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain/mesh_task_command.comp.glsl"));

        requireContains(store, "PAGES.markPending(new SectionLayerKey(mesh, sectionNode, layer.ordinal()));", "a captured section layer must wait for a page of its own");
        require(!store.contains("writeMetadataSnapshot"), "terrain must not be rewritten as a whole when one section changes");
        requireContains(resources, "SectionMeshletStore.collectPageUploads(PAGE_UPLOAD_BYTES_PER_FRAME, PAGE_UPLOADS_PER_FRAME)", "a frame must upload changed pages only, within a budget");
        requireContains(resources, "VK10.vkCmdCopyBuffer(commandBuffer, staging.vkBuffer(), target.vkBuffer(), regions);", "pages must reach the GPU through copies in the command stream");
        requireContains(resources, "\"VIM Terrain Meshlet Vertex Payload\", layout.vertexPayloadBytes, storageUsage, TerrainGpuBuffer.Placement.DEVICE)", "the terrain payload must live in device memory");
        // The GPU reads these or counts them up every frame; from host memory that crosses the bus.
        requireContains(resources, "\"VIM Terrain Visible Meshlet List\", layout.visibleMeshletBytes, storageUsage, TerrainGpuBuffer.Placement.DEVICE_MAPPED)", "the visible page lists must live in device memory");
        requireContains(resources, "\"VIM Terrain Mesh Task Commands\", layout.meshTaskCommandBytes, indirectStorageUsage, TerrainGpuBuffer.Placement.DEVICE_MAPPED)", "the mesh-task commands must live in device memory");
        requireContains(resources, "\"VIM Terrain Debug Counters\", TerrainGpuLayout.DEBUG_COUNTER_BYTES, storageUsage, TerrainGpuBuffer.Placement.DEVICE_MAPPED)", "the counters the GPU counts up must live in device memory");
        requireContains(resources, "growTerrainResourcesIfNeeded(SectionMeshletStore.takePageShortage());", "a page that does not fit must grow the buffer that was short");
        requireContains(renderer, "SectionMeshletStore.visiblePages(layerOrdinal)", "a frame must list a layer's visible pages, not its meshlets");
        requireContains(renderer, "MeshShaderTerrainProgram.get().prepareVisiblePageCommand(preparedDispatch, upload)", "a layer's mesh-task command must be written by the GPU visibility pass");
        requireContains(renderer, "GpuWorldDatabase.get().recordVisibilityCommandPass(", "the visibility pass must be reported in the GPU world diagnostics");
        requireContains(visibilityPass, "if (outsideFrustum(ivec3(page.sectionX, page.sectionY, page.sectionZ))) {", "the visibility pass must cull pages against the view");
        requireContains(visibilityPass, "uint drawn = atomicAdd(commands.commands[pc.commandIndex].groupCountX, 1u);", "the visibility pass must count up the mesh-task command itself");
        requireContains(config, "booleanProperty(\"vim.enableGpuGeneratedMeshTaskCommands\", true)", "mesh replacement must use the GPU visibility pass unless it is switched off");
        requireContains(config, "booleanProperty(\"vim.strictMeshTerrainReplacement\", false)", "strict replacement must stay opt-in");
    }

    private static void checkLocalOnlyExternalReferenceBoundary(String gitignore, String fabricModJson) {
        requireContains(gitignore, "docs/local/", "external mod source references must stay in ignored local notes");
        require(!fabricModJson.contains("\"suggests\"") && !fabricModJson.contains("\"recommends\""), "fabric metadata must not add optional external worldgen provider relationships before a dedicated integration plan");
    }
    
    private static void checkPublicDocsOnlyDescribeInferredArchitecture() throws IOException {
        List<String> forbiddenTokens = List.of("github.com/", "modrinth.com", "curseforge.com", "Nvidium", "nvidium", "C2ME", "c2me", "Voxy", "VulkanMod", "Sodium", "Iris", "Radiance", "MCRcortex", "RelativityMC", "xCollateral", "CaffeineMC", "IrisShaders", "Minecraft-Radiance", "minecraft-radiance", "GitHub-only source of truth", "public GitHub repository", "source tree for architecture review");
        try (Stream<Path> paths = Files.walk(Path.of("docs"))) {
            List<Path> markdownFiles = paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md")).filter(path -> !path.startsWith(Path.of("docs/local"))).toList();
            for (Path markdownFile : markdownFiles) {
                String text = Files.readString(markdownFile);
                for (String token : forbiddenTokens) {
                    require(!text.contains(token), "public docs must describe inferred architecture without external mod references: " + markdownFile + " contains `" + token + "`");
                }
            }
        }
    }
    
    private static void checkCodeContracts(String databaseSource, String residencyBudgetSource, String residencyStatsSource, String visibilityCommandStatsSource, String pipelineStageSource, String layerDirectorySource) {
        requireContains(residencyBudgetSource, "public record GpuWorldResidencyBudget", "GPU world residency budget contract must exist");
        requireContains(residencyBudgetSource, "conservativeDefault()", "residency budget must expose a conservative default");
        requireContains(residencyBudgetSource, "fitsWithinMaxResidentBytes", "residency budget must validate the explicit budget cap");
        requireContains(residencyBudgetSource, "asMap()", "residency budget must expose diagnostics");
        requireContains(residencyStatsSource, "public final class GpuWorldResidencyStats", "GPU world residency usage stats contract must exist");
        requireContains(residencyStatsSource, "tryReservePage", "residency stats must refuse over-budget page reservations");
        requireContains(residencyStatsSource, "recordEviction", "residency stats must expose eviction accounting");
        requireContains(residencyStatsSource, "overBudgetRefusals", "residency stats must expose over-budget refusal diagnostics");
        requireContains(residencyStatsSource, "livePageCountsByKind", "residency stats must expose live page counts by page kind");
        requireContains(residencyStatsSource, "usedBytesByKind", "residency stats must expose used bytes by page kind");
        requireContains(visibilityCommandStatsSource, "public final class GpuWorldVisibilityCommandStats", "visibility command stats contract must exist");
        requireContains(visibilityCommandStatsSource, "recordPass", "visibility command stats must record compaction passes");
        requireContains(visibilityCommandStatsSource, "inputSectionCount", "visibility command stats must expose input sections");
        requireContains(visibilityCommandStatsSource, "culledSectionCount", "visibility command stats must expose culled sections");
        requireContains(visibilityCommandStatsSource, "visibleSectionCount", "visibility command stats must expose visible sections");
        requireContains(visibilityCommandStatsSource, "emittedCommandCount", "visibility command stats must expose emitted commands");
        requireContains(visibilityCommandStatsSource, "overflowedCommandCount", "visibility command stats must expose overflowed commands");
        requireContains(visibilityCommandStatsSource, "fallbackCommandCount", "visibility command stats must expose fallback commands");
        requireContains(visibilityCommandStatsSource, "\"affectsVanillaCancellation\", false", "visibility command stats must remain diagnostic until strict replacement is proven");
        requireContains(pipelineStageSource, "public enum GpuWorldPipelineStage", "GPU world pipeline stage contract must exist");
        requireContains(pipelineStageSource, "PERSISTENT_RESIDENCY", "pipeline contract must include persistent residency");
        requireContains(pipelineStageSource, "VISIBILITY_COMPACTION", "pipeline contract must include visibility compaction");
        requireContains(pipelineStageSource, "INDIRECT_COMMAND_GENERATION", "pipeline contract must include indirect command generation");
        requireContains(layerDirectorySource, "public final class GpuWorldTerrainLayerDirectory", "resident terrain layer directory contract must exist");
        requireContains(layerDirectorySource, "section-node-layer-ordinal", "resident terrain layer directory must be keyed by section and layer, not vanilla draw objects");
        requireContains(layerDirectorySource, "vanillaDrawKeyed\", false", "resident terrain layer directory diagnostics must reject vanilla draw keying");
        requireContains(databaseSource, "GpuWorldResidencyBudget.conservativeDefault()", "GPU world diagnostics must expose the residency budget");
        requireContains(databaseSource, "residencyStats", "GPU world diagnostics must expose residency usage stats");
        requireContains(databaseSource, "visibilityCommandStats", "GPU world diagnostics must expose visibility command stats");
        requireContains(databaseSource, "GpuWorldPipelineStage.asDiagnostics()", "GPU world diagnostics must expose the pipeline stages");
        requireContains(databaseSource, "terrainLayerDirectory", "GPU world diagnostics must expose the resident terrain layer directory");
    }
    
    private static String read(Path path) throws IOException {
        require(Files.isRegularFile(path), "missing required architecture artifact: " + path);
        return Files.readString(path);
    }
    
    private static void requireContains(String source, String token, String message) {
        require(source.contains(token), message + " (missing `" + token + "`)");
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}