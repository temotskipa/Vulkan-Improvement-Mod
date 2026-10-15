package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompileChecks;
import org.lwjgl.util.shaderc.Shaderc;

import java.util.Map;

/**
 * Compiles the terrain shaders with the compiler the game compiles them with at startup (see
 * {@link ShaderCompileChecks}).
 */
public final class TerrainShaderCompileCheck {
    private static final Map<String, Integer> SHADERS = Map.of(TerrainShaderSource.TASK_SHADER, Shaderc.shaderc_task_shader, TerrainShaderSource.MESH_SHADER, Shaderc.shaderc_mesh_shader, TerrainShaderSource.FRAGMENT_SHADER, Shaderc.shaderc_fragment_shader, TerrainShaderSource.MESH_TASK_COMMAND_SHADER, Shaderc.shaderc_compute_shader, TerrainShaderSource.VISIBLE_SECTION_PAGES_SHADER, Shaderc.shaderc_compute_shader);
    
    private TerrainShaderCompileCheck() {
    }
    
    
    static void main() throws InterruptedException {
        ShaderCompileChecks.requireCompiles("terrain shaders", SHADERS);
    }
}
