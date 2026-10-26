package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.*;

import java.io.IOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.StructLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The records the CPU writes for the GPU must be the structs the shaders read: the same members in
 * the same order, each as large, and as large as the strides the buffers are laid out with.
 */
public final class TerrainGpuRecordsInvariantCheck {
    private static final Pattern STRUCT = Pattern.compile("struct\\s+(\\w+)\\s*\\{([^}]*)}");
    
    private TerrainGpuRecordsInvariantCheck() {
    }
    
    
    static void main() throws IOException {
        checkStrides();
        List<String> shaders = new ArrayList<>();
        try (Stream<Path> files = Files.list(GlslSource.SHADERS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".glsl")).sorted().toList()) {
                shaders.add(Files.readString(file));
            }
        }
        checkMatchesShaders(TerrainResidentPageRecord.LAYOUT, shaders, 3);
        checkMatchesShaders(TerrainMeshletHeaderRecord.LAYOUT, shaders, 2);
        checkMatchesShaders(TerrainMaterialRecord.LAYOUT, shaders, 1);
        checkMatchesShaders(TerrainVisibleRecord.LAYOUT, shaders, 2);
        checkMatchesShaders(TerrainWorkQueueRecord.LAYOUT, shaders, 1);
        checkMatchesShaders(TerrainMeshTaskCommandRecord.LAYOUT, shaders, 2);
    }
    
    private static void checkStrides() {
        require(TerrainResidentPageRecord.LAYOUT.byteSize() == TerrainGpuLayout.SECTION_METADATA_STRIDE, "a page's record must be as large as the page table's stride");
        require(TerrainMeshletHeaderRecord.LAYOUT.byteSize() == TerrainGpuLayout.MESHLET_HEADER_STRIDE, "a meshlet's header must be as large as the header stride");
        require(TerrainMaterialRecord.LAYOUT.byteSize() == TerrainGpuLayout.MATERIAL_RECORD_STRIDE, "a material's record must be as large as the material table's stride");
        require(TerrainVisibleRecord.LAYOUT.byteSize() == TerrainGpuLayout.VISIBLE_MESHLET_RECORD_STRIDE, "a task's record must be as large as the visible list's stride");
        require(TerrainWorkQueueRecord.LAYOUT.byteSize() == TerrainGpuLayout.TERRAIN_WORK_QUEUE_RECORD_STRIDE, "a work queue record must be as large as the work queue's stride");
        require(TerrainMeshTaskCommandRecord.LAYOUT.byteSize() == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE, "a mesh-task command must be as large as the command stride");
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
        List<MemoryLayout> expected = layout.memberLayouts();
        List<GlslMember> declared = GlslSource.members(body);
        require(declared.size() == expected.size(), name + " declares " + declared.size() + " members in a shader but " + expected.size() + " in Java");
        for (int index = 0; index < declared.size(); index++) {
            GlslMember member = declared.get(index);
            MemoryLayout java = expected.get(index);
            require(java.name().orElseThrow().equals(member.name()), name + " member " + index + " is " + member.name() + " in a shader but " + java.name().orElseThrow() + " in Java");
            require(member.bytes() == java.byteSize(), name + "." + member.name() + " has " + member.bytes() + " bytes in a shader but " + java.byteSize() + " in Java");
        }
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}