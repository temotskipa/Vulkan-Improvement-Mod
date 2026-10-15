package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuRecords;

import java.io.IOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The records the CPU writes for the GPU must be the structs the shaders read: the same members in
 * the same order, each as large, and as large as the strides the buffers are laid out with.
 */
public final class TerrainGpuRecordsInvariantCheck {
    private static final Path SHADERS = Path.of("src/client/resources/assets/vulkanimprovement/shaders/terrain");
    private static final Pattern STRUCT = Pattern.compile("struct\\s+(\\w+)\\s*\\{([^}]*)}");
    private static final Pattern MEMBER = Pattern.compile("(\\w+)\\s+(\\w+)\\s*;");
    private static final Map<String, Long> GLSL_BYTES = Map.of("int", 4L, "uint", 4L, "float", 4L, "vec2", 8L, "vec3", 12L, "vec4", 16L, "uint64_t", 8L);
    
    private TerrainGpuRecordsInvariantCheck() {
    }
    
    
    static void main() throws IOException {
        checkStrides();
        List<String> shaders = new ArrayList<>();
        try (Stream<Path> files = Files.list(SHADERS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".glsl")).sorted().toList()) {
                shaders.add(Files.readString(file));
            }
        }
        checkMatchesShaders(TerrainGpuRecords.ResidentPage.LAYOUT, shaders, 3);
        checkMatchesShaders(TerrainGpuRecords.MeshletHeader.LAYOUT, shaders, 2);
        checkMatchesShaders(TerrainGpuRecords.MaterialRecord.LAYOUT, shaders, 1);
        checkMatchesShaders(TerrainGpuRecords.VisibleRecord.LAYOUT, shaders, 2);
        checkMatchesShaders(TerrainGpuRecords.WorkQueueRecord.LAYOUT, shaders, 1);
        checkMatchesShaders(TerrainGpuRecords.MeshTaskCommand.LAYOUT, shaders, 2);
    }
    
    private static void checkStrides() {
        require(TerrainGpuRecords.ResidentPage.LAYOUT.byteSize() == TerrainGpuLayout.SECTION_METADATA_STRIDE, "a page's record must be as large as the page table's stride");
        require(TerrainGpuRecords.MeshletHeader.LAYOUT.byteSize() == TerrainGpuLayout.MESHLET_HEADER_STRIDE, "a meshlet's header must be as large as the header stride");
        require(TerrainGpuRecords.MaterialRecord.LAYOUT.byteSize() == TerrainGpuLayout.MATERIAL_RECORD_STRIDE, "a material's record must be as large as the material table's stride");
        require(TerrainGpuRecords.VisibleRecord.LAYOUT.byteSize() == TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE, "a task's record must be as large as the visible list's stride");
        require(TerrainGpuRecords.WorkQueueRecord.LAYOUT.byteSize() == TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE, "a work queue record must be as large as the work queue's stride");
        require(TerrainGpuRecords.MeshTaskCommand.LAYOUT.byteSize() == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, "a mesh-task command must be as large as the command stride");
    }
    
    /**
     * Every shader that declares the record's struct must declare the record's members, and at
     * least {@code minimumShaders} of them must declare it.
     */
    private static void checkMatchesShaders(StructLayout layout, List<String> shaders, int minimumShaders) {
        String name = layout.name().orElseThrow();
        int declarations = 0;
        for (String shader : shaders) {
            Matcher struct = STRUCT.matcher(shader);
            while (struct.find()) {
                if (struct.group(1).equals(name)) {
                    checkMembers(layout, struct.group(2));
                    declarations++;
                }
            }
        }
        require(declarations >= minimumShaders, name + " must be declared by at least " + minimumShaders + " shaders, found " + declarations);
    }
    
    private static void checkMembers(StructLayout layout, String body) {
        String name = layout.name().orElseThrow();
        List<MemoryLayout> members = layout.memberLayouts();
        Matcher member = MEMBER.matcher(body);
        int index = 0;
        while (member.find()) {
            require(index < members.size(), name + " declares more members in a shader than in Java: " + member.group(2));
            MemoryLayout expected = members.get(index);
            require(expected.name().orElseThrow().equals(member.group(2)), name + " member " + index + " is " + member.group(2) + " in a shader but " + expected.name().orElseThrow() + " in Java");
            Long shaderBytes = GLSL_BYTES.get(member.group(1));
            require(shaderBytes != null && shaderBytes == expected.byteSize(), name + "." + member.group(2) + " has " + shaderBytes + " bytes in a shader but " + expected.byteSize() + " in Java");
            index++;
        }
        require(index == members.size(), name + " declares " + index + " members in a shader but " + members.size() + " in Java");
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}