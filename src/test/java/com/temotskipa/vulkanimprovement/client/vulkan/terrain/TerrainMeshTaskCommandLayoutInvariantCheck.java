package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.util.Map;

public final class TerrainMeshTaskCommandLayoutInvariantCheck {
    private TerrainMeshTaskCommandLayoutInvariantCheck() {
    }

    @SuppressWarnings("unused")
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
        require(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_PUSH_CONSTANT_BYTES == Long.BYTES * 2 + Integer.BYTES * 4, "mesh-task command push constants must cover two addresses and four uints");
        require(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_USE_PUSH_TASK_COUNT_FLAG == 1, "push task-count command flag must match shader constant");
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
        require(((Map<?, ?>) pushConstants).get("meshTaskCommands").equals(8), "push constant diagnostics must include command buffer address offset");
        require(((Map<?, ?>) pushConstants).get("reserved").equals(28), "push constant diagnostics must include reserved offset");
        require(diagnostics.get("usePushTaskCountFlag").equals(TerrainGpuLayout.TERRAIN_MESH_TASK_COMMAND_USE_PUSH_TASK_COUNT_FLAG), "diagnostics must include push task-count flag");
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
