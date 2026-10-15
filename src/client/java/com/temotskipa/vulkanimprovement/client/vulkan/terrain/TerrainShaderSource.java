package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompiler;

final class TerrainShaderSource {
    static final String TASK_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.task.glsl";
    static final String MESH_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.mesh.glsl";
    static final String FRAGMENT_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.frag.glsl";
    static final String MESH_TASK_COMMAND_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_task_command.comp.glsl";
    static final String VISIBLE_SECTION_PAGES_SHADER = "assets/vulkanimprovement/shaders/terrain/visible_section_pages.comp.glsl";
    static final String TASK_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_terrain.task.spv";
    static final String MESH_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_terrain.mesh.spv";
    static final String FRAGMENT_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_terrain.frag.spv";
    static final String MESH_TASK_COMMAND_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_task_command.comp.spv";
    static final String VISIBLE_SECTION_PAGES_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/visible_section_pages.comp.spv";
    
    private TerrainShaderSource() {
    }
    
    static String load(String path) {
        return ShaderCompiler.source(path);
    }
}