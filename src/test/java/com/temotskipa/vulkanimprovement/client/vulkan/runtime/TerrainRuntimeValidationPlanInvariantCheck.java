package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerrainRuntimeValidationPlanInvariantCheck {
    private static final Path MESH_BUGFIX_PLAN = Path.of("docs/exec-plans/active/mesh-replacement-device-loss-bugfix.md");
    private static final Path RENDERER_EXTENSION_PLAN = Path.of("docs/exec-plans/active/26-2-vulkan-renderer-extension.md");
    private static final Path RELIABILITY = Path.of("docs/RELIABILITY.md");

    private TerrainRuntimeValidationPlanInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        String meshPlan = read(MESH_BUGFIX_PLAN);
        String extensionPlan = read(RENDERER_EXTENSION_PLAN);
        String reliability = read(RELIABILITY);

        checkMeshBugfixPlanEvidence(meshPlan);
        checkRecordFirstRuntimeProcedure(meshPlan);
        checkSafeDefaults(meshPlan, extensionPlan, reliability);
        checkReliabilityEvidence(reliability);
    }

    private static void checkMeshBugfixPlanEvidence(String plan) {
        requireContains(plan, "crash-2026-06-29_08.25.58-client.txt",
                "mesh bugfix plan must retain the device-loss crash report evidence");
        requireContains(plan, "req_17",
                "mesh bugfix plan must retain the pre-fix visual corruption recording");
        requireContains(plan, "req_34",
                "mesh bugfix plan must retain the post-fix jitter recording");
        requireContains(plan, "req_43",
                "mesh bugfix plan must retain the texture-quality caveat recording");
        requireContains(plan, "textureFiltering:2",
                "mesh bugfix plan must retain the anisotropic filtering clue for req_43");
        requireContains(plan, "mipmapLevels:4",
                "mesh bugfix plan must retain the mipmap clue for req_43");
    }

    private static void checkRecordFirstRuntimeProcedure(String plan) {
        int scheduleJitter = plan.indexOf("large camera jitter scheduled");
        int recordGrid = plan.indexOf("mc_record_video(frames=300, interval=100, output=\"grid\", downscale=1, quality=1.0)");
        requireContains(plan, "Runtime validation must call `mc_record_video` first",
                "mesh bugfix plan must preserve the user-required record-first validation rule");
        require(scheduleJitter >= 0, "mesh bugfix plan must include a concrete delayed jitter command");
        require(recordGrid >= 0, "mesh bugfix plan must include a long record-first grid capture command");
        require(scheduleJitter < recordGrid,
                "mesh bugfix plan must schedule delayed jitter before starting the recording call");
        requireContains(plan, "mc_record_video(frames=60, interval=100, output=\"frames\", downscale=1, quality=1.0)",
                "mesh bugfix plan must require full-resolution frames for texture inspection");
        requireContains(plan, "RendererDiagnostics.bugReport()",
                "mesh bugfix plan must require bug-report diagnostics alongside captures");
        requireContains(plan, "terrainRenderer.descriptorHeap.textureBindings.Sampler0.maxAnisotropy",
                "mesh bugfix plan must require sampler anisotropy diagnostics for texture-quality debugging");
    }

    private static void checkSafeDefaults(String meshPlan, String extensionPlan, String reliability) {
        requireContains(meshPlan, "`vim.replaceVanillaTerrain` remains `false` by default",
                "mesh bugfix plan must keep visible replacement off by default");
        requireContains(meshPlan, "Do not change the default value of `vim.replaceVanillaTerrain`",
                "mesh bugfix plan must prohibit default promotion before runtime evidence");
        requireContains(extensionPlan, "runtime mode is Vulkan capture/bootstrap until mesh replacement is stable",
                "main renderer extension plan must keep capture/bootstrap as the default runtime mode");
        requireContains(reliability, "| `vim.replaceVanillaTerrain`            | `false`",
                "reliability docs must record vim.replaceVanillaTerrain=false as the default");
    }

    private static void checkReliabilityEvidence(String reliability) {
        requireContains(reliability, "crash-2026-06-29_08.25.58-client.txt",
                "reliability matrix must retain the mesh replacement crash evidence");
        requireContains(reliability, "run/debugbridge-recordings/req_17/recording.jpg",
                "reliability matrix must retain the pre-fix visual corruption recording");
        requireContains(reliability, "run/debugbridge-recordings/req_34/recording.jpg",
                "reliability matrix must retain the post-fix jitter recording");
        requireContains(reliability, "run/debugbridge-recordings/req_43/recording.jpg",
                "reliability matrix must retain the texture-quality caveat recording");
        requireContains(reliability, "Required texture-quality diagnostics",
                "reliability docs must list the texture-quality diagnostics required by the bugfix plan");
    }

    private static String read(Path path) throws IOException {
        require(Files.isRegularFile(path), "missing required plan or reliability file: " + path);
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
