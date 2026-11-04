package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VulkanVideoOptionsInvariantCheck {
	private static final Path RUNTIME = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementRuntime.java");
	private static final Path VIDEO_OPTIONS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementVideoOptions.java");
	private static final Path VIDEO_SETTINGS_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/VideoSettingsScreenMixin.java");
	private static final Path TERRAIN_CONFIG = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/TerrainRendererDebugConfig.java");
	private static final Path MESH_TERRAIN_RENDERER = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshTerrainRenderer.java");
	private static final Path CHUNK_SECTIONS_TO_RENDER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderMixin.java");
	private static final Path RENDER_SECTION_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/SectionRenderDispatcherRenderSectionMixin.java");
	private static final Path DRAW_INDIRECT_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderDrawIndirectMixin.java");
	private static final Path DRAW_SEPARATE_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderDrawSeparateMixin.java");
	private static final Path LEVEL_RENDERER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/LevelRendererMixin.java");
	private static final Path LANG = Path.of("src/client/resources/assets/vulkanimprovement/lang/en_us.json");
	private static final Path BACKEND_NOTICE = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanBackendNotice.java");
	private static final Pattern NOTICE_KEY = Pattern.compile("\"(vulkanimprovement\\.notice\\.[\\w.]+)\"");
	private static final Pattern OPTION_FIELD = Pattern.compile("private static @Nullable OptionInstance<\\w+> (\\w+);");
	private static final Set<String> INDEPENDENT_OPTIONS = Set.of("dumpCapabilities", "requireVulkanBackend", "validationDescriptorBufferOnly");
	
	private VulkanVideoOptionsInvariantCheck() {
	}
	
	
	static void main() throws IOException {
		checkRuntimeKeepsOptionsVisibleForActiveVulkan(read(RUNTIME));
		checkVideoSettingsUsesRuntimeVisibilityGate(read(VIDEO_OPTIONS), read(VIDEO_SETTINGS_MIXIN));
		checkEveryDependentOptionIsGreyedOut(read(VIDEO_OPTIONS));
		checkOptionRules();
		checkTerrainCaptureBootstrapIsExplicitOptIn(read(TERRAIN_CONFIG), read(VIDEO_OPTIONS), read(LANG));
		checkStrictTerrainCancellationIsExplicitOptIn(read(TERRAIN_CONFIG), read(LANG));
		checkTranslucentTerrainFlagIsMetadataOnly(read(TERRAIN_CONFIG), read(LANG));
		checkOpenGlActiveSessionsDoNotEnterTerrainServices(read(CHUNK_SECTIONS_TO_RENDER_MIXIN), read(RENDER_SECTION_MIXIN), read(LEVEL_RENDERER_MIXIN));
		checkTerrainMirrorHotPathsRequireCaptureOptIn(read(MESH_TERRAIN_RENDERER), read(CHUNK_SECTIONS_TO_RENDER_MIXIN), read(RENDER_SECTION_MIXIN), read(LEVEL_RENDERER_MIXIN));
		checkBackendNotice(read(BACKEND_NOTICE), read(LANG));
	}
	
	/**
	 * A GPU that lacks what the mod needs keeps Minecraft on Vulkan with the mod off, which the
	 * toast and the Video Settings line both say, and every line the notice shows must have its text.
	 */
	private static void checkBackendNotice(String noticeSource, String langSource) {
		require(methodBody(noticeSource, "public static void showToastOnce").contains("else if (!VulkanImprovementSupport.enabled())"), "the toast must say when Vulkan runs on a GPU the mod cannot use");
		require(methodBody(noticeSource, "public static @Nullable Component videoSettingsLine").contains("if (!VulkanImprovementSupport.enabled())"), "Video Settings must say when Vulkan runs on a GPU the mod cannot use");
		require(!noticeSource.contains("onlyTheModRequires"), "the mod no longer turns a device down, so no failure is the mod's doing");
		Matcher key = NOTICE_KEY.matcher(noticeSource);
		int keys = 0;
		while (key.find()) {
			keys++;
			require(langSource.contains("\"" + key.group(1) + "\":"), "the lang file must give the text of " + key.group(1));
		}
		require(keys >= 8, "the check must find the notice's lines");
	}
	
	/**
	 * Every option but those that hold whatever else is set must follow its rule on the screen.
	 */
	private static void checkEveryDependentOptionIsGreyedOut(String source) {
		String update = methodBody(source, "public static void updateWidgetState");
		Matcher option = OPTION_FIELD.matcher(source);
		int options = 0;
		while (option.find()) {
			options++;
			String name = option.group(1);
			require(INDEPENDENT_OPTIONS.contains(name) || update.contains("setWidgetActive(optionsList, " + name + ", rules."), name + " must be greyed out by a rule while it can do nothing");
		}
		require(options > INDEPENDENT_OPTIONS.size(), "the check must find the video options");
	}
	
	private static void checkOptionRules() {
		VulkanVideoOptionRules none = rules();
		require(!none.meshTerrain() && !none.captureBootstrap() && !none.capturedTerrain() && !none.drawnMeshTerrain() && !none.presentPacing() && !none.gpuTimer() && !none.worldgenProvider(), "a device without what the options need must leave them greyed out");
		VulkanVideoOptionRules supported = rules("meshTerrainSupported");
		require(supported.meshTerrain() && supported.captureBootstrap(), "a device with mesh shaders must offer Mesh Terrain and Capture Bootstrap");
		require(!supported.capturedTerrain() && !supported.drawnMeshTerrain(), "what reads captured terrain must wait for a capture");
		VulkanVideoOptionRules bootstrap = rules("meshTerrainSupported", "captureBootstrapOn");
		require(bootstrap.capturedTerrain() && !bootstrap.drawnMeshTerrain(), "Capture Bootstrap captures terrain but draws none");
		VulkanVideoOptionRules meshTerrain = rules("meshTerrainSupported", "meshTerrainOn");
		require(!meshTerrain.captureBootstrap(), "Mesh Terrain captures terrain anyway, so Capture Bootstrap must grey out");
		require(meshTerrain.capturedTerrain() && meshTerrain.drawnMeshTerrain() && meshTerrain.terrainInView(), "Mesh Terrain must enable what it reads");
		require(meshTerrain.meshletCulling() && !meshTerrain.taskMeshletCulling() && !meshTerrain.taskPageCulling(), "without GPU task commands terrain is drawn from a list, not from pages");
		require(!meshTerrain.gpuDrivenTerrain(), "GPU-Driven Terrain must wait for Cancel Vanilla Terrain");
		VulkanVideoOptionRules taskCommands = rules("meshTerrainSupported", "meshTerrainOn", "gpuTaskCommandsOn");
		require(!taskCommands.meshletCulling() && taskCommands.taskMeshletCulling() && taskCommands.taskPageCulling(), "GPU task commands draw pages, which the task stage culls");
		VulkanVideoOptionRules full = rules("meshTerrainSupported", "meshTerrainOn", "gpuTaskCommandsOn", "cancelVanillaTerrainOn", "gpuDrivenTerrainOn", "fullCapturedTerrainOn");
		require(full.drawnMeshTerrain() && full.meshletCulling(), "Full Captured Terrain draws meshlets the mesh stage culls");
		require(!full.terrainInView() && !full.gpuDrivenTerrain() && !full.taskMeshletCulling() && !full.taskPageCulling(), "Full Captured Terrain must grey out whatever finds terrain in view");
		VulkanVideoOptionRules gpuDriven = rules("meshTerrainSupported", "meshTerrainOn", "cancelVanillaTerrainOn", "gpuDrivenTerrainOn");
		require(gpuDriven.gpuDrivenTerrain() && gpuDriven.taskMeshletCulling() && !gpuDriven.taskPageCulling(), "GPU-driven terrain draws pages without the listed path");
		require(!gpuDriven.occlusionCulling() && rules("meshTerrainSupported", "meshTerrainOn", "cancelVanillaTerrainOn", "gpuDrivenTerrainOn", "improvedTransparencyOn").occlusionCulling(), "Terrain Occlusion Culling must need improved transparency");
		require(!rules("meshTerrainSupported", "meshTerrainOn", "cancelVanillaTerrainOn", "improvedTransparencyOn").occlusionCulling(), "Terrain Occlusion Culling must need GPU-driven terrain");
		require(!rules("shadingRateSupported", "shadingRateOn").shadingRate() && rules("meshTerrainSupported", "meshTerrainOn", "shadingRateSupported").shadingRate(), "Terrain VRS must need mesh terrain and the device");
		require(!rules("meshTerrainSupported", "meshTerrainOn", "shadingRateSupported").shadingRateSize() && rules("meshTerrainSupported", "meshTerrainOn", "shadingRateSupported", "shadingRateOn").shadingRateSize(), "Terrain VRS Size must need Terrain VRS");
		require(!rules("presentPacingSupported").presentPacing() && !rules("vsyncOn").presentPacing() && rules("presentPacingSupported", "vsyncOn").presentPacing(), "Present Pacing must need the device and VSync");
	}
	
	private static VulkanVideoOptionRules rules(String... on) {
		Set<String> set = Set.of(on);
		return new VulkanVideoOptionRules(set.contains("meshTerrainSupported"), set.contains("meshTerrainOn"), set.contains("captureBootstrapOn"), set.contains("cancelVanillaTerrainOn"), set.contains("fullCapturedTerrainOn"), set.contains("gpuTaskCommandsOn"), set.contains("gpuDrivenTerrainOn"), set.contains("improvedTransparencyOn"), set.contains("shadingRateSupported"), set.contains("shadingRateOn"), set.contains("presentPacingSupported"), set.contains("vsyncOn"), set.contains("gpuTimerSupported"), set.contains("worldgenProviderPresent"));
	}
	
	private static void checkRuntimeKeepsOptionsVisibleForActiveVulkan(String source) {
		String body = methodBody(source, "public static boolean shouldShowVideoOptions");
		int activeVulkanCheck = body.indexOf("if (isVulkanBackendActive())");
		int pendingOpenGlReject = body.indexOf("if (preferred == PreferredGraphicsApi.OPENGL)");
		require(activeVulkanCheck >= 0, "video option visibility must check whether Vulkan is currently active");
		require(pendingOpenGlReject >= 0, "video option visibility must still reject pending OpenGL when Vulkan is inactive");
		require(activeVulkanCheck < pendingOpenGlReject, "active Vulkan must keep VIM Video Settings visible even when the pending restart preference is OpenGL");
		require(body.contains("return preferred == PreferredGraphicsApi.VULKAN;"), "inactive runtime must still expose VIM Video Settings when Vulkan is the selected backend");
	}
	
	private static void checkVideoSettingsUsesRuntimeVisibilityGate(String videoOptionsSource, String mixinSource) {
		require(videoOptionsSource.contains("VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())"), "VIM video option creation/update must use the central runtime visibility gate");
		require(mixinSource.contains("VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())"), "Video Settings mixin must use the central runtime visibility gate");
		require(mixinSource.contains("VulkanImprovementVideoOptions.createOptions()"), "Video Settings mixin must append the VIM config flags through VulkanImprovementVideoOptions");
	}
	
	private static void checkTerrainCaptureBootstrapIsExplicitOptIn(String configSource, String videoOptionsSource, String langSource) {
		requireContains(configSource, "booleanProperty(\"vim.enableTerrainCaptureBootstrap\", false)", "terrain capture/bootstrap must be disabled by default");
		requireContains(configSource, "public static boolean terrainCaptureEnabled()", "terrain config must expose one predicate for capture/upload hot paths");
		requireContains(methodBody(configSource, "public static boolean terrainCaptureEnabled"), "replaceVanillaTerrain() || terrainCaptureBootstrap", "terrain capture must be enabled only by mesh replacement or explicit capture-bootstrap opt-in");
		requireContains(methodBody(configSource, "public static String rendererMode"), "\"vanilla\"", "renderer mode must report vanilla when mesh replacement and capture bootstrap are disabled");
		requireContains(methodBody(configSource, "public static String rendererMode"), "\"mesh-capture-bootstrap\"", "renderer mode must retain an explicit capture/bootstrap mode");
		requireContains(configSource, "enableTerrainCaptureBootstrap=\" + terrainCaptureBootstrapEnabled()", "diagnostics must report whether terrain capture/bootstrap is enabled");
		requireContains(videoOptionsSource, "terrainCaptureBootstrap", "Video Settings must expose the terrain capture/bootstrap opt-in flag");
		requireContains(videoOptionsSource, "TerrainRendererDebugConfig.terrainCaptureBootstrapEnabled()", "Video Settings must sync from the terrain capture/bootstrap config flag");
		requireContains(videoOptionsSource, "TerrainRendererDebugConfig::setTerrainCaptureBootstrapEnabled", "Video Settings must update the terrain capture/bootstrap config flag");
		requireContains(langSource, "\"options.vulkanimprovement.terrainCaptureBootstrap\"", "lang file must name the terrain capture/bootstrap option");
		requireContains(langSource, "\"options.vulkanimprovement.terrainCaptureBootstrap.tooltip\"", "lang file must describe the terrain capture/bootstrap option");
	}
	
	private static void checkStrictTerrainCancellationIsExplicitOptIn(String configSource, String langSource) {
		requireContains(configSource, "strictMeshTerrainReplacement = booleanProperty(\"vim.strictMeshTerrainReplacement\", false)", "strict terrain cancellation must be disabled by default");
		requireContains(methodBody(configSource, "public static String rendererMode"), "\"mesh-overlay-vanilla-fallback\"", "terrain renderer mode must report fallback overlay mode when strict cancellation is off");
		requireContains(methodBody(configSource, "public static String rendererMode"), "\"mesh-strict-replacement\"", "terrain renderer mode must report strict replacement when cancellation is enabled");
		requireContains(configSource, "strictMeshTerrainReplacement=\" + strictMeshTerrainReplacement()", "diagnostics must report whether strict terrain cancellation is enabled");
		requireContains(langSource, "\"options.vulkanimprovement.strictMeshTerrainReplacement\": \"Cancel Vanilla Terrain\"", "Video Settings must label strict replacement as explicit vanilla cancellation");
		requireContains(langSource, "Keep off until visible coverage is proven", "Video Settings tooltip must warn that strict terrain cancellation is unsafe before coverage validation");
	}
	
	private static void checkTranslucentTerrainFlagIsMetadataOnly(String configSource, String langSource) {
		requireContains(configSource, "public static String translucentLayerMode()", "terrain config must expose the visible translucent fallback mode");
		requireContains(methodBody(configSource, "public static String translucentLayerMode"), "\"metadata-capture-vanilla-visible\"", "translucent flag must report metadata capture with vanilla visible rendering");
		requireContains(configSource, "defaultTranslucentLayerMode=\" + translucentLayerMode()", "diagnostics must report the shared translucent-layer mode");
		requireContains(langSource, "Captures translucent terrain metadata", "Video Settings tooltip must describe the translucent flag as metadata capture");
		requireContains(langSource, "Visible water and translucency remain vanilla", "Video Settings tooltip must say visible translucent rendering remains vanilla");
		require(!langSource.contains("Routes translucent terrain through the mesh-shader path"), "Video Settings tooltip must not claim visible translucent mesh replacement is active");
		require(!configSource.contains("mesh-experimental-translucent"), "terrain diagnostics must not claim visible translucent mesh replacement is active");
	}
	
	private static void checkOpenGlActiveSessionsDoNotEnterTerrainServices(String chunkSectionsSource, String renderSectionSource, String levelRendererSource) {
		requireCallGuard(methodBody(chunkSectionsSource, "private void vim$observeTerrainGroup"), "MeshTerrainRenderer.get().observeTerrainGroup", "terrain render-group observation must be gated off for OpenGL-active sessions");
		// The slice lookup records and takes section layers only while a frame is collected, and a
		// frame is begun only behind both gates.
		String takeSectionLayer = methodBody(levelRendererSource, "private SectionRenderDispatcher.RenderSectionBufferSlice vim$takeSectionLayer");
		requireCallGuard(takeSectionLayer, "SectionMeshletStore.recordDrawSlice", "terrain draw-slice recording must happen only while a frame is collected", "if (!SectionMeshletStore.collectingFrame())");
		requireCallGuard(takeSectionLayer, "SectionMeshletStore.takeSectionLayer", "taking section layers must happen only while a frame is collected", "if (!SectionMeshletStore.collectingFrame())");
		requireCallGuard(methodBody(renderSectionSource, "private void vim$captureSectionMeshletInput"), "SectionMeshletStore.capture", "terrain section capture must be gated off for OpenGL-active sessions");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$beginVisibleFrame"), "MeshTerrainRenderer.get().beginVisibleFrame", "terrain visibility-frame reset must be gated off for OpenGL-active sessions");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$endVisibleFrame"), "MeshTerrainRenderer.get().prepareVisibleFrame(", "terrain frame preparation must be gated off for OpenGL-active sessions");
		for (Path layerMixin : List.of(DRAW_INDIRECT_MIXIN, DRAW_SEPARATE_MIXIN)) {
			requireCallGuard(methodBody(readUnchecked(layerMixin), "private void vim$drawMeshTerrainLayer"), "MeshTerrainRenderer.get().drawTerrainLayer", "terrain layer draws must be gated off for OpenGL-active sessions");
		}
		requireCallGuard(methodBody(levelRendererSource, "private void vim$clearMeshletCacheForGeometryInvalidation"), "SectionMeshletStore.clearAll", "geometry invalidation cache clears must be gated off for OpenGL-active sessions");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$clearMeshletCacheForLevelReset"), "SectionMeshletStore.clearAll", "level reset cache clears must be gated off for OpenGL-active sessions");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$clearMeshletCacheForRendererClose"), "SectionMeshletStore.clearAll", "renderer-close cache clears must be gated off for OpenGL-active sessions");
	}
	
	private static void checkTerrainMirrorHotPathsRequireCaptureOptIn(String meshRendererSource, String chunkSectionsSource, String renderSectionSource, String levelRendererSource) {
		require(methodBody(meshRendererSource, "public boolean beginVisibleFrame").contains("terrainResources.uploadDirtyTerrainData()"), "terrain metadata is uploaded as a frame begins");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$beginVisibleFrame"), "MeshTerrainRenderer.get().beginVisibleFrame", "terrain metadata uploads must be skipped unless capture/bootstrap or mesh replacement is enabled", "TerrainRendererDebugConfig.terrainCaptureEnabled()");
		requireCallGuard(methodBody(chunkSectionsSource, "private void vim$observeTerrainGroup"), "MeshTerrainRenderer.get().observeTerrainGroup", "terrain render-group observation must be skipped unless capture/bootstrap or mesh replacement is enabled", "TerrainRendererDebugConfig.terrainCaptureEnabled()");
		requireCallGuard(methodBody(renderSectionSource, "private void vim$captureSectionMeshletInput"), "SectionMeshletStore.capture", "terrain section capture must be skipped unless capture/bootstrap or mesh replacement is enabled", "TerrainRendererDebugConfig.terrainCaptureEnabled()");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$beginVisibleFrame"), "MeshTerrainRenderer.get().beginVisibleFrame", "terrain visibility resets must be skipped unless capture/bootstrap or mesh replacement is enabled", "TerrainRendererDebugConfig.terrainCaptureEnabled()");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$clearMeshletCacheForGeometryInvalidation"), "SectionMeshletStore.clearAll", "geometry invalidation cache clears must be skipped unless capture/bootstrap or mesh replacement is enabled", "TerrainRendererDebugConfig.terrainCaptureEnabled()");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$clearMeshletCacheForLevelReset"), "SectionMeshletStore.clearAll", "level reset cache clears must be skipped unless capture/bootstrap or mesh replacement is enabled", "TerrainRendererDebugConfig.terrainCaptureEnabled()");
		requireCallGuard(methodBody(levelRendererSource, "private void vim$clearMeshletCacheForRendererClose"), "SectionMeshletStore.clearAll", "renderer-close cache clears must be skipped unless capture/bootstrap or mesh replacement is enabled", "TerrainRendererDebugConfig.terrainCaptureEnabled()");
	}
	
	private static void requireCallGuard(String methodBody, String serviceCall, String message) {
		requireCallGuard(methodBody, serviceCall, message, "VulkanImprovementRuntime.isModAvailable()");
	}
	
	private static void requireCallGuard(String methodBody, String serviceCall, String message, String requiredGate) {
		int serviceCallIndex = methodBody.indexOf(serviceCall);
		require(serviceCallIndex >= 0, "missing guarded service call: " + serviceCall);
		int gate = methodBody.indexOf(requiredGate);
		require(gate >= 0 && gate < serviceCallIndex, message);
	}
	
	private static String methodBody(String source, String signature) {
		int signatureStart = source.indexOf(signature);
		require(signatureStart >= 0, "missing method signature: " + signature);
		int bodyStart = source.indexOf('{', signatureStart);
		require(bodyStart >= 0, "missing method body: " + signature);
		int depth = 0;
		for (int i = bodyStart; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') {
				depth++;
			}
			else if (c == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart + 1, i);
				}
			}
		}
		throw new AssertionError("unterminated method body: " + signature);
	}
	
	private static String read(Path path) throws IOException {
		return JavaSources.read(path);
	}
	
	private static String readUnchecked(Path path) {
		try {
			return read(path);
		} catch (IOException ex) {
			throw new AssertionError("failed to read " + path, ex);
		}
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