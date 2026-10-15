package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import org.lwjgl.util.shaderc.Shaderc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Compiles the terrain shaders with the compiler the game compiles them with at startup, set up
 * the same way. A development build has no other GLSL compiler unless the Vulkan SDK is installed,
 * and without this a shader that does not compile is first noticed as a crash when the game starts.
 */
public final class TerrainShaderCompileCheck {
    /**
     * The compiler recurses deeply; the game compiles on its main thread, which has room for it.
     */
    private static final long COMPILE_THREAD_STACK_BYTES = 64L * 1024L * 1024L;
    private static final Map<String, Integer> SHADERS = Map.of(TerrainShaderSource.TASK_SHADER, Shaderc.shaderc_task_shader, TerrainShaderSource.MESH_SHADER, Shaderc.shaderc_mesh_shader, TerrainShaderSource.FRAGMENT_SHADER, Shaderc.shaderc_fragment_shader, TerrainShaderSource.MESH_TASK_COMMAND_SHADER, Shaderc.shaderc_compute_shader, TerrainShaderSource.VISIBLE_SECTION_PAGES_SHADER, Shaderc.shaderc_compute_shader);
    
    private TerrainShaderCompileCheck() {
    }
    
    
    static void main() throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread compileThread = new Thread(null, () -> {
            try {
                compileAll();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "terrain-shader-compile-check", COMPILE_THREAD_STACK_BYTES);
        compileThread.start();
        compileThread.join();
        if (failure.get() != null) {
            throw new AssertionError("terrain shaders must compile", failure.get());
        }
    }
    
    private static void compileAll() {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_4);
            Shaderc.shaderc_compile_options_set_target_spirv(options, Shaderc.shaderc_spirv_version_1_6);
            Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_zero);
            Shaderc.shaderc_compile_options_set_generate_debug_info(options);
            List<String> errors = new ArrayList<>();
            SHADERS.forEach((resource, kind) -> {
                long result = Shaderc.shaderc_compile_into_spv(compiler, TerrainShaderSource.load(resource), kind, resource, "main", options);
                try {
                    if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                        errors.add(Shaderc.shaderc_result_get_error_message(result));
                    }
                } finally {
                    Shaderc.shaderc_result_release(result);
                }
            });
            if (!errors.isEmpty()) {
                throw new AssertionError(String.join(System.lineSeparator(), errors));
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}