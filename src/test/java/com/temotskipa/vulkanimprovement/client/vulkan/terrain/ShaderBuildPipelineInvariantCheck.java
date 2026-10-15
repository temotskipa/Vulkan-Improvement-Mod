package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompileChecks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ShaderBuildPipelineInvariantCheck {
    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    
    private ShaderBuildPipelineInvariantCheck() {
    }
    
    
    static void main() throws IOException {
        String build = Files.readString(BUILD_GRADLE);
        checkReleaseBuildRequiresShaderValidator(build);
        checkBothShaderTasksUseReleaseRequirement(build);
        checkPrecompiledShaderNames(build);
    }
    
    private static void checkReleaseBuildRequiresShaderValidator(String source) {
        require(source.contains("vim.releaseBuild"), "build must expose a release-build flag for release-grade shader validation");
        require(source.contains("releaseBuild.get() || requireShaderValidator.get()"), "release builds must imply required glslangValidator checks");
    }
    
    private static void checkBothShaderTasksUseReleaseRequirement(String source) {
        String compileTask = taskBody(source, "tasks.register(\"compileShaderSpirv\")");
        String checkTask = taskBody(source, "tasks.register(\"checkShaders\")");
        require(compileTask.contains("inputs.property(\"releaseBuild\", releaseBuild)"), "SPIR-V generation task must track release-build mode as an input");
        require(compileTask.contains("requireShaderValidatorTool()"), "SPIR-V generation task must fail without glslangValidator for release builds");
        require(checkTask.contains("inputs.property(\"releaseBuild\", releaseBuild)"), "shader validation task must track release-build mode as an input");
        require(checkTask.contains("requireShaderValidatorTool()"), "shader validation task must fail without glslangValidator for release builds");
    }
    
    /**
     * The game loads a shader's SPIR-V from where the build writes it, and compiles the GLSL when
     * it is not there, so a name that differs only costs the compile at startup and is otherwise
     * not noticed.
     */
    private static void checkPrecompiledShaderNames(String source) {
        String compileTask = taskBody(source, "tasks.register(\"compileShaderSpirv\")");
        require(compileTask.contains("shaders/${shaderFile.parentFile.name}/spirv"), "SPIR-V generation task must write a shader family's SPIR-V into its spirv directory");
        require(compileTask.contains("shaderFile.name.replaceFirst(/\\.glsl$/, \".spv\")"), "SPIR-V generation task must name a shader's SPIR-V after its GLSL file, .glsl replaced by .spv");
        Map<String, String> terrainShaders = Map.of(TerrainShaderSource.TASK_SHADER, TerrainShaderSource.TASK_SHADER_SPIRV, TerrainShaderSource.MESH_SHADER, TerrainShaderSource.MESH_SHADER_SPIRV, TerrainShaderSource.FRAGMENT_SHADER, TerrainShaderSource.FRAGMENT_SHADER_SPIRV, TerrainShaderSource.MESH_TASK_COMMAND_SHADER, TerrainShaderSource.MESH_TASK_COMMAND_SHADER_SPIRV, TerrainShaderSource.VISIBLE_SECTION_PAGES_SHADER, TerrainShaderSource.VISIBLE_SECTION_PAGES_SHADER_SPIRV);
        terrainShaders.forEach((glsl, spirv) -> require(spirv.equals(ShaderCompileChecks.precompiledResource(glsl)), "terrain shader SPIR-V must be where the build writes it: " + spirv));
    }
    
    private static String taskBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        require(signatureStart >= 0, "missing Gradle task: " + signature);
        int bodyStart = source.indexOf('{', signatureStart);
        require(bodyStart >= 0, "missing task body: " + signature);
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
        throw new AssertionError("unterminated task body: " + signature);
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}