package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.util.TerrainGpuLayout;

import java.util.Map;

public final class TerrainMeshTaskCommandLayoutInvariantCheck {
    private TerrainMeshTaskCommandLayoutInvariantCheck() {
    }
    
    
    static void main(String[] args) {
        checkCommandLayout();
        checkCapacityBytes();
        checkDiagnostics();
        checkInvalidInputsRejected();
    }
    
    @SuppressWarnings("ConstantValue")
    private static void checkCommandLayout() {
        require(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE == Integer.BYTES * 3, "mesh-task indirect command must stay three uints");
        require(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY > 0, "mesh-task command capacity must be positive");
        require(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES == Long.BYTES * 4 + Integer.BYTES * 8 + Float.BYTES * 4 * 6, "visibility pass push constants must cover four addresses, eight uints and the camera");
        require(TerrainGpuLayout.VISIBILITY_COUNTER_OFFSET + 3 * Integer.BYTES <= TerrainGpuLayout.DEBUG_COUNTER_BYTES, "the visibility counters must fit behind the mesh stage's in the debug counters");
        require(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE % Integer.BYTES == 0, "mesh-task command stride must be int-aligned");
    }
    
    private static void checkCapacityBytes() {
        require(TerrainMeshTaskCommandLayout.bytesForCapacity() == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE * (long) TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY, "command byte size must cover every command record");
        require(TerrainMeshTaskCommandLayout.commandOffset(4) == TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE * 4L, "command offsets must use command stride");
    }
    
    private static void checkDiagnostics() {
        Map<String, Object> diagnostics = TerrainMeshTaskCommandLayout.asMap();
        require(diagnostics.get("commandStride").equals(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_STRIDE), "diagnostics must include command stride");
        require(diagnostics.get("commandCapacity").equals(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_CAPACITY), "diagnostics must include command capacity");
        require(diagnostics.get("commandPushConstantBytes").equals(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES), "diagnostics must include command push constant bytes");
        require(diagnostics.get("bytes").equals(TerrainMeshTaskCommandLayout.bytesForCapacity()), "diagnostics must include total bytes");
        Object fields = diagnostics.get("commandFields");
        require(fields instanceof Map<?, ?>, "diagnostics must include command field offsets");
        require(((Map<?, ?>) fields).get("groupCountX").equals(0), "command diagnostics must include groupCountX offset");
        require(((Map<?, ?>) fields).get("groupCountZ").equals(8), "command diagnostics must include groupCountZ offset");
        Object pushConstants = diagnostics.get("pushConstantFields");
        require(pushConstants instanceof Map<?, ?>, "diagnostics must include command push constant offsets");
        require(((Map<?, ?>) pushConstants).get("meshTaskCommands").equals(16), "push constant diagnostics must include command buffer address offset");
        require(((Map<?, ?>) pushConstants).get("camera").equals(64), "push constant diagnostics must include the camera offset");
        require(diagnostics.get("visibilityWorkgroupSize").equals(TerrainGpuLayout.VISIBILITY_WORKGROUP_SIZE), "diagnostics must include the visibility workgroup size");
    }
    
    private static void checkInvalidInputsRejected() {
        requireThrows(() -> TerrainMeshTaskCommandLayout.commandOffset(-1));
    }
    
    private static void requireThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("negative command index must be rejected");
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}