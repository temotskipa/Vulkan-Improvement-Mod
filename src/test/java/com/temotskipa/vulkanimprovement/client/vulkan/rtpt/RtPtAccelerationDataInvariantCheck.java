package com.temotskipa.vulkanimprovement.client.vulkan.rtpt;

import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanRuntimeProfile;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldPageKind;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldRevision;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldSectionId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class RtPtAccelerationDataInvariantCheck {
    private static final Path RENDERER_DIAGNOSTICS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/RendererDiagnostics.java");

    private RtPtAccelerationDataInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        checkRuntimeProfileKeepsRtPtOptional();
        checkAccelerationPageDiagnostics();
        checkRegistryLifecycleDiagnostics();
        checkRendererDiagnosticsIncludesAccelerationData();
    }

    private static void checkRuntimeProfileKeepsRtPtOptional() {
        Map<String, Object> profile = VulkanRuntimeProfile.from(VulkanImprovementCapabilities.Snapshot.empty()).asMap();
        Map<?, ?> hardRequirements = child(profile, "hardRequirements");
        Map<?, ?> rtReadiness = child(profile, "rtReadiness");
        Map<?, ?> selectedPaths = child(profile, "selectedPaths");
        Map<?, ?> disabledReasons = child(profile, "disabledReasons");

        require(!hardRequirements.containsKey("accelerationStructureExtension"), "RT/PT acceleration structures must not be a startup hard requirement");
        require(!hardRequirements.containsKey("rayTracingPipelineExtension"), "RT/PT pipelines must not be a startup hard requirement");
        require(Boolean.FALSE.equals(rtReadiness.get("accelerationStructureExtension")), "empty profile must report missing RT acceleration structures as readiness state");
        require(Boolean.FALSE.equals(rtReadiness.get("rayTracingPipelineExtension")), "empty profile must report missing RT pipelines as readiness state");
        require("not-ready".equals(selectedPaths.get("rtPtReadiness")), "missing RT/PT support must select not-ready state");
        require("requires all RT/PT readiness extensions".equals(disabledReasons.get("rtPtReadiness")), "missing RT/PT support must explain disabled readiness without backend failure");
    }

    private static void checkAccelerationPageDiagnostics() {
        RtPtAccelerationPage page = samplePage();
        Map<String, Object> map = page.asMap();

        require("terrain".equals(map.get("domain")), "page diagnostics must expose terrain domain");
        require(map.get("sectionId") instanceof Map<?, ?>, "page diagnostics must expose section id");
        require(Long.valueOf(7L).equals(map.get("sourceRevision")), "page diagnostics must expose source revision value");
        require("canonical-mirror".equals(map.get("sourcePageKind")), "page diagnostics must expose source page kind");
        require(Boolean.FALSE.equals(map.get("sourceGameplayAuthoritative")), "acceleration pages must not claim gameplay authority");
        require(Long.valueOf(11L).equals(map.get("blasHandle")), "page diagnostics must expose opaque BLAS handle");
        require(Long.valueOf(13L).equals(map.get("tlasInstanceHandle")), "page diagnostics must expose opaque TLAS instance handle");
        require("test fallback".equals(map.get("fallbackReason")), "page diagnostics must expose fallback reason");
    }

    private static void checkRegistryLifecycleDiagnostics() {
        RtPtAccelerationDataRegistry registry = RtPtAccelerationDataRegistry.get();
        registry.reset();

        Map<String, Object> empty = registry.asMap();
        require(Long.valueOf(0L).equals(empty.get("registeredPageCount")), "registry must reset registered count");
        require(Long.valueOf(0L).equals(empty.get("livePageCount")), "registry must reset live count");
        require(Boolean.FALSE.equals(empty.get("allocationEnabled")), "registry must not enable allocation before an allocation plan exists");

        RtPtAccelerationPage page = samplePage();
        registry.registerPage(page);
        registry.markPendingRebuild(page, "source revision changed");
        registry.retirePage(page, "source page retired");
        registry.clearForDeviceLost("device lost");

        Map<String, Object> map = registry.asMap();
        require(Long.valueOf(1L).equals(map.get("registeredPageCount")), "registry must count registered pages");
        require(Long.valueOf(1L).equals(map.get("retiredPageCount")), "registry must count retired pages");
        require(Long.valueOf(1L).equals(map.get("pendingRebuildCount")), "registry must count pending rebuilds");
        require(Long.valueOf(1L).equals(map.get("deviceLostClearCount")), "registry must count device-lost clears");
        require(Long.valueOf(0L).equals(map.get("livePageCount")), "device-lost clear must leave no live pages");

        Map<?, ?> fallbackReasons = child(map, "fallbackReasonCounts");
        require(Long.valueOf(1L).equals(fallbackReasons.get("source revision changed")), "registry must count rebuild fallback reasons");
        require(Long.valueOf(1L).equals(fallbackReasons.get("source page retired")), "registry must count retirement fallback reasons");
        require(Long.valueOf(1L).equals(fallbackReasons.get("device lost")), "registry must count device-lost fallback reasons");

        registry.reset();
    }

    private static void checkRendererDiagnosticsIncludesAccelerationData() throws IOException {
        String source = Files.readString(RENDERER_DIAGNOSTICS);
        require(source.contains("\"rtPtAccelerationData\""), "bug reports must include RT/PT acceleration-data diagnostics");
        require(source.contains("RtPtAccelerationDataRegistry.get().asMap()"), "bug reports must read RT/PT acceleration diagnostics from the registry");
    }

    private static RtPtAccelerationPage samplePage() {
        return new RtPtAccelerationPage(
                RtPtAccelerationDomain.TERRAIN,
                GpuWorldSectionId.fromBlockPosition(16, 80, 32),
                GpuWorldRevision.of(7L),
                GpuWorldPageKind.CANONICAL_MIRROR,
                11L,
                13L,
                "test fallback"
        );
    }

    private static Map<?, ?> child(Map<String, Object> map, String key) {
        Object value = map.get(key);
        require(value instanceof Map<?, ?>, "child diagnostics must be a map: " + key);
        return (Map<?, ?>) value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
