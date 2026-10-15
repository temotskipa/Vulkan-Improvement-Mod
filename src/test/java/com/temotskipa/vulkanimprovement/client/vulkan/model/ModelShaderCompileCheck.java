package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompileChecks;
import org.lwjgl.util.shaderc.Shaderc;

import java.util.Map;

/**
 * Compiles the model shaders with the compiler the game compiles them with at startup (see
 * {@link ShaderCompileChecks}).
 */
public final class ModelShaderCompileCheck {
    private static final Map<String, Integer> SHADERS = Map.of(ModelGpuVertices.SHADER, Shaderc.shaderc_compute_shader);
    
    private ModelShaderCompileCheck() {
    }
    
    static void main() throws InterruptedException {
        ShaderCompileChecks.requireCompiles("model shaders", SHADERS);
    }
}
