package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AcceleratedWorldgenProviderCompatibilityInvariantCheck {
	private static final Path RENDERER_DIAGNOSTICS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/RendererDiagnostics.java");
	private static final Path PROVIDER_COMPATIBILITY = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/AcceleratedWorldgenProviderCompatibility.java");
	private static final Path TERRAIN_CONFIG = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/TerrainRendererDebugConfig.java");
	private static final Path VIDEO_OPTIONS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementVideoOptions.java");
	private static final Path FABRIC_MOD_JSON = Path.of("src/main/resources/fabric.mod.json");
	private static final Path LANG = Path.of("src/client/resources/assets/vulkanimprovement/lang/en_us.json");
	
	private AcceleratedWorldgenProviderCompatibilityInvariantCheck() {
	}
	
	
	static void main() throws IOException {
		checkAbsentProviderDiagnostics();
		checkPresentProviderStillDoesNotOwnRendererAuthority();
		checkSourceWiring(read(RENDERER_DIAGNOSTICS), read(PROVIDER_COMPATIBILITY), read(TERRAIN_CONFIG), read(VIDEO_OPTIONS), read(FABRIC_MOD_JSON), read(LANG));
	}
	
	private static void checkAbsentProviderDiagnostics() {
		TerrainRendererDebugConfig.setAcceleratedWorldgenProviderIntegrationEnabled(false);
		AcceleratedWorldgenProviderState diagnostics = AcceleratedWorldgenProviderCompatibility.forDiagnostics(false, false, 0);
		
		require(!diagnostics.present(), "absent provider diagnostics must report present=false");
		require(!diagnostics.integrationEnabled(), "provider integration must be disabled by default");
		require("cpu-chunk-state".equals(diagnostics.rendererAuthority()), "renderer authority must remain CPU chunk state");
		require("minecraft".equals(diagnostics.worldgenAuthority()), "worldgen authority must remain Minecraft when provider is absent");
		require("none".equals(diagnostics.metadataRelationship()), "Fabric metadata relationship must remain none");
		require("no-provider-id-configured".equals(diagnostics.fallbackReason()), "absent provider diagnostics must explain why the integration is inactive");
	}
	
	private static void checkPresentProviderStillDoesNotOwnRendererAuthority() {
		AcceleratedWorldgenProviderState diagnostics = AcceleratedWorldgenProviderCompatibility.forDiagnostics(true, true, 1);
		
		require(diagnostics.present(), "present provider diagnostics must report present=true");
		require(diagnostics.integrationEnabled(), "explicit opt-in must be visible in provider diagnostics");
		require("cpu-chunk-state".equals(diagnostics.rendererAuthority()), "renderer authority must remain CPU chunk state after provider opt-in");
		require("minecraft".equals(diagnostics.worldgenAuthority()), "diagnostics-only provider opt-in must not claim worldgen authority");
		require("provider-owned-worldgen-not-implemented".equals(diagnostics.fallbackReason()), "provider diagnostics must explain that behavior is not implemented yet");
	}
	
	private static void checkSourceWiring(String diagnosticsSource, String providerSource, String configSource, String videoOptionsSource, String fabricModJson, String langSource) {
		requireContains(diagnosticsSource, "\"worldgenProvider: \" + AcceleratedWorldgenProviderCompatibility.snapshot()", "bug reports must expose accelerated-worldgen provider diagnostics");
		requireContains(providerSource, "FabricLoader.getInstance().getModContainer(providerId)", "provider detection must use Fabric loader mod-container presence");
		require(!providerSource.contains("Class.forName("), "provider detection must not probe provider implementation classes");
		requireContains(configSource, "acceleratedWorldgenProviderIntegrationEnabled = booleanProperty(\"vim.enableAcceleratedWorldgenProviderIntegration\", false)", "provider integration flag must default off");
		requireContains(configSource, "public static boolean acceleratedWorldgenProviderIntegrationEnabled()", "terrain debug config must expose the provider integration flag");
		requireContains(configSource, "public static void setAcceleratedWorldgenProviderIntegrationEnabled", "terrain debug config must expose a setter for live debugging and Video Settings");
		requireContains(configSource, "enableAcceleratedWorldgenProviderIntegration=\" + acceleratedWorldgenProviderIntegrationEnabled()", "debug config diagnostics must report the provider integration flag");
		requireContains(videoOptionsSource, "acceleratedWorldgenProviderIntegration", "Video Settings must expose the provider integration flag");
		requireContains(videoOptionsSource, "TerrainRendererDebugConfig.acceleratedWorldgenProviderIntegrationEnabled()", "Video Settings must sync from the provider integration config flag");
		requireContains(videoOptionsSource, "TerrainRendererDebugConfig::setAcceleratedWorldgenProviderIntegrationEnabled", "Video Settings must update the provider integration config flag");
		requireContains(langSource, "\"options.vulkanimprovement.acceleratedWorldgenProviderIntegration\"", "lang file must name the provider integration option");
		requireContains(langSource, "\"options.vulkanimprovement.acceleratedWorldgenProviderIntegration.tooltip\"", "lang file must describe the provider integration option");
		require(!fabricModJson.contains("\"suggests\"") && !fabricModJson.contains("\"recommends\""), "initial provider integration must not add Fabric soft dependencies");
	}
	
	private static String read(Path path) throws IOException {
		require(Files.isRegularFile(path), "missing provider integration artifact: " + path);
		return Files.readString(path);
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
	
	private static void requireContains(String source, String token, String message) {
		require(source.contains(token), message + " (missing `" + token + "`)");
	}
}