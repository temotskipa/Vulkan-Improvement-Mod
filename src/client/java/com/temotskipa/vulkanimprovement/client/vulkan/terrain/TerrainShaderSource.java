package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class TerrainShaderSource {
    static final String TASK_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.task.glsl";
    static final String MESH_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.mesh.glsl";
    static final String FRAGMENT_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_terrain.frag.glsl";
    static final String MESH_TASK_COMMAND_SHADER = "assets/vulkanimprovement/shaders/terrain/mesh_task_command.comp.glsl";
    static final String TASK_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_terrain.task.spv";
    static final String MESH_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_terrain.mesh.spv";
    static final String FRAGMENT_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_terrain.frag.spv";
    static final String MESH_TASK_COMMAND_SHADER_SPIRV = "assets/vulkanimprovement/shaders/terrain/spirv/mesh_task_command.comp.spv";
    
    private TerrainShaderSource() {
    }
    
    static String load(String path) {
        return loadText(path);
    }
    
    static ByteBuffer loadSpirv(String path) {
        ClassLoader classLoader = TerrainShaderSource.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            byte[] bytes = stream.readAllBytes();
            if (bytes.length == 0 || (bytes.length % Integer.BYTES) != 0) {
                throw new IllegalStateException("Invalid SPIR-V resource size for " + path);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read terrain SPIR-V resource " + Objects.requireNonNull(path), ex);
        }
    }
    
    private static String loadText(String path) {
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