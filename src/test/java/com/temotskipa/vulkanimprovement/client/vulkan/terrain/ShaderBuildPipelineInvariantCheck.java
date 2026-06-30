package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ShaderBuildPipelineInvariantCheck {
    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    
    private ShaderBuildPipelineInvariantCheck() {
    }
    
    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        String build = Files.readString(BUILD_GRADLE);
        checkReleaseBuildRequiresShaderValidator(build);
        checkBothShaderTasksUseReleaseRequirement(build);
    }
    
    private static void checkReleaseBuildRequiresShaderValidator(String source) {
        require(source.contains("vim.releaseBuild"), "build must expose a release-build flag for release-grade shader validation");
        require(source.contains("releaseBuild.get() || requireShaderValidator.get()"), "release builds must imply required glslangValidator checks");
    }
    
    private static void checkBothShaderTasksUseReleaseRequirement(String source) {
        String compileTask = taskBody(source, "tasks.register(\"compileTerrainSpirv\")");
        String checkTask = taskBody(source, "tasks.register(\"checkTerrainShaders\")");
        require(compileTask.contains("inputs.property(\"releaseBuild\", releaseBuild)"), "SPIR-V generation task must track release-build mode as an input");
        require(compileTask.contains("requireTerrainShaderValidator()"), "SPIR-V generation task must fail without glslangValidator for release builds");
        require(checkTask.contains("inputs.property(\"releaseBuild\", releaseBuild)"), "terrain shader validation task must track release-build mode as an input");
        require(checkTask.contains("requireTerrainShaderValidator()"), "terrain shader validation task must fail without glslangValidator for release builds");
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
            } else if (c == '}') {
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