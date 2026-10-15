package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Compiles shaders with the compiler the game compiles them with at startup, set up the same way.
 * A development build has no other GLSL compiler unless the Vulkan SDK is installed, and without
 * this a shader that does not compile is first noticed as a crash when the game starts.
 */
public final class ShaderCompileChecks {
    /**
     * The compiler recurses deeply; the game compiles on its main thread, which has room for it.
     */
    private static final long COMPILE_THREAD_STACK_BYTES = 64L * 1024L * 1024L;
    
    private ShaderCompileChecks() {
    }
    
    /**
     * Where the build puts a shader's SPIR-V and the game looks for it:
     * {@code shaders/<family>/<name>.<stage>.glsl} compiles to
     * {@code shaders/<family>/spirv/<name>.<stage>.spv}.
     */
    public static String precompiledResource(String glslResource) {
        int family = glslResource.lastIndexOf('/');
        if (family < 0 || !glslResource.endsWith(".glsl")) {
            throw new IllegalArgumentException("not a GLSL resource in a shader family: " + glslResource);
        }
        String name = glslResource.substring(family + 1, glslResource.length() - ".glsl".length());
        return glslResource.substring(0, family) + "/spirv/" + name + ".spv";
    }
    
    /**
     * Fails with every compiler error of the shaders, each a GLSL resource and its shaderc kind.
     */
    public static void requireCompiles(String what, Map<String, Integer> shaders) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<String> errors = new ArrayList<>();
        Thread compileThread = new Thread(null, () -> {
            try (ShaderCompiler compiler = new ShaderCompiler()) {
                shaders.forEach((resource, kind) -> {
                    String error = compiler.compileErrors(resource, kind);
                    if (error != null) {
                        errors.add(resource + ": " + error);
                    }
                });
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, what + "-compile-check", COMPILE_THREAD_STACK_BYTES);
        compileThread.start();
        compileThread.join();
        if (failure.get() != null) {
            throw new AssertionError(what + " must compile", failure.get());
        }
        if (!errors.isEmpty()) {
            throw new AssertionError(what + " must compile:" + System.lineSeparator() + String.join(System.lineSeparator(), errors));
        }
    }
}
