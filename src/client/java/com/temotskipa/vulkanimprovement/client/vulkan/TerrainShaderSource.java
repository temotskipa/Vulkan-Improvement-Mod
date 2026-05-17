package com.temotskipa.vulkanimprovement.client.vulkan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class TerrainShaderSource {
    static final String TASK_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.task.glsl";
    static final String MESH_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.mesh.glsl";
    static final String FRAGMENT_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.frag.glsl";
    static final String MESH_TASK_COMMAND_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_task_command.comp.glsl";

    private TerrainShaderSource() {
    }
    
    static String load(String path) {
        ClassLoader classLoader = TerrainShaderSource.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing terrain shader resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read terrain shader resource " + Objects.requireNonNull(path), ex);
        }
    }
}
