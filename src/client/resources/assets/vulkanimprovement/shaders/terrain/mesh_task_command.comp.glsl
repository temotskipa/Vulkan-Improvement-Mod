#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

layout(local_size_x = 1) in;

struct TerrainWorkQueueRecord {
    uint meshletIndex;
    uint layerOrdinal;
    uint lodLevel;
    uint flags;
};

struct MeshTaskCommand {
    uint groupCountX;
    uint groupCountY;
    uint groupCountZ;
};

layout(buffer_reference, scalar, buffer_reference_align = 4) readonly buffer TerrainWorkQueue {
    uint producedCount;
    uint consumedCount;
    uint droppedCount;
    uint reserved;
    TerrainWorkQueueRecord records[];
};

layout(buffer_reference, scalar, buffer_reference_align = 4) buffer MeshTaskCommands {
    MeshTaskCommand commands[];
};

layout(push_constant, scalar) uniform CommandPushConstants {
    uint64_t workQueue;
    uint64_t meshTaskCommands;
    uint commandIndex;
    uint maxTaskCount;
    uint flags;
    uint reserved;
} pc;

const uint COMMAND_FLAG_USE_PUSH_TASK_COUNT = 1u;

void main() {
    TerrainWorkQueue queue = TerrainWorkQueue(pc.workQueue);
    MeshTaskCommands commands = MeshTaskCommands(pc.meshTaskCommands);
    uint produced = (pc.flags & COMMAND_FLAG_USE_PUSH_TASK_COUNT) != 0u ? pc.maxTaskCount : min(queue.producedCount, pc.maxTaskCount);
    commands.commands[pc.commandIndex].groupCountX = produced;
    commands.commands[pc.commandIndex].groupCountY = produced == 0u ? 0u : 1u;
    commands.commands[pc.commandIndex].groupCountZ = produced == 0u ? 0u : 1u;
}
