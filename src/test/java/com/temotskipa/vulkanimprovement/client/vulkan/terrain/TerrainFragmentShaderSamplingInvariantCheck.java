package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerrainFragmentShaderSamplingInvariantCheck {
    private static final Path FRAGMENT_SHADER = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain/mesh_terrain.frag.glsl");
    private static final Path MESH_SHADER = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain/mesh_terrain.mesh.glsl");
    private static final Path TASK_SHADER = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain/mesh_terrain.task.glsl");
    private static final Path MESH_SHADER_PROGRAM = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshShaderTerrainProgram.java");
    private static final Path DESCRIPTOR_RESOURCES = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/DescriptorHeapTerrainResources.java");
    private static final Path TERRAIN_GROUP_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderMixin.java");
    
    private TerrainFragmentShaderSamplingInvariantCheck() {
    }
    
    
    static void main() throws IOException {
        String shader = Files.readString(FRAGMENT_SHADER);
        checkBlockAtlasSamplingIsCrispAndMipAware(shader);
        checkCutoutAlphaUsesVanillaMask(shader);
        checkOpaqueMaskedAlphaOutput(shader);
        String meshShader = Files.readString(MESH_SHADER);
        checkLightingMatchesMinecraft(shader, meshShader);
        checkFogMatchesMinecraft(shader, meshShader);
        checkImprovedFog(shader, Files.readString(MESH_SHADER_PROGRAM), Files.readString(DESCRIPTOR_RESOURCES), Files.readString(TERRAIN_GROUP_MIXIN));
        checkPushConstantsAgree(shader, meshShader, Files.readString(TASK_SHADER), Files.readString(MESH_SHADER_PROGRAM));
        checkPositionsStayExactFarFromTheOrigin(meshShader, Files.readString(MESH_SHADER_PROGRAM));
    }
    
    /**
     * Terrain is lit by the lightmap alone, looked up per vertex at the centre of the texel a light
     * level names, as Minecraft's terrain is; the fragment stage multiplies the atlas by the lit
     * colour. A floor under the light, which this shader once had, made caves and night brighter
     * than Minecraft draws them.
     */
    private static void checkLightingMatchesMinecraft(String shader, String meshShader) {
        require(meshShader.contains("const float LIGHTMAP_HALF_TEXEL = 0.5 / 16.0;") && meshShader.contains("clamp(lightUv + LIGHTMAP_HALF_TEXEL, LIGHTMAP_HALF_TEXEL, 1.0 - LIGHTMAP_HALF_TEXEL)"), "the lightmap must be sampled at texel centres");
        require(meshShader.contains("meshColor[vertex] = unpackUnorm4x8(blockVertex.color) * light;"), "the vertex colour must be lit by the lightmap in the mesh stage");
        require(shader.contains("vec4 lit = atlas * meshColor;"), "terrain colour must be the atlas and the lit vertex colour multiplied");
        require(!shader.contains("vec3(0.35)") && !shader.contains("max(texture(") && !meshShader.contains("max(textureLod("), "the lightmap must not be given a floor");
    }
    
    /**
     * Mesh terrain fades into the frame's fog and fades in with its section, as Minecraft's does.
     * The mesh stage supplies the two distances Minecraft's fogs are measured over.
     */
    private static void checkFogMatchesMinecraft(String shader, String meshShader) {
        require(meshShader.contains("layout(location = 4) out vec3 meshFog[];") && shader.contains("layout(location = 4) in vec3 meshFog;"), "the mesh stage must hand fog distances and section fade to the fragment stage");
        require(meshShader.contains("max(length(relative.xz), abs(relative.y))"), "render-distance fog must be measured over the larger of horizontal distance and height difference");
        require(meshShader.contains("mix(1.0, clamp(taskPayload.meshletVisibility, 0.0, 1.0), fadeApplies)"), "a section's fade must not apply next to the camera");
        require(meshShader.contains("meshColor[vertex] = unpackUnorm4x8(blockVertex.color) * light;") && !meshShader.contains("meshColor[vertex] = unpackUnorm4x8(blockVertex.color) * light * "), "a section's fade must not change the alpha that cutout terrain is tested on");
        require(shader.contains("fogAmount(meshFog.x, fog.fogRange.x, fog.fogRange.y)") && shader.contains("fogAmount(meshFog.y, fog.fogRange.z, fog.fogRange.w)"), "environmental fog must use the distance from the camera and render-distance fog the cylindrical distance");
        require(shader.contains("vec3 fogged = mix(lit.rgb, fogTarget, fogValue);") && shader.contains("vec3 shown = mix(behind, fogged, sectionShown);"), "terrain must fade into the fog with distance and out of what is behind it as its section fades in");
    }
    
    /**
     * With improved transparency Minecraft fades terrain into the sky and cloud images behind it.
     * Those are the pipelines it draws terrain with whenever that setting is on, so a mesh path
     * that left them to Minecraft would draw nothing for anyone who uses it.
     */
    private static void checkImprovedFog(String shader, String program, String resources, String groupMixin) {
        require(shader.contains("layout(set = 0, binding = 4) uniform texture2D skyTexture;") && shader.contains("layout(set = 0, binding = 5) uniform texture2D cloudsTexture;") && shader.contains("layout(set = 0, binding = 6) uniform texture2D cloudsDepthTexture;"), "the fragment stage must be able to read the sky, cloud and cloud-depth images");
        require(shader.contains("if (fog.fogMode.x != 0)"), "the sky and cloud images must only be read when the draw uses improved fog");
        require(shader.contains("gl_FragCoord.z >= texelFetch(cloudsDepthTexture, pixel, 0).r"), "clouds must only show through terrain they are behind");
        require(shader.contains("max(smoothstep(0.9, 1.0, environmentalFog), renderDistanceFog)"), "environmental fog must give way to the background only at its far end");
        require(program.contains("VkDescriptorSetLayoutBinding.calloc(7, stack)"), "the mesh pipeline must declare the three improved-fog images");
        require(program.contains("boolean improvedFog = TerrainRenderContext.improvedFog();") && program.contains("TerrainFogState.write(fogConstants, improvedFog);"), "each terrain draw must tell the fragment stage which kind of fog its group uses");
        require(resources.contains("if (improvedFog && !(imageBindingReady(SKY_IMAGE) && imageBindingReady(CLOUDS_IMAGE) && imageBindingReady(CLOUDS_DEPTH_IMAGE)))"), "a draw with improved fog must be refused while its images are unknown");
        require(groupMixin.contains("resources.recordImageBinding(DescriptorHeapTerrainResources.SKY_IMAGE, improvedFogTextures.skyView());"), "the improved-fog images must be the ones Minecraft binds for the group");
    }
    
    /**
     * The task and mesh stages declare the same block, the fragment stage's fog follows it, and the
     * whole fits what a Vulkan 1.4 device must accept.
     */
    private static void checkPushConstantsAgree(String shader, String meshShader, String taskShader, String program) {
        String meshBlock = pushConstantBlock(meshShader);
        require(meshBlock.equals(pushConstantBlock(taskShader)), "the task and mesh stages must declare the same push constants");
        require(meshBlock.contains("ivec4 cameraBlock;") && meshBlock.contains("vec4 cameraOffset;"), "the camera position must be split into a block and an offset");
        int stageBytes = GlslSource.bytes(GlslSource.body(meshShader, "uniform TerrainPushConstants {"));
        require(TerrainGpuLayout.TERRAIN_PUSH_CONSTANT_BYTES == stageBytes, "the task and mesh push-constant size must match the shader block");
        int vectorAlignment = 4 * Float.BYTES;
        String fogBlock = GlslSource.body(shader, "uniform TerrainFogConstants {");
        int fogOffset = GlslSource.layoutValue(fogBlock, "offset");
        int fogBytes = GlslSource.bytes(fogBlock);
        require(fogOffset + fogBytes <= TerrainGpuLayout.VULKAN_1_4_MIN_PUSH_CONSTANT_BYTES, "terrain push constants must fit the size every Vulkan 1.4 device accepts");
        require(fogOffset == (stageBytes + vectorAlignment - 1) / vectorAlignment * vectorAlignment, "the fog push constants must start at the first vector-aligned offset after the other stages'");
        require(fogBytes == TerrainGpuLayout.TERRAIN_FOG_PUSH_CONSTANT_BYTES, "the fog push constants are a colour, four distances and a mode");
        require(shader.contains("layout(offset = " + (TerrainGpuLayout.TERRAIN_FOG_PUSH_CONSTANT_OFFSET + 2 * vectorAlignment) + ") ivec4 fogMode;"), "the fragment stage must read the fog mode where the program writes it");
        require(shader.contains("layout(offset = " + TerrainGpuLayout.TERRAIN_FOG_PUSH_CONSTANT_OFFSET + ") vec4 fogColor;") && shader.contains("layout(offset = " + (TerrainGpuLayout.TERRAIN_FOG_PUSH_CONSTANT_OFFSET + vectorAlignment) + ") vec4 fogRange;"), "the fragment stage must read the fog where the program writes it");
        require(program.contains("pushConstantRanges.apply(1, range -> range.stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT).offset(TerrainGpuLayout.TERRAIN_FOG_PUSH_CONSTANT_OFFSET).size(TerrainGpuLayout.TERRAIN_FOG_PUSH_CONSTANT_BYTES));"), "the pipeline layout must give the fragment stage its own push-constant range");
        require(program.contains("VK10.VK_SHADER_STAGE_FRAGMENT_BIT, TerrainGpuLayout.TERRAIN_FOG_PUSH_CONSTANT_OFFSET, fogConstants);"), "the frame's fog must be pushed for every terrain draw");
    }
    
    /**
     * A float cannot hold a position far from the world origin exactly, so the camera's block is
     * subtracted from a section's as integers before anything becomes a float.
     */
    private static void checkPositionsStayExactFarFromTheOrigin(String meshShader, String program) {
        require(meshShader.contains("return vec3(section * 16 - pc.cameraBlock.xyz) + (local - pc.cameraOffset.xyz);"), "camera-relative positions must be formed from an integer block difference");
        require(!meshShader.contains("float(header.sectionX * 16)"), "section coordinates must not be turned into floats before the camera is subtracted");
        require(program.contains("target.putInt(cameraBlockX).putInt(cameraBlockY).putInt(cameraBlockZ).putInt(0);") && program.contains("(float) (cameraX - cameraBlockX)"), "the program must write the camera's block as integers and the rest as an offset");
    }
    
    private static String pushConstantBlock(String shader) {
        int start = shader.indexOf("uniform TerrainPushConstants {");
        int end = shader.indexOf("} pc;", start);
        require(start >= 0 && end > start, "missing terrain push-constant block");
        return shader.substring(start, end).replace("\r", "");
    }
    
    /**
     * The atlas is sampled through its sampler with explicit gradients, so distant terrain uses the
     * mip chain as vanilla terrain does, and the sample point is pulled to the texel centre, so
     * near terrain stays sharp. A direct fetch of mip 0 was sharp but shimmered at a distance and
     * kept cutout foliage fuller than vanilla (runtime comparison of 2026-10-13).
     */
    private static void checkBlockAtlasSamplingIsCrispAndMipAware(String shader) {
        require(shader.contains("textureSize(sampler2D(blockAtlasTexture, blockAtlasSampler), 0)"), "terrain block atlas sampling must inspect the atlas size");
        require(shader.contains("textureGrad(sampler2D(blockAtlasTexture, blockAtlasSampler)") && shader.contains("dFdx(uv), dFdy(uv)"), "terrain block atlas sampling must go through the sampler with the gradients of the unmodified coordinate");
        require(shader.contains("fwidth(texel)") && shader.contains("clamp((texel - nearestEdge) / texelsPerPixel, -0.5, 0.5)"), "terrain block atlas sampling must keep magnified texels hard-edged");
        require(!shader.contains("texelFetch(blockAtlasTexture") && !shader.contains("texelFetch(sampler2D(blockAtlasTexture"), "terrain block atlas sampling must not fetch a fixed mip level");
        require(shader.contains("vec4 atlas = sampleBlockAtlas(meshUv0);"), "the fragment colour must come from the shared atlas sampling function");
    }
    
    private static void checkCutoutAlphaUsesVanillaMask(String shader) {
        require(shader.contains("float alphaCutoff = alphaMasked ? 0.5 : (alphaBlended ? 0.1 : 0.0);"), "terrain must be cut out at the thresholds of Minecraft's cutout and translucent pipelines, and solid terrain not at all");
        require(shader.contains("if (lit.a < alphaCutoff)"), "fragment discard must test the lit colour's alpha against the material's cutoff");
    }
    
    private static void checkOpaqueMaskedAlphaOutput(String shader) {
        require(shader.contains("float materialAlpha = alphaBlended ? lit.a : 1.0;"), "solid and alpha-masked terrain must render opaque in the replacement path");
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}