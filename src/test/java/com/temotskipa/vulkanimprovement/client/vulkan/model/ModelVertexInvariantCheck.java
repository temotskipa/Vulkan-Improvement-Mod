package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.vertex.*;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompileChecks;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that the model vertex pass writes what Minecraft writes. The shader's word counts must
 * be the records' sizes and offsets, and the records, read as the shader reads them and with the
 * shader's arithmetic, must give the bytes Minecraft's own {@code ModelPart.Cube.compile} gives
 * into a {@code BufferBuilder}, for cubes of every shape Minecraft builds and for poses that
 * mirror, scale and move far from the origin.
 */
public final class ModelVertexInvariantCheck {
    private static final Path SHADER_SOURCE = Path.of("src/client/resources", ModelGpuVertices.SHADER);
    private static final Pattern SHADER_CONSTANT = Pattern.compile("const uint (\\w+) = (\\d+)u;");
    private static final Pattern WORKGROUP = Pattern.compile("layout\\(local_size_x = (\\d+)\\) in;");
    /**
     * The bytes of a vertex Minecraft writes: all but the last, which follows the three bytes of
     * the normal and which {@code BufferBuilder} leaves as it finds it.
     */
    private static final int WRITTEN_VERTEX_BYTES = 35;
    private static final int RANDOM_POSES = 256;
    
    private ModelVertexInvariantCheck() {
    }
    
    static void main() throws IOException {
        String shader = Files.readString(SHADER_SOURCE).replaceAll("\\s+", " ");
        Map<String, Integer> constants = shaderConstants(shader);
        checkShaderReadsTheRecords(shader, constants);
        checkEntityFormat();
        checkPrecompiledShaderName();
        checkVerticesAreMinecrafts(constants);
    }
    
    private static Map<String, Integer> shaderConstants(String shader) {
        Map<String, Integer> constants = new HashMap<>();
        Matcher matcher = SHADER_CONSTANT.matcher(shader);
        while (matcher.find()) {
            constants.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
        return constants;
    }
    
    private static int constant(Map<String, Integer> constants, String name) {
        Integer value = constants.get(name);
        require(value != null, "model_vertices.comp.glsl must declare " + name);
        return value;
    }
    
    private static void requireConstant(Map<String, Integer> constants, String name, long expected) {
        require(constant(constants, name) == expected, "model_vertices.comp.glsl " + name + " must be " + expected + ", is " + constants.get(name));
    }
    
    private static void checkShaderReadsTheRecords(String shader, Map<String, Integer> constants) {
        Matcher workgroup = WORKGROUP.matcher(shader);
        require(workgroup.find() && Integer.parseInt(workgroup.group(1)) == ModelVertexFrame.WORKGROUP_SIZE, "the model vertex pass's workgroup must be ModelVertexFrame.WORKGROUP_SIZE wide");
        requireConstant(constants, "WORKGROUP_SIZE", ModelVertexFrame.WORKGROUP_SIZE);
        requireConstant(constants, "MAX_CUBE_POLYGONS", ModelGpuRecords.MAX_CUBE_POLYGONS);
        requireConstant(constants, "POLYGON_VERTICES", ModelGpuRecords.POLYGON_VERTICES);
        
        requireConstant(constants, "POLYGON_WORDS", ModelPolygonRecord.BYTES / Integer.BYTES);
        requireConstant(constants, "CORNER_WORDS", ModelPolygonRecord.CORNER_BYTES / Integer.BYTES);
        requireConstant(constants, "POLYGON_DIRECTION", ModelPolygonRecord.DIRECTION / Integer.BYTES);
        // The shader reads a corner's words in order from the polygon's first word.
        require(ModelPolygonRecord.CORNERS == 0L, "a polygon record must start with its corners");
        require(ModelPolygonRecord.CORNER_X == 0L && ModelPolygonRecord.CORNER_Y == 4L && ModelPolygonRecord.CORNER_Z == 8L && ModelPolygonRecord.CORNER_U == 12L && ModelPolygonRecord.CORNER_V == 16L, "a corner record must be x, y, z, u, v");
        
        requireConstant(constants, "PART_WORDS", ModelPartRecord.BYTES / Integer.BYTES);
        requireConstant(constants, "PART_NORMALS", ModelPartRecord.NORMALS / Integer.BYTES);
        requireConstant(constants, "PART_COLOR", ModelPartRecord.COLOR / Integer.BYTES);
        requireConstant(constants, "PART_OVERLAY", ModelPartRecord.OVERLAY / Integer.BYTES);
        requireConstant(constants, "PART_LIGHT", ModelPartRecord.LIGHT / Integer.BYTES);
        require(ModelPartRecord.POSE == 0L, "a part record must start with its pose");
        
        requireConstant(constants, "JOB_WORDS", ModelJobRecord.BYTES / Integer.BYTES);
        requireConstant(constants, "DRAW_BITS", ModelJobRecord.DRAW_BITS);
        require(ModelJobRecord.PART == 0L && ModelJobRecord.FIRST_POLYGON == 4L && ModelJobRecord.DRAW_AND_POLYGONS == 8L && ModelJobRecord.FIRST_VERTEX == 12L, "a job record must be part, first polygon, draw and polygons, first vertex");
        
        requireConstant(constants, "VERTEX_WORDS", ModelEntityVertexRecord.BYTES / Integer.BYTES);
        
        // ModelVertexFrame.write fills the push constants in this order: five addresses, then the
        // first job and the job count.
        String pushConstants = "uniform ModelVertexPass { Words polygons; Words parts; Words jobs; Words drawBases; VertexWords vertices; uint firstJob; uint jobCount; } pass;";
        require(shader.contains(pushConstants), "the model vertex pass's push constants must be the addresses ModelVertexFrame.write pushes, in its order");
        long addresses = pushConstants.split("Words ", -1).length - 1L;
        long counts = pushConstants.split("uint ", -1).length - 1L;
        require(ModelVertexFrame.PUSH_CONSTANT_BYTES == addresses * Long.BYTES + counts * Integer.BYTES, "ModelVertexFrame.PUSH_CONSTANT_BYTES must hold the pass's addresses and counts");
        
        // Matrix4f.transformPosition, as JOML evaluates it without fused multiply-adds, which the
        // emulation below repeats.
        require(shader.contains("precise float outX = m00 * x + (m10 * y + (m20 * z + m30));"), "the model vertex pass must compute x as Matrix4f.transformPosition does, precisely");
        require(shader.contains("precise float outY = m01 * x + (m11 * y + (m21 * z + m31));"), "the model vertex pass must compute y as Matrix4f.transformPosition does, precisely");
        require(shader.contains("precise float outZ = m02 * x + (m12 * y + (m22 * z + m32));"), "the model vertex pass must compute z as Matrix4f.transformPosition does, precisely");
    }
    
    /**
     * The shader writes a vertex as nine words: position, color, texture coordinate, overlay,
     * light, normal.
     */
    private static void checkEntityFormat() {
        VertexFormat format = DefaultVertexFormat.ENTITY;
        require(format.getVertexSize() == ModelEntityVertexRecord.BYTES, "DefaultVertexFormat.ENTITY must be " + ModelEntityVertexRecord.BYTES + " bytes, is " + format.getVertexSize());
        requireOffset(format, DefaultVertexFormat.POSITION_SEMANTIC_NAME, 0);
        requireOffset(format, DefaultVertexFormat.COLOR_SEMANTIC_NAME, 12);
        requireOffset(format, DefaultVertexFormat.UV0_SEMANTIC_NAME, 16);
        requireOffset(format, DefaultVertexFormat.UV1_SEMANTIC_NAME, 24);
        requireOffset(format, DefaultVertexFormat.UV2_SEMANTIC_NAME, 28);
        requireOffset(format, DefaultVertexFormat.NORMAL_SEMANTIC_NAME, 32);
        require(format.getElements().size() == 6, "DefaultVertexFormat.ENTITY must have the six elements the model vertex pass writes, has " + format.getElements());
    }
    
    private static void requireOffset(VertexFormat format, String element, int offset) {
        VertexFormatElement formatElement = format.getElement(element);
        require(formatElement != null && formatElement.offset() == offset, "DefaultVertexFormat.ENTITY " + element + " must be at byte " + offset);
    }
    
    private static void checkPrecompiledShaderName() {
        String expected = ShaderCompileChecks.precompiledResource(ModelGpuVertices.SHADER);
        require(ModelGpuVertices.SHADER_SPIRV.equals(expected), "ModelGpuVertices.SHADER_SPIRV must be where the build puts the shader's SPIR-V: " + expected);
    }
    
    private static void checkVerticesAreMinecrafts(Map<String, Integer> constants) {
        List<ModelPart.Cube> cubes = cubes();
        Random random = new Random(0x5EEDL);
        List<PoseStack.Pose> poses = new ArrayList<>(fixedPoses());
        for (int i = 0; i < RANDOM_POSES; i++) {
            poses.add(randomPose(random));
        }
        int checked = 0;
        for (int poseIndex = 0; poseIndex < poses.size(); poseIndex++) {
            PoseStack.Pose pose = poses.get(poseIndex);
            int color = random.nextInt();
            int overlay = random.nextInt(16) | random.nextInt(16) << 16;
            int light = random.nextInt(256) | random.nextInt(256) << 16;
            byte[] minecraft = minecraftVertices(cubes, pose, light, overlay, color);
            for (boolean laidOut : new boolean[]{false, true}) {
                String records = laidOut ? " (from the part's layout)" : " (cube by cube)";
                byte[] pass = passVertices(constants, cubes, pose, light, overlay, color, laidOut);
                require(minecraft.length == pass.length, "pose " + poseIndex + records + ": the model vertex pass must write as many vertices as Minecraft, " + pass.length / ModelEntityVertexRecord.BYTES + " against " + minecraft.length / ModelEntityVertexRecord.BYTES);
                for (int vertex = 0; vertex < minecraft.length / ModelEntityVertexRecord.BYTES; vertex++) {
                    for (int b = 0; b < WRITTEN_VERTEX_BYTES; b++) {
                        int at = vertex * (int) ModelEntityVertexRecord.BYTES + b;
                        require(minecraft[at] == pass[at], "pose " + poseIndex + records + ", vertex " + vertex + ", byte " + b + ": the model vertex pass writes " + pass[at] + " where Minecraft writes " + minecraft[at]);
                    }
                    checked++;
                }
            }
        }
        require(checked > 0, "the vertex check must compare vertices");
        System.out.println("Model vertex invariant check passed: " + checked + " vertices of " + cubes.size() + " cubes in " + poses.size() + " poses, recorded cube by cube and from part layouts, match Minecraft's.");
    }
    
    /**
     * Cubes as Minecraft's model layers build them: plain, grown, mirrored, flat, and with some
     * faces left out.
     */
    private static List<ModelPart.Cube> cubes() {
        Set<Direction> all = EnumSet.allOf(Direction.class);
        return List.of(new ModelPart.Cube(0, 0, -4.0F, -8.0F, -2.0F, 8.0F, 12.0F, 4.0F, 0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, all), new ModelPart.Cube(16, 16, -3.5F, 0.0F, -2.0F, 7.0F, 5.0F, 4.0F, 0.25F, 0.5F, 0.25F, false, 64.0F, 32.0F, all), new ModelPart.Cube(40, 16, -1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0.0F, 0.0F, 0.0F, true, 64.0F, 64.0F, all), new ModelPart.Cube(0, 32, -2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 6.0F, 0.0F, 0.0F, 0.0F, false, 128.0F, 64.0F, all), new ModelPart.Cube(3, 7, -0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, -0.1F, 0.2F, 0.0F, true, 16.0F, 16.0F, EnumSet.of(Direction.UP, Direction.NORTH, Direction.WEST)), new ModelPart.Cube(24, 0, -6.0F, 1.0F, -6.0F, 12.0F, 2.0F, 12.0F, 0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, EnumSet.of(Direction.DOWN)));
    }
    
    private static List<PoseStack.Pose> fixedPoses() {
        List<PoseStack.Pose> poses = new ArrayList<>();
        poses.add(new PoseStack().last());
        PoseStack entity = new PoseStack();
        entity.translate(-12.25F, 64.0F, 7.5F);
        entity.rotate(new Quaternionf().rotationY((float) Math.toRadians(-37.0)));
        // As LivingEntityRenderer turns a model to face the camera and puts it on its feet.
        entity.scale(-1.0F, -1.0F, 1.0F);
        entity.translate(0.0F, -1.501F, 0.0F);
        entity.scale(0.9375F, 0.9375F, 0.9375F);
        poses.add(entity.last());
        PoseStack far = new PoseStack();
        far.translate(29_999_872.5F, -63.75F, -29_999_871.25F);
        far.rotate(new Quaternionf().rotationXYZ(0.3F, 1.1F, -0.7F));
        poses.add(far.last());
        PoseStack squashed = new PoseStack();
        squashed.scale(0.0625F, 3.0F, -0.5F);
        poses.add(squashed.last());
        return poses;
    }
    
    private static PoseStack.Pose randomPose(Random random) {
        PoseStack poses = new PoseStack();
        poses.translate(range(random, 2048.0F), range(random, 2048.0F), range(random, 2048.0F));
        poses.rotate(new Quaternionf().rotationXYZ(range(random, 4.0F), range(random, 4.0F), range(random, 4.0F)));
        if (random.nextBoolean()) {
            poses.scale(scale(random), scale(random), scale(random));
        }
        poses.translate(range(random, 2.0F), range(random, 2.0F), range(random, 2.0F));
        poses.rotate(new Quaternionf().rotationXYZ(range(random, 4.0F), range(random, 4.0F), range(random, 4.0F)));
        return poses.last();
    }
    
    private static float range(Random random, float half) {
        return (random.nextFloat() * 2.0F - 1.0F) * half;
    }
    
    private static float scale(Random random) {
        float scale = 0.05F + random.nextFloat() * 2.0F;
        return random.nextInt(4) == 0 ? -scale : scale;
    }
    
    /**
     * The part's vertices as Minecraft writes them.
     */
    private static byte[] minecraftVertices(List<ModelPart.Cube> cubes, PoseStack.Pose pose, int light, int overlay, int color) {
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(4096)) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
            for (ModelPart.Cube cube : cubes) {
                cube.compile(pose, builder, light, overlay, color);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                byte[] vertices = new byte[mesh.vertexBuffer().remaining()];
                mesh.vertexBuffer().get(vertices);
                return vertices;
            }
        }
    }
    
    /**
     * The part's vertices as the model vertex pass writes them: the records the GPU path makes,
     * cube by cube or from the part's layout, read as the shader reads them, with its arithmetic.
     */
    private static byte[] passVertices(Map<String, Integer> constants, List<ModelPart.Cube> cubes, PoseStack.Pose pose, int light, int overlay, int color, boolean laidOut) {
        ModelVertexFrame frame = new ModelVertexFrame();
        try (Arena arena = Arena.ofConfined()) {
            int polygonCount = cubes.stream().mapToInt(cube -> cube.polygons.length).sum();
            MemorySegment polygons = arena.allocate(polygonCount * ModelPolygonRecord.BYTES, 16L);
            // The check writes no frame, so the draw is drawn with no render type.
            ModelVertexDraw draw = frame.addDraw(new Object(), null);
            int part = frame.addPart(pose, color, overlay, light);
            int directions = 0;
            int firstPolygon = 0;
            int[] firstPolygons = new int[cubes.size()];
            int[] polygonCounts = new int[cubes.size()];
            for (int cubeIndex = 0; cubeIndex < cubes.size(); cubeIndex++) {
                ModelPart.Cube cube = cubes.get(cubeIndex);
                for (int i = 0; i < cube.polygons.length; i++) {
                    ModelPart.Polygon polygon = cube.polygons[i];
                    int direction = ModelCubeStore.direction(polygon.normal());
                    require(direction >= 0 && polygon.vertices().length == ModelGpuRecords.POLYGON_VERTICES, "a vanilla cube's faces must be quads facing one of the six directions");
                    directions |= 1 << direction;
                    ModelCubeStore.writePolygon(polygons, (firstPolygon + i) * ModelPolygonRecord.BYTES, polygon);
                }
                if (!laidOut) {
                    frame.addJob(part, firstPolygon, cube.polygons.length, draw);
                }
                firstPolygons[cubeIndex] = firstPolygon;
                polygonCounts[cubeIndex] = cube.polygons.length;
                firstPolygon += cube.polygons.length;
            }
            if (laidOut) {
                frame.addJobs(part, ModelPartCubes.laidOut(0, firstPolygons, polygonCounts, directions), draw);
            }
            frame.writeNormals(part, pose, directions, new Vector3f());
            MemorySegment vertices = arena.allocate(frame.vertexCount() * ModelEntityVertexRecord.BYTES, 16L);
            int[] drawBases = {0};
            for (int job = 0; job < frame.jobCount(); job++) {
                for (int polygonInCube = 0; polygonInCube < constant(constants, "MAX_CUBE_POLYGONS"); polygonInCube++) {
                    writePolygonVertices(constants, polygons, frame.partRecords(), frame.jobRecords(), drawBases, vertices, job, polygonInCube);
                }
            }
            return vertices.toArray(ValueLayout.JAVA_BYTE);
        } finally {
            frame.close();
        }
    }
    
    /**
     * One invocation of {@code model_vertices.comp.glsl}, line for line.
     */
    private static void writePolygonVertices(Map<String, Integer> constants, MemorySegment polygons, MemorySegment parts, MemorySegment jobs, int[] drawBases, MemorySegment vertices, int jobIndex, int polygonInCube) {
        int job = jobIndex * constant(constants, "JOB_WORDS");
        int drawAndPolygons = word(jobs, job + 2);
        int drawBits = constant(constants, "DRAW_BITS");
        if (polygonInCube >= drawAndPolygons >>> drawBits) {
            return;
        }
        int part = word(jobs, job) * constant(constants, "PART_WORDS");
        int polygon = (word(jobs, job + 1) + polygonInCube) * constant(constants, "POLYGON_WORDS");
        int draw = drawAndPolygons & (1 << drawBits) - 1;
        int firstVertex = drawBases[draw] + word(jobs, job + 3) + polygonInCube * constant(constants, "POLYGON_VERTICES");
        
        float m00 = Float.intBitsToFloat(word(parts, part));
        float m01 = Float.intBitsToFloat(word(parts, part + 1));
        float m02 = Float.intBitsToFloat(word(parts, part + 2));
        float m10 = Float.intBitsToFloat(word(parts, part + 3));
        float m11 = Float.intBitsToFloat(word(parts, part + 4));
        float m12 = Float.intBitsToFloat(word(parts, part + 5));
        float m20 = Float.intBitsToFloat(word(parts, part + 6));
        float m21 = Float.intBitsToFloat(word(parts, part + 7));
        float m22 = Float.intBitsToFloat(word(parts, part + 8));
        float m30 = Float.intBitsToFloat(word(parts, part + 9));
        float m31 = Float.intBitsToFloat(word(parts, part + 10));
        float m32 = Float.intBitsToFloat(word(parts, part + 11));
        int normal = word(parts, part + constant(constants, "PART_NORMALS") + word(polygons, polygon + constant(constants, "POLYGON_DIRECTION")));
        int color = word(parts, part + constant(constants, "PART_COLOR"));
        int overlay = word(parts, part + constant(constants, "PART_OVERLAY"));
        int light = word(parts, part + constant(constants, "PART_LIGHT"));
        
        for (int corner = 0; corner < constant(constants, "POLYGON_VERTICES"); corner++) {
            int source = polygon + corner * constant(constants, "CORNER_WORDS");
            float x = Float.intBitsToFloat(word(polygons, source));
            float y = Float.intBitsToFloat(word(polygons, source + 1));
            float z = Float.intBitsToFloat(word(polygons, source + 2));
            float outX = m00 * x + (m10 * y + (m20 * z + m30));
            float outY = m01 * x + (m11 * y + (m21 * z + m31));
            float outZ = m02 * x + (m12 * y + (m22 * z + m32));
            int target = (firstVertex + corner) * constant(constants, "VERTEX_WORDS");
            setWord(vertices, target, Float.floatToRawIntBits(outX));
            setWord(vertices, target + 1, Float.floatToRawIntBits(outY));
            setWord(vertices, target + 2, Float.floatToRawIntBits(outZ));
            setWord(vertices, target + 3, color);
            setWord(vertices, target + 4, word(polygons, source + 3));
            setWord(vertices, target + 5, word(polygons, source + 4));
            setWord(vertices, target + 6, overlay);
            setWord(vertices, target + 7, light);
            setWord(vertices, target + 8, normal);
        }
    }
    
    private static int word(MemorySegment words, int index) {
        return words.get(ModelGpuRecords.INT, (long) index * Integer.BYTES);
    }
    
    private static void setWord(MemorySegment words, int index, int value) {
        words.set(ModelGpuRecords.INT, (long) index * Integer.BYTES, value);
    }
    
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}