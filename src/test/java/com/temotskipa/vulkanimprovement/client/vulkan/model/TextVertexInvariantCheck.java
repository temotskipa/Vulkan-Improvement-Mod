package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompileChecks;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that the text vertex pass writes what Minecraft writes. The shader's word counts must be
 * those of the records and Minecraft's vertex format, and quads captured from Minecraft's own
 * glyphs under no pose, read as the shader reads them and with the shader's arithmetic, must give
 * the bytes those glyphs give into a {@code BufferBuilder} under the pose: plain, bold, italic,
 * with a shadow, and as an underline.
 */
public final class TextVertexInvariantCheck {
	private static final Path SHADER_SOURCE = Path.of("src/client/resources", TextGpuVertices.SHADER);
	private static final Pattern SHADER_CONSTANT = Pattern.compile("const uint (\\w+) = (\\d+)u;");
	private static final int VERTEX_BYTES = TextQuadRecord.VERTEX_WORDS * Integer.BYTES;
	private static final int RANDOM_POSES = 256;
	
	private TextVertexInvariantCheck() {
	}
	
	static void main() throws IOException {
		String shader = Files.readString(SHADER_SOURCE).replaceAll("\\s+", " ");
		Map<String, Integer> constants = new HashMap<>();
		Matcher matcher = SHADER_CONSTANT.matcher(shader);
		while (matcher.find()) {
			constants.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
		}
		require(constants.get("WORKGROUP_SIZE") == TextVertexFrame.WORKGROUP_SIZE && shader.contains("layout(local_size_x = " + TextVertexFrame.WORKGROUP_SIZE + ") in;"), "the text pass's workgroup must be TextVertexFrame.WORKGROUP_SIZE");
		require(constants.get("JOB_QUADS") == TextQuadRecord.JOB_QUADS, "a job must hold as many quads as the records say");
		require(constants.get("QUAD_VERTICES") == TextQuadRecord.QUAD_VERTICES, "a glyph must be a quad");
		require(constants.get("QUAD_WORDS") == TextQuadRecord.WORDS, "the shader's quad must be the record's");
		require(constants.get("CORNER_WORDS") == TextQuadRecord.CORNER_WORDS, "the shader's corner must be the record's");
		require(constants.get("INSTANCE_WORDS") == TextQuadRecord.INSTANCE_WORDS, "the shader's instance must be the record's");
		require(constants.get("INSTANCE_LIGHT") == TextQuadRecord.INSTANCE_LIGHT_WORD, "the shader must read the instance's light where the record has it");
		require(constants.get("JOB_WORDS") == TextQuadRecord.JOB_WORDS, "the shader's job must be the record's");
		require(constants.get("DRAW_BITS") == TextQuadRecord.DRAW_BITS, "the shader must split a job's third word where the record does");
		require(constants.get("VERTEX_WORDS") == TextQuadRecord.VERTEX_WORDS, "the shader must write a whole vertex");
		require(DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR.getVertexSize() == VERTEX_BYTES, "a text vertex must be the size of DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR");
		require(RenderTypes.text(Identifier.withDefaultNamespace("textures/font/ascii.png")).format() == DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, "Minecraft's text must be drawn in the format the pass writes");
		require(TextGpuVertices.SHADER_SPIRV.equals(ShaderCompileChecks.precompiledResource(TextGpuVertices.SHADER)), "TextGpuVertices.SHADER_SPIRV must be where the build puts the shader's SPIR-V");
		require(shader.contains("precise float outX = m00 * x + (m10 * y + (m20 * z + m30));"), "the text pass must compute x as Matrix4f.transformPosition does");
		checkVerticesAreMinecrafts();
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
	
	private static void checkVerticesAreMinecrafts() {
		Random random = new Random(0x7E47L);
		BakedSheetGlyph glyph = new BakedSheetGlyph(null, null, null, 0.125F, 0.1875F, 0.25F, 0.3125F, 0.5F, 5.5F, 1.0F, 8.0F);
		Style plain = Style.EMPTY;
		Style bold = plain.withBold(true);
		Style italic = plain.withItalic(true);
		Style both = bold.withItalic(true);
		List<TextRenderable> text = new ArrayList<>();
		float x = -40.0F;
		for (Style style : List.of(plain, bold, italic, both)) {
			for (int shadow : new int[]{0, 0x3F102030}) {
				text.add(glyph.createGlyph(x, -9.5F, 0xFF20C0A0, shadow, style, 0.5F, 1.0F));
				x += 6.0F;
			}
		}
		text.add(glyph.createEffect(-40.0F, -1.0F, 8.0F, 0.0F, 0.01F, 0x80FFFFFF, 0x20000000, 1.0F));
		text.add(glyph.createEffect(-41.0F, -11.0F, 9.0F, 1.0F, -0.01F, 0x40000000, 0, 0.0F));
		TextQuadCapture capture = new TextQuadCapture(DisplayMode.NORMAL);
		for (TextRenderable renderable : text) {
			capture.add(renderable);
		}
		require(!capture.failed(), "Minecraft's glyphs must write whole quads the capture takes");
		int checked = 0;
		for (int i = 0; i < RANDOM_POSES; i++) {
			Matrix4f pose = pose(random);
			int light = random.nextInt(256) | random.nextInt(256) << 16;
			byte[] minecraft = minecraftVertices(text, pose, light);
			byte[] pass = passVertices(capture, pose, light);
			require(minecraft.length == pass.length, "the text pass must write as many vertices as Minecraft, " + pass.length + " bytes against " + minecraft.length);
			for (int at = 0; at < minecraft.length; at++) {
				require(minecraft[at] == pass[at], "pose " + i + ", vertex " + at / VERTEX_BYTES + ", byte " + at % VERTEX_BYTES + ": the text pass writes " + pass[at] + ", Minecraft " + minecraft[at]);
			}
			checked += minecraft.length / VERTEX_BYTES;
		}
		System.out.println("Text vertex invariant check passed: " + checked + " vertices in " + RANDOM_POSES + " poses match Minecraft's.");
	}
	
	private static Matrix4f pose(Random random) {
		return new Matrix4f().translate(range(random, 2048.0F), range(random, 2048.0F), range(random, 2048.0F)).rotate(new Quaternionf().rotationXYZ(range(random, 4.0F), range(random, 4.0F), range(random, 4.0F))).scale(0.025F, -0.025F, 0.025F);
	}
	
	private static byte[] minecraftVertices(List<TextRenderable> text, Matrix4fc pose, int light) {
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(4096)) {
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR);
			for (TextRenderable renderable : text) {
				renderable.render(pose, builder, light, false);
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				byte[] vertices = new byte[mesh.vertexBuffer().remaining()];
				mesh.vertexBuffer().get(vertices);
				return vertices;
			}
		}
	}
	
	/**
	 * The captured quads, through the frame's records and the shader line for line.
	 */
	private static byte[] passVertices(TextQuadCapture capture, Matrix4fc pose, int light) {
		TextVertexFrame frame = new TextVertexFrame();
		TextVertexDraw draw = frame.addDraw(new Object());
		int instance = frame.addInstance(pose, light);
		frame.addRun(instance, 0, capture.quads(), draw);
		require(frame.jobCount() == Math.ceilDiv(capture.quads(), TextQuadRecord.JOB_QUADS), "a run must take as many jobs as it needs and no more");
		int[] quads = capture.words();
		int[] instances = frame.instanceWords();
		int[] jobs = frame.jobWords();
		ByteBuffer vertices = ByteBuffer.allocate(capture.quads() * TextQuadRecord.QUAD_VERTICES * VERTEX_BYTES).order(ByteOrder.nativeOrder());
		for (int job = 0; job < frame.jobCount(); job++) {
			for (int quadInJob = 0; quadInJob < TextQuadRecord.JOB_QUADS; quadInJob++) {
				int word = job * TextQuadRecord.JOB_WORDS;
				int drawAndQuads = jobs[word + 2];
				if (quadInJob >= drawAndQuads >>> TextQuadRecord.DRAW_BITS) {
					continue;
				}
				int part = jobs[word] * TextQuadRecord.INSTANCE_WORDS;
				int quad = (jobs[word + 1] + quadInJob) * TextQuadRecord.WORDS;
				int firstVertex = jobs[word + 3] + quadInJob * TextQuadRecord.QUAD_VERTICES;
				float m00 = Float.intBitsToFloat(instances[part]);
				float m01 = Float.intBitsToFloat(instances[part + 1]);
				float m02 = Float.intBitsToFloat(instances[part + 2]);
				float m10 = Float.intBitsToFloat(instances[part + 3]);
				float m11 = Float.intBitsToFloat(instances[part + 4]);
				float m12 = Float.intBitsToFloat(instances[part + 5]);
				float m20 = Float.intBitsToFloat(instances[part + 6]);
				float m21 = Float.intBitsToFloat(instances[part + 7]);
				float m22 = Float.intBitsToFloat(instances[part + 8]);
				float m30 = Float.intBitsToFloat(instances[part + 9]);
				float m31 = Float.intBitsToFloat(instances[part + 10]);
				float m32 = Float.intBitsToFloat(instances[part + 11]);
				int packedLight = instances[part + TextQuadRecord.INSTANCE_LIGHT_WORD];
				for (int corner = 0; corner < TextQuadRecord.QUAD_VERTICES; corner++) {
					int source = quad + corner * TextQuadRecord.CORNER_WORDS;
					float x = Float.intBitsToFloat(quads[source]);
					float y = Float.intBitsToFloat(quads[source + 1]);
					float z = Float.intBitsToFloat(quads[source + 2]);
					int target = (firstVertex + corner) * VERTEX_BYTES;
					vertices.putFloat(target, m00 * x + (m10 * y + (m20 * z + m30)));
					vertices.putFloat(target + 4, m01 * x + (m11 * y + (m21 * z + m31)));
					vertices.putFloat(target + 8, m02 * x + (m12 * y + (m22 * z + m32)));
					vertices.putInt(target + 12, quads[source + 4]);
					vertices.putInt(target + 16, quads[source + 5]);
					vertices.putInt(target + 20, packedLight);
					vertices.putInt(target + 24, quads[source + 3]);
				}
			}
		}
		return vertices.array();
	}
	
	private static float range(Random random, float half) {
		return (random.nextFloat() * 2.0F - 1.0F) * half;
	}
}