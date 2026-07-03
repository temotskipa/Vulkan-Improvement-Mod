package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class GpuResidentTerrainArchitectureInvariantCheck {
    private static final Path ARCHITECTURE_PLAN = Path.of("docs/exec-plans/active/gpu-resident-terrain-architecture.md");
    private static final Path EXTENSION_PLAN = Path.of("docs/exec-plans/active/26-2-vulkan-renderer-extension.md");
    private static final Path PROVIDER_PLAN = Path.of("docs/exec-plans/active/accelerated-worldgen-provider-integration.md");
    private static final Path ACTIVE_README = Path.of("docs/exec-plans/active/README.md");
    private static final Path FABRIC_MOD_JSON = Path.of("src/main/resources/fabric.mod.json");
    private static final Path GITIGNORE = Path.of(".gitignore");
    private static final Path DATABASE_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldDatabase.java");
    private static final Path RESIDENCY_BUDGET_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldResidencyBudget.java");
    private static final Path RESIDENCY_STATS_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldResidencyStats.java");
    private static final Path PIPELINE_STAGE_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldPipelineStage.java");
    private static final Path LAYER_DIRECTORY_SOURCE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/gpuworld/GpuWorldTerrainLayerDirectory.java");

    private GpuResidentTerrainArchitectureInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        String architecturePlan = read(ARCHITECTURE_PLAN);
        String extensionPlan = read(EXTENSION_PLAN);
        String providerPlan = read(PROVIDER_PLAN);
        String activeReadme = read(ACTIVE_README);
        String fabricModJson = read(FABRIC_MOD_JSON);
        String gitignore = read(GITIGNORE);
        String databaseSource = read(DATABASE_SOURCE);
        String residencyBudgetSource = read(RESIDENCY_BUDGET_SOURCE);
        String residencyStatsSource = read(RESIDENCY_STATS_SOURCE);
        String pipelineStageSource = read(PIPELINE_STAGE_SOURCE);
        String layerDirectorySource = read(LAYER_DIRECTORY_SOURCE);

        checkPlanWiring(architecturePlan, extensionPlan, providerPlan, activeReadme);
        checkLocalOnlyExternalReferenceBoundary(gitignore, fabricModJson);
        checkPublicDocsOnlyDescribeInferredArchitecture();
        checkGpuResidentRequirements(architecturePlan);
        checkRuntimeSafetyBoundaries(architecturePlan);
        checkCodeContracts(databaseSource, residencyBudgetSource, residencyStatsSource,
                pipelineStageSource, layerDirectorySource);
    }

    private static void checkPlanWiring(String architecturePlan, String extensionPlan,
                                        String providerPlan, String activeReadme) {
        requireContains(activeReadme, "[GPU Resident Terrain Architecture](gpu-resident-terrain-architecture.md)",
                "active README must list the GPU-resident terrain architecture plan");
        requireContains(activeReadme, "[Accelerated Worldgen Provider Integration](accelerated-worldgen-provider-integration.md)",
                "active README must list the optional accelerated-worldgen provider integration plan");
        requireContains(extensionPlan, "[GPU-resident terrain architecture](gpu-resident-terrain-architecture.md)",
                "26.2 renderer extension plan must link the GPU-resident terrain architecture plan");
        requireContains(extensionPlan, "[accelerated-worldgen provider integration](accelerated-worldgen-provider-integration.md)",
                "26.2 renderer extension plan must link the dedicated optional-provider integration plan");
        requireContains(architecturePlan, "# GPU Resident Terrain Architecture",
                "architecture plan must use the expected title");
        requireContains(architecturePlan, "## Exit Criteria",
                "architecture plan must define exit criteria");
        requireContains(providerPlan, "# Accelerated Worldgen Provider Integration",
                "provider integration plan must use the expected title");
        requireContains(providerPlan, "diagnostics-only detection",
                "provider integration plan must keep initial integration diagnostic-only");
        requireContains(providerPlan, "must not add `suggests` or `recommends`",
                "provider integration plan must not change Fabric metadata before explicit approval");
        requireContains(providerPlan, "CPU-authoritative Minecraft chunk state remains the renderer default",
                "provider integration plan must preserve renderer authority boundaries");
    }

    private static void checkLocalOnlyExternalReferenceBoundary(String gitignore, String fabricModJson) {
        requireContains(gitignore, "docs/local/",
                "external mod source references must stay in ignored local notes");
        require(!fabricModJson.contains("\"suggests\"") && !fabricModJson.contains("\"recommends\""),
                "fabric metadata must not add optional external worldgen provider relationships before a dedicated integration plan");
    }

    private static void checkPublicDocsOnlyDescribeInferredArchitecture() throws IOException {
        List<String> forbiddenTokens = List.of(
                "github.com/",
                "modrinth.com",
                "curseforge.com",
                "Nvidium",
                "nvidium",
                "C2ME",
                "c2me",
                "Voxy",
                "VulkanMod",
                "Sodium",
                "Iris",
                "Radiance",
                "MCRcortex",
                "RelativityMC",
                "xCollateral",
                "CaffeineMC",
                "IrisShaders",
                "Minecraft-Radiance",
                "minecraft-radiance",
                "GitHub-only source of truth",
                "public GitHub repository",
                "source tree for architecture review"
        );
        try (Stream<Path> paths = Files.walk(Path.of("docs"))) {
            List<Path> markdownFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                    .filter(path -> !path.startsWith(Path.of("docs/local")))
                    .toList();
            for (Path markdownFile : markdownFiles) {
                String text = Files.readString(markdownFile);
                for (String token : forbiddenTokens) {
                    require(!text.contains(token), "public docs must describe inferred architecture without external mod references: "
                            + markdownFile + " contains `" + token + "`");
                }
            }
        }
    }

    private static void checkGpuResidentRequirements(String architecturePlan) {
        requireContains(architecturePlan, "GpuWorldResidencyBudget",
                "architecture plan must name the residency-budget contract");
        requireContains(architecturePlan, "GpuWorldResidencyStats",
                "architecture plan must name the residency-stats contract");
        requireContains(architecturePlan, "GpuWorldPipelineStage",
                "architecture plan must name the pipeline-stage contract");
        requireContains(architecturePlan, "GpuWorldTerrainLayerDirectory",
                "architecture plan must name the resident terrain layer directory contract");
        requireContains(architecturePlan, "persistent section pages",
                "architecture plan must require persistent section pages");
        requireContains(architecturePlan, "dirty-event updates",
                "architecture plan must move updates to dirty-event ingestion");
        requireContains(architecturePlan, "visibility compaction",
                "architecture plan must require visibility compaction");
        requireContains(architecturePlan, "indirect command generation",
                "architecture plan must require indirect command generation");
        requireContains(architecturePlan, "explicit residency budget",
                "architecture plan must require explicit memory budgeting");
        requireContains(architecturePlan, "not one CPU work-queue upload per vanilla terrain draw",
                "architecture plan must reject the current per-draw upload shape");
        requireContains(architecturePlan, "External source links and named reference projects stay in ignored local notes under `docs/local/`",
                "architecture plan must route named external references to ignored local notes");
    }

    private static void checkRuntimeSafetyBoundaries(String architecturePlan) {
        requireContains(architecturePlan, "`vim.strictMeshTerrainReplacement=false`",
                "architecture plan must preserve strict terrain replacement as default-off");
        requireContains(architecturePlan, "strict cancellation remains validation-only",
                "architecture plan must keep strict cancellation validation-gated");
        requireContains(architecturePlan, "vanilla terrain remains visible",
                "architecture plan must preserve vanilla fallback while the GPU path is incomplete");
        requireContains(architecturePlan, "translucent terrain, underwater overlays, and environment screen effects stay vanilla",
                "architecture plan must keep uncontracted translucent/effect rendering on vanilla");
    }

    private static void checkCodeContracts(String databaseSource, String residencyBudgetSource,
                                           String residencyStatsSource,
                                           String pipelineStageSource, String layerDirectorySource) {
        requireContains(residencyBudgetSource, "public record GpuWorldResidencyBudget",
                "GPU world residency budget contract must exist");
        requireContains(residencyBudgetSource, "conservativeDefault()",
                "residency budget must expose a conservative default");
        requireContains(residencyBudgetSource, "fitsWithinMaxResidentBytes",
                "residency budget must validate the explicit budget cap");
        requireContains(residencyBudgetSource, "asMap()",
                "residency budget must expose diagnostics");
        requireContains(residencyStatsSource, "public final class GpuWorldResidencyStats",
                "GPU world residency usage stats contract must exist");
        requireContains(residencyStatsSource, "tryReservePage",
                "residency stats must refuse over-budget page reservations");
        requireContains(residencyStatsSource, "recordEviction",
                "residency stats must expose eviction accounting");
        requireContains(residencyStatsSource, "overBudgetRefusals",
                "residency stats must expose over-budget refusal diagnostics");
        requireContains(residencyStatsSource, "livePageCountsByKind",
                "residency stats must expose live page counts by page kind");
        requireContains(residencyStatsSource, "usedBytesByKind",
                "residency stats must expose used bytes by page kind");
        requireContains(pipelineStageSource, "public enum GpuWorldPipelineStage",
                "GPU world pipeline stage contract must exist");
        requireContains(pipelineStageSource, "PERSISTENT_RESIDENCY",
                "pipeline contract must include persistent residency");
        requireContains(pipelineStageSource, "VISIBILITY_COMPACTION",
                "pipeline contract must include visibility compaction");
        requireContains(pipelineStageSource, "INDIRECT_COMMAND_GENERATION",
                "pipeline contract must include indirect command generation");
        requireContains(layerDirectorySource, "public final class GpuWorldTerrainLayerDirectory",
                "resident terrain layer directory contract must exist");
        requireContains(layerDirectorySource, "section-node-layer-ordinal",
                "resident terrain layer directory must be keyed by section and layer, not vanilla draw objects");
        requireContains(layerDirectorySource, "vanillaDrawKeyed\", false",
                "resident terrain layer directory diagnostics must reject vanilla draw keying");
        requireContains(databaseSource, "GpuWorldResidencyBudget.conservativeDefault()",
                "GPU world diagnostics must expose the residency budget");
        requireContains(databaseSource, "residencyStats",
                "GPU world diagnostics must expose residency usage stats");
        requireContains(databaseSource, "GpuWorldPipelineStage.asDiagnostics()",
                "GPU world diagnostics must expose the pipeline stages");
        requireContains(databaseSource, "terrainLayerDirectory",
                "GPU world diagnostics must expose the resident terrain layer directory");
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
