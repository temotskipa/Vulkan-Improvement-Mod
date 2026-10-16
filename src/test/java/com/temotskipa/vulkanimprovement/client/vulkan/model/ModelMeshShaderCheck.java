package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.shaderc.ShadercIncludeResolve;
import org.lwjgl.util.shaderc.ShadercIncludeResult;
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the mesh stage made from Minecraft's entity vertex shader: it compiles as a mesh shader the
 * way Minecraft's compiler compiles its shaders (Vulkan 1.2, uniforms bound automatically, its
 * includes), under the defines Minecraft's entity pipelines use; it writes every output of the
 * vertex shader; and a vertex shader the mesh stage cannot take gets none.
 */
public final class ModelMeshShaderCheck {
    private static final String ENTITY_VERTEX_SHADER = "assets/minecraft/shaders/core/entity.vsh";
    private static final List<List<String>> DEFINE_SETS = List.of(List.of(), List.of("PER_FACE_LIGHTING"), List.of("NO_OVERLAY"), List.of("EMISSIVE", "NO_CARDINAL_LIGHTING"), List.of("APPLY_TEXTURE_MATRIX"), List.of("GLINT"), List.of("OIT_ALPHA_ONLY"));
    private static final Pattern OUTPUT_LOCATION = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*out\\b");
    /**
     * The compiler recurses deeply; the game compiles on threads with room for it.
     */
    private static final long COMPILE_THREAD_STACK_BYTES = 64L * 1024L * 1024L;
    
    private ModelMeshShaderCheck() {
    }
    
    static void main() throws InterruptedException {
        String vertexShader = entityVertexShader();
        String meshShader = Objects.requireNonNull(ModelMeshShader.generate(vertexShader, 16), "the entity vertex shader must get a mesh stage");
        Matcher output = OUTPUT_LOCATION.matcher(vertexShader);
        while (output.find()) {
            require(meshShader.contains("vim_store_" + output.group(1) + "(vertex);"), "the mesh stage must write the vertex shader's output at location " + output.group(1));
        }
        require(ModelMeshShader.generate(vertexShader.replaceFirst("void main\\(\\) \\{", "void main() {\n    int vertex = gl_VertexIndex;"), 0) == null, "a vertex shader that reads gl_VertexIndex must get no mesh stage");
        require(ModelMeshShader.generate(vertexShader.replace("in vec3 Normal;", "in vec4 Normal;"), 0) == null, "a vertex shader whose inputs are not the entity format's must get no mesh stage");
        require(ModelMeshShader.generate(vertexShader.replace("layout(location = 5) in vec3 Normal;", "layout(location = 5) in vec3 Normal;\nlayout(location = 6) in vec2 UV3;"), 0) == null, "a vertex shader with an input the entity format lacks must get no mesh stage");
        
        List<String> errors = compileUnderEachDefineSet(meshShader);
        require(errors.isEmpty(), "the entity mesh stage must compile:" + System.lineSeparator() + String.join(System.lineSeparator(), errors));
        System.out.println("Model mesh shader check passed: the entity mesh stage compiles under " + DEFINE_SETS.size() + " sets of defines.");
    }
    
    /**
     * The compiler's errors for the mesh stage under each set of defines, compiled on a thread with
     * the stack the compiler needs.
     */
    private static List<String> compileUnderEachDefineSet(String meshShader) throws InterruptedException {
        List<String> errors = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread compileThread = new Thread(null, () -> {
            try {
                for (List<String> defines : DEFINE_SETS) {
                    String error = compileErrors(meshShader, defines);
                    if (error != null) {
                        errors.add(defines + ": " + error);
                    }
                }
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "model-mesh-shader-check", COMPILE_THREAD_STACK_BYTES);
        compileThread.start();
        compileThread.join();
        if (failure.get() != null) {
            throw new AssertionError("the entity mesh stage must compile", failure.get());
        }
        return errors;
    }
    
    /**
     * The compiler's errors for the mesh stage under these defines, or null when it compiles.
     */
    private static @Nullable String compileErrors(String meshShader, List<String> defines) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        try (ShadercIncludeResolve resolve = ShadercIncludeResolve.create((_, requestedSource, _, _, _) -> include(MemoryUtil.memUTF8(requestedSource)));
             ShadercIncludeResultRelease release = ShadercIncludeResultRelease.create((_, includeResult) -> {
                 MemoryUtil.nmemFree(MemoryUtil.memGetAddress(includeResult + ShadercIncludeResult.SOURCE_NAME));
                 MemoryUtil.nmemFree(MemoryUtil.memGetAddress(includeResult + ShadercIncludeResult.CONTENT));
                 MemoryUtil.nmemFree(includeResult);
             })) {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            Shaderc.shaderc_compile_options_set_include_callbacks(options, resolve, release, 0L);
            for (String define : defines) {
                Shaderc.shaderc_compile_options_add_macro_definition(options, define, "");
            }
            long result = Shaderc.shaderc_compile_into_spv(compiler, meshShader, Shaderc.shaderc_mesh_shader, "entity" + ModelMeshShader.MODULE_SUFFIX, "main", options);
            try {
                return Shaderc.shaderc_result_get_compilation_status(result) == Shaderc.shaderc_compilation_status_success ? null : Shaderc.shaderc_result_get_error_message(result);
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
    
    /**
     * A Minecraft include, {@code namespace:path}, from {@code assets/<namespace>/shaders/include/},
     * as the result shaderc takes; one that is missing fails the compile.
     */
    private static long include(String requested) {
        int separator = requested.indexOf(':');
        String path = "assets/" + requested.substring(0, Math.max(separator, 0)) + "/shaders/include/" + requested.substring(separator + 1);
        String content = null;
        try (InputStream stream = ModelMeshShaderCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (stream != null) {
                content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        long result = MemoryUtil.nmemCalloc(1, ShadercIncludeResult.SIZEOF);
        ShadercIncludeResult.nsource_name(result, MemoryUtil.memUTF8(requested, false));
        ShadercIncludeResult.ncontent(result, MemoryUtil.memUTF8(content == null ? "#error include not found: " + requested + "\n" : content, false));
        return result;
    }
    
    private static String entityVertexShader() {
        try (InputStream stream = ModelMeshShaderCheck.class.getClassLoader().getResourceAsStream(ENTITY_VERTEX_SHADER)) {
            require(stream != null, "missing " + ENTITY_VERTEX_SHADER + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}