package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mesh stage that stands in for Minecraft's vertex stage in a GPU model draw (step 3 of the
 * GPU-driven pipeline plan). It is Minecraft's own vertex shader, compiled by Minecraft with its
 * defines and includes, run once for each vertex of a cube: the vertex Minecraft would have fetched
 * is made from the cube and part records as the vertex pass makes it, and what the vertex shader
 * writes goes to the mesh outputs at the same locations, for Minecraft's fragment shader. One
 * workgroup draws one cube, its faces as Minecraft's quad index buffer splits them, so a draw's
 * triangles come in Minecraft's order.
 * <p>
 * The vertex shader's inputs and outputs become plain globals, its {@code main} an ordinary
 * function and {@code gl_Position} a global. A declaration under a preprocessor condition defines a
 * macro beside it, so the copy to or from it is compiled only where the declaration is. A vertex
 * shader this cannot take gets no mesh stage: one with an input outside the entity format, a push
 * constant block, or built-ins only the vertex stage has.
 */
public final class ModelMeshShader {
    /**
     * The bytes of the mesh stage's push constants, which start where Minecraft's own end.
     */
    public static final int PUSH_CONSTANT_BYTES = 40;
    static final int WORKGROUP_SIZE = 32;
    /**
     * A mesh stage module's name ends with this, which tells its reflection which stage it is.
     */
    public static final String MODULE_SUFFIX = " (VIM model mesh)";
    /**
     * The 8-bit conversions the vertex fetch makes, one table after the other: unsigned normalized
     * for colors, then signed normalized for normals, each indexed by the byte.
     */
    static final int CONVERSION_TABLE_ENTRIES = 512;
    
    private static final int CUBE_VERTICES = ModelGpuRecords.MAX_CUBE_POLYGONS * ModelGpuRecords.POLYGON_VERTICES;
    private static final int CUBE_TRIANGLES = ModelGpuRecords.MAX_CUBE_POLYGONS * 2;
    /**
     * The inputs of Minecraft's entity format and the types its vertex fetch gives them.
     */
    private static final Map<String, String> ENTITY_INPUTS = Map.of("Position", "vec3", "Color", "vec4", "UV0", "vec2", "UV1", "ivec2", "UV2", "ivec2", "Normal", "vec3");
    private static final Pattern VERSION = Pattern.compile("^[ \\t]*#[ \\t]*version\\b.*$", Pattern.MULTILINE);
    private static final Pattern EXTENSION = Pattern.compile("^[ \\t]*#[ \\t]*extension\\b.*$", Pattern.MULTILINE);
    private static final Pattern INPUT = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*\\d+\\s*\\)\\s*in\\s+(\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern OUTPUT = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*((?:(?:flat|noperspective|smooth|centroid)\\s+)*)out\\s+(\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern OTHER_INTERFACE = Pattern.compile("\\b(?:in|out)\\s+\\w+\\s+\\w+\\s*(?:\\[[^]]*])?\\s*;");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern POSITION = Pattern.compile("\\bgl_Position\\b");
    private static final Pattern VERTEX_ONLY = Pattern.compile("\\b(?:gl_VertexIndex|gl_VertexID|gl_InstanceIndex|gl_InstanceID|gl_BaseVertex|gl_BaseInstance|gl_DrawID|gl_PointSize|gl_ClipDistance|gl_CullDistance|gl_PerVertex|push_constant)\\b");
    
    private ModelMeshShader() {
    }
    
    public static boolean isMeshModule(String moduleName) {
        return moduleName.endsWith(MODULE_SUFFIX);
    }
    
    /**
     * The table of 8-bit conversions, as the vertex fetch converts: {@code c / 255} for an unsigned
     * normalized byte, {@code max(c / 127, -1)} for a signed one, each rounded once to a float.
     */
    static float[] conversionTable() {
        float[] table = new float[CONVERSION_TABLE_ENTRIES];
        for (int c = 0; c < 256; c++) {
            table[c] = c / 255.0F;
            table[256 + c] = Math.max((byte) c / 127.0F, -1.0F);
        }
        return table;
    }
    
    /**
     * The mesh stage for a vertex shader, its push constants starting at {@code pushConstantOffset},
     * or null when the vertex shader is one this cannot take.
     */
    public static @Nullable String generate(String vertexSource, int pushConstantOffset) {
        if (VERTEX_ONLY.matcher(vertexSource).find() || !MAIN.matcher(vertexSource).find()) {
            return null;
        }
        Matcher inputs = INPUT.matcher(vertexSource);
        while (inputs.find()) {
            if (!inputs.group(1).equals(ENTITY_INPUTS.get(inputs.group(2)))) {
                return null;
            }
        }
        String otherDeclarations = OUTPUT.matcher(INPUT.matcher(vertexSource).replaceAll("")).replaceAll("");
        if (OTHER_INTERFACE.matcher(otherDeclarations).find()) {
            return null;
        }
        
        StringBuilder extensions = new StringBuilder();
        Matcher extension = EXTENSION.matcher(vertexSource);
        while (extension.find()) {
            extensions.append(extension.group().strip()).append('\n');
        }
        String body = EXTENSION.matcher(VERSION.matcher(vertexSource).replaceAll("")).replaceAll("");
        body = INPUT.matcher(body).replaceAll(input -> Matcher.quoteReplacement(input.group(1) + " " + input.group(2) + ";\n#define VIM_IN_" + input.group(2) + "\n"));
        SortedSet<Integer> locations = new TreeSet<>();
        body = OUTPUT.matcher(body).replaceAll(output -> {
            int location = Integer.parseInt(output.group(1));
            locations.add(location);
            String qualifiers = output.group(2);
            String type = output.group(3);
            String name = output.group(4);
            return Matcher.quoteReplacement(type + " " + name + ";\n" + "layout(location = " + location + ") " + qualifiers + "out " + type + " vim_out_" + location + "[];\n" + "void vim_store_" + location + "(uint vertex) { vim_out_" + location + "[vertex] = " + name + "; }\n" + "#define VIM_OUT_" + location + "\n");
        });
        body = MAIN.matcher(body).replaceFirst("void vim_vertex_main()");
        body = POSITION.matcher(body).replaceAll("vim_Position");
        
        StringBuilder stores = new StringBuilder();
        for (int location : locations) {
            stores.append("#ifdef VIM_OUT_").append(location).append('\n');
            stores.append("        vim_store_").append(location).append("(vertex);\n");
            stores.append("#endif\n");
        }
        return header(extensions, pushConstantOffset) + body + meshMain(stores);
    }
    
    private static String header(CharSequence extensions, int pushConstantOffset) {
        return """
               #version 460
               #extension GL_EXT_mesh_shader : require
               #extension GL_EXT_buffer_reference : require
               %s
               layout(local_size_x = %d) in;
               layout(triangles, max_vertices = %d, max_primitives = %d) out;
               
               layout(buffer_reference, std430, buffer_reference_align = 4) readonly buffer VimWords {
                   uint words[];
               };
               
               layout(push_constant, std430) uniform VimModelMesh {
                   layout(offset = %d) VimWords polygons;
                   layout(offset = %d) VimWords parts;
                   layout(offset = %d) VimWords jobs;
                   layout(offset = %d) VimWords conversions;
                   layout(offset = %d) uint firstJob;
                   layout(offset = %d) uint jobCount;
               } vim_pass;
               
               vec4 vim_Position;
               """.formatted(extensions, WORKGROUP_SIZE, CUBE_VERTICES, CUBE_TRIANGLES, pushConstantOffset, pushConstantOffset + 8, pushConstantOffset + 16, pushConstantOffset + 24, pushConstantOffset + 32, pushConstantOffset + 36);
    }
    
    private static String meshMain(CharSequence stores) {
        int polygonWords = Math.toIntExact(ModelPolygonRecord.BYTES / Integer.BYTES);
        int cornerWords = Math.toIntExact(ModelPolygonRecord.CORNER_BYTES / Integer.BYTES);
        int polygonDirection = Math.toIntExact(ModelPolygonRecord.DIRECTION / Integer.BYTES);
        int partWords = Math.toIntExact(ModelPartRecord.BYTES / Integer.BYTES);
        int partNormals = Math.toIntExact(ModelPartRecord.NORMALS / Integer.BYTES);
        int partColor = Math.toIntExact(ModelPartRecord.COLOR / Integer.BYTES);
        int partOverlay = Math.toIntExact(ModelPartRecord.OVERLAY / Integer.BYTES);
        int partLight = Math.toIntExact(ModelPartRecord.LIGHT / Integer.BYTES);
        int jobWords = Math.toIntExact(ModelJobRecord.BYTES / Integer.BYTES);
        return """
               
               float vim_unorm8(uint value, uint shift) {
                   return uintBitsToFloat(vim_pass.conversions.words[(value >> shift) & 255u]);
               }
               
               float vim_snorm8(uint value, uint shift) {
                   return uintBitsToFloat(vim_pass.conversions.words[256u + ((value >> shift) & 255u)]);
               }
               
               ivec2 vim_shorts(uint value) {
                   return ivec2(bitfieldExtract(int(value), 0, 16), bitfieldExtract(int(value), 16, 16));
               }
               
               void main() {
                   if (gl_WorkGroupID.x >= vim_pass.jobCount) {
                       SetMeshOutputsEXT(0u, 0u);
                       return;
                   }
                   uint job = (vim_pass.firstJob + gl_WorkGroupID.x) * %du;
                   uint polygons = vim_pass.jobs.words[job + 2u] >> %du;
                   SetMeshOutputsEXT(polygons * 4u, polygons * 2u);
                   uint vertex = gl_LocalInvocationIndex;
                   if (vertex < polygons * 4u) {
                       uint part = vim_pass.jobs.words[job] * %du;
                       uint polygon = (vim_pass.jobs.words[job + 1u] + vertex / 4u) * %du;
                       uint source = polygon + (vertex %% 4u) * %du;
                       float m00 = uintBitsToFloat(vim_pass.parts.words[part + 0u]);
                       float m01 = uintBitsToFloat(vim_pass.parts.words[part + 1u]);
                       float m02 = uintBitsToFloat(vim_pass.parts.words[part + 2u]);
                       float m10 = uintBitsToFloat(vim_pass.parts.words[part + 3u]);
                       float m11 = uintBitsToFloat(vim_pass.parts.words[part + 4u]);
                       float m12 = uintBitsToFloat(vim_pass.parts.words[part + 5u]);
                       float m20 = uintBitsToFloat(vim_pass.parts.words[part + 6u]);
                       float m21 = uintBitsToFloat(vim_pass.parts.words[part + 7u]);
                       float m22 = uintBitsToFloat(vim_pass.parts.words[part + 8u]);
                       float m30 = uintBitsToFloat(vim_pass.parts.words[part + 9u]);
                       float m31 = uintBitsToFloat(vim_pass.parts.words[part + 10u]);
                       float m32 = uintBitsToFloat(vim_pass.parts.words[part + 11u]);
                       float x = uintBitsToFloat(vim_pass.polygons.words[source]);
                       float y = uintBitsToFloat(vim_pass.polygons.words[source + 1u]);
                       float z = uintBitsToFloat(vim_pass.polygons.words[source + 2u]);
                       // Matrix4f.transformPosition, as the vertex pass evaluates it: precise keeps the
                       // compiler from fusing or reordering, so the position is Minecraft's to the bit.
                       precise float outX = m00 * x + (m10 * y + (m20 * z + m30));
                       precise float outY = m01 * x + (m11 * y + (m21 * z + m31));
                       precise float outZ = m02 * x + (m12 * y + (m22 * z + m32));
               #ifdef VIM_IN_Position
                       Position = vec3(outX, outY, outZ);
               #endif
               #ifdef VIM_IN_Color
                       uint color = vim_pass.parts.words[part + %du];
                       Color = vec4(vim_unorm8(color, 0u), vim_unorm8(color, 8u), vim_unorm8(color, 16u), vim_unorm8(color, 24u));
               #endif
               #ifdef VIM_IN_UV0
                       UV0 = vec2(uintBitsToFloat(vim_pass.polygons.words[source + 3u]), uintBitsToFloat(vim_pass.polygons.words[source + 4u]));
               #endif
               #ifdef VIM_IN_UV1
                       UV1 = vim_shorts(vim_pass.parts.words[part + %du]);
               #endif
               #ifdef VIM_IN_UV2
                       UV2 = vim_shorts(vim_pass.parts.words[part + %du]);
               #endif
               #ifdef VIM_IN_Normal
                       uint normal = vim_pass.parts.words[part + %du + vim_pass.polygons.words[polygon + %du]];
                       Normal = vec3(vim_snorm8(normal, 0u), vim_snorm8(normal, 8u), vim_snorm8(normal, 16u));
               #endif
                       vim_vertex_main();
                       gl_MeshVerticesEXT[vertex].gl_Position = vim_Position;
               %s    }
                   if (vertex < polygons * 2u) {
                       // Minecraft's quad index buffer: 0 1 2, then 2 3 0.
                       uint first = vertex / 2u * 4u;
                       gl_PrimitiveTriangleIndicesEXT[vertex] = (vertex & 1u) == 0u ? uvec3(first, first + 1u, first + 2u) : uvec3(first + 2u, first + 3u, first);
                   }
               }
               """.formatted(jobWords, ModelJobRecord.DRAW_BITS, partWords, polygonWords, cornerWords, partColor, partOverlay, partLight, partNormals, polygonDirection, stores);
    }
}