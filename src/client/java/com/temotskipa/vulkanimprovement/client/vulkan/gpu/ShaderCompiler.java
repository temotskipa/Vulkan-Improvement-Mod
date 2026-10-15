package com.temotskipa.vulkanimprovement.client.vulkan.gpu;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Turns the mod's shaders into Vulkan shader modules: the SPIR-V the build compiled where there is
 * some, otherwise the GLSL compiled at runtime with the shaderc Minecraft ships.
 */
public final class ShaderCompiler implements AutoCloseable {
    private final long compiler = Shaderc.shaderc_compiler_initialize();
    private final long options = Shaderc.shaderc_compile_options_initialize();
    private String lastCompileSource = "unconfigured";
    
    public ShaderCompiler() {
        Shaderc.shaderc_compile_options_set_target_env(this.options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_4);
        Shaderc.shaderc_compile_options_set_target_spirv(this.options, Shaderc.shaderc_spirv_version_1_6);
        Shaderc.shaderc_compile_options_set_optimization_level(this.options, Shaderc.shaderc_optimization_level_zero);
        Shaderc.shaderc_compile_options_set_generate_debug_info(this.options);
    }
    
    /**
     * The text of a GLSL resource.
     */
    public static String source(String path) {
        try (InputStream stream = ShaderCompiler.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing shader resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read shader resource " + path, ex);
        }
    }
    
    /**
     * The words of a SPIR-V resource, or null where the build compiled none.
     */
    private static @Nullable ByteBuffer spirv(String path) {
        try (InputStream stream = ShaderCompiler.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            byte[] bytes = stream.readAllBytes();
            if (bytes.length == 0 || bytes.length % Integer.BYTES != 0) {
                throw new IllegalStateException("Invalid SPIR-V resource size for " + path);
            }
            return ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder()).put(bytes).flip();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read SPIR-V resource " + path, ex);
        }
    }
    
    /**
     * Where the last module came from: the build's SPIR-V or runtime compilation.
     */
    public String lastCompileSource() {
        return this.lastCompileSource;
    }
    
    /**
     * A shader module of a GLSL resource, made from its SPIR-V resource where the build made one.
     */
    public long compile(VulkanDevice device, String glslResource, String spirvResource, int shaderKind) {
        ByteBuffer precompiled = spirv(spirvResource);
        if (precompiled != null) {
            this.lastCompileSource = "precompiled-spirv";
            return createShaderModule(device, glslResource, precompiled);
        }
        this.lastCompileSource = "runtime-shaderc";
        long result = Shaderc.shaderc_compile_into_spv(this.compiler, source(glslResource), shaderKind, glslResource, "main", this.options);
        try {
            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                throw new IllegalStateException("Failed to compile " + glslResource + ": " + Shaderc.shaderc_result_get_error_message(result));
            }
            return createShaderModule(device, glslResource, Objects.requireNonNull(Shaderc.shaderc_result_get_bytes(result)));
        } finally {
            Shaderc.shaderc_result_release(result);
        }
    }
    
    private static long createShaderModule(VulkanDevice device, String filename, ByteBuffer spirv) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack).sType$Default();
            moduleCreateInfo.pCode(spirv);
            long[] module = new long[1];
            VulkanUtils.crashIfFailure(device, VK12.vkCreateShaderModule(device.vkDevice(), moduleCreateInfo, null, module), "Failed to create " + filename);
            return module[0];
        }
    }
    
    @Override
    public void close() {
        Shaderc.shaderc_compile_options_release(this.options);
        Shaderc.shaderc_compiler_release(this.compiler);
    }
}