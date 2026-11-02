package com.temotskipa.vulkanimprovement.client.vulkan.model;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderCompileChecks;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.joml.Quaternionf;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that the particle vertex pass writes what Minecraft writes. The shader's counts must be
 * those of Minecraft's arrays and vertex format, and the arrays as Minecraft fills them, read as
 * the shader reads them and with the shader's arithmetic, must give the bytes
 * {@code QuadParticleRenderState.buildLayer} gives into a {@code BufferBuilder}.
 */
public final class ParticleVertexInvariantCheck {
	private static final Path SHADER_SOURCE = Path.of("src/client/resources", ParticleGpuVertices.SHADER);
	private static final Pattern SHADER_CONSTANT = Pattern.compile("const uint (\\w+) = (\\d+)u;");
	private static final float[] CORNER_X = {1.0F, 1.0F, -1.0F, -1.0F};
	private static final float[] CORNER_Y = {-1.0F, 1.0F, 1.0F, -1.0F};
	private static final int PARTICLES = 4096;
	
	private ParticleVertexInvariantCheck() {
	}
	
	static void main() throws IOException, ReflectiveOperationException {
		String shader = Files.readString(SHADER_SOURCE).replaceAll("\\s+", " ");
		Map<String, Integer> constants = new HashMap<>();
		Matcher matcher = SHADER_CONSTANT.matcher(shader);
		while (matcher.find()) {
			constants.put(matcher.group(1), Integer.parseInt(matcher.group(2)));
		}
		require(constants.get("WORKGROUP_SIZE") == ParticleGpuVertices.WORKGROUP_SIZE && shader.contains("layout(local_size_x = " + ParticleGpuVertices.WORKGROUP_SIZE + ") in;"), "the particle pass's workgroup must be ParticleGpuVertices.WORKGROUP_SIZE");
		require(constants.get("FLOATS_PER_PARTICLE") == ParticleGpuVertices.FLOATS_PER_PARTICLE, "the shader must read as many floats per particle as Minecraft stores");
		require(constants.get("INTS_PER_PARTICLE") == ParticleGpuVertices.INTS_PER_PARTICLE, "the shader must read as many ints per particle as Minecraft stores");
		require(constants.get("QUAD_VERTICES") == ParticleGpuVertices.QUAD_VERTICES, "a particle must be a quad");
		require(constants.get("VERTEX_WORDS") * Integer.BYTES == ParticleGpuVertices.VERTEX_BYTES, "the shader must write a whole vertex");
		require(DefaultVertexFormat.PARTICLE.getVertexSize() == ParticleGpuVertices.VERTEX_BYTES, "ParticleGpuVertices.VERTEX_BYTES must be the size of DefaultVertexFormat.PARTICLE");
		require(shader.contains("uniform ParticleVertexPass { Words floats; Words ints; VertexWords vertices; uint firstVertex; uint count; } pass;"), "the particle pass's push constants must be what ParticleGpuVertices.upload pushes, in its order");
		require(ParticleGpuVertices.SHADER_SPIRV.equals(ShaderCompileChecks.precompiledResource(ParticleGpuVertices.SHADER)), "ParticleGpuVertices.SHADER_SPIRV must be where the build puts the shader's SPIR-V");
		checkVerticesAreMinecrafts();
	}
	
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
	
	private static void checkVerticesAreMinecrafts() throws ReflectiveOperationException {
		Random random = new Random(0x9A47L);
		Layer layer = Layer.TRANSLUCENT;
		QuadParticleRenderState state = new QuadParticleRenderState();
		for (int i = 0; i < PARTICLES; i++) {
			Quaternionf rotation = new Quaternionf().rotationXYZ(range(random, 4.0F), range(random, 4.0F), range(random, 4.0F));
			if (i % 3 == 0) {
				// A particle's roll is not normalized away: extract multiplies it in as it comes.
				rotation.mul(new Quaternionf(range(random, 0.1F), range(random, 0.1F), range(random, 0.1F), 1.0F));
			}
			state.add(layer, range(random, 512.0F), range(random, 256.0F), range(random, 512.0F), rotation.x, rotation.y, rotation.z, rotation.w, random.nextFloat() * 4.0F, random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextInt(), random.nextInt(256) | random.nextInt(256) << 16);
		}
		byte[] minecraft = minecraftVertices(state, layer);
		byte[] pass = passVertices(state, layer);
		require(minecraft.length == pass.length, "the particle pass must write as many vertices as Minecraft, " + pass.length + " bytes against " + minecraft.length);
		for (int at = 0; at < minecraft.length; at++) {
			require(minecraft[at] == pass[at], "vertex " + at / ParticleGpuVertices.VERTEX_BYTES + ", byte " + at % ParticleGpuVertices.VERTEX_BYTES + ": the particle pass writes " + pass[at] + ", Minecraft " + minecraft[at]);
		}
		System.out.println("Particle vertex invariant check passed: " + minecraft.length / ParticleGpuVertices.VERTEX_BYTES + " vertices of " + PARTICLES + " particles match Minecraft's.");
	}
	
	private static float range(Random random, float half) {
		return (random.nextFloat() * 2.0F - 1.0F) * half;
	}
	
	private static byte[] minecraftVertices(QuadParticleRenderState state, Layer layer) {
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 20)) {
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.PARTICLE);
			state.buildLayer(layer, builder);
			try (MeshData mesh = builder.buildOrThrow()) {
				byte[] vertices = new byte[mesh.vertexBuffer().remaining()];
				mesh.vertexBuffer().get(vertices);
				return vertices;
			}
		}
	}
	
	/**
	 * The arrays Minecraft keeps, through the shader line for line.
	 */
	private static byte[] passVertices(QuadParticleRenderState state, Layer layer) throws ReflectiveOperationException {
		Field particles = QuadParticleRenderState.class.getDeclaredField("particles");
		particles.setAccessible(true);
		Object storage = ((Map<?, ?>) particles.get(state)).get(layer);
		float[] floats = (float[]) field(storage, "floatValues");
		int[] ints = (int[]) field(storage, "intValues");
		ByteBuffer vertices = ByteBuffer.allocate(PARTICLES * ParticleGpuVertices.QUAD_VERTICES * ParticleGpuVertices.VERTEX_BYTES).order(ByteOrder.nativeOrder());
		for (int particle = 0; particle < PARTICLES; particle++) {
			int source = particle * ParticleGpuVertices.FLOATS_PER_PARTICLE;
			float x = floats[source];
			float y = floats[source + 1];
			float z = floats[source + 2];
			float qx = floats[source + 3];
			float qy = floats[source + 4];
			float qz = floats[source + 5];
			float qw = floats[source + 6];
			float scale = floats[source + 7];
			int u0 = Float.floatToRawIntBits(floats[source + 8]);
			int u1 = Float.floatToRawIntBits(floats[source + 9]);
			int v0 = Float.floatToRawIntBits(floats[source + 10]);
			int v1 = Float.floatToRawIntBits(floats[source + 11]);
			int argb = ints[particle * ParticleGpuVertices.INTS_PER_PARTICLE];
			int light = ints[particle * ParticleGpuVertices.INTS_PER_PARTICLE + 1];
			int abgr = argb & 0xFF00FF00 | argb >>> 16 & 0xFF | (argb & 0xFF) << 16;
			
			float xx = qx * qx;
			float yy = qy * qy;
			float zz = qz * qz;
			float ww = qw * qw;
			float xy = qx * qy;
			float xz = qx * qz;
			float yz = qy * qz;
			float xw = qx * qw;
			float zw = qz * qw;
			float yw = qy * qw;
			float k = 1.0F / (xx + yy + zz + ww);
			float r00 = (xx - yy - zz + ww) * k;
			float r01 = 2.0F * (xy - zw) * k;
			float r02 = 2.0F * (xz + yw) * k * 0.0F;
			float r10 = 2.0F * (xy + zw) * k;
			float r11 = (yy - xx - zz + ww) * k;
			float r12 = 2.0F * (yz - xw) * k * 0.0F;
			float r20 = 2.0F * (xz - yw) * k;
			float r21 = 2.0F * (yz + xw) * k;
			float r22 = (zz - xx - yy + ww) * k * 0.0F;
			
			for (int corner = 0; corner < ParticleGpuVertices.QUAD_VERTICES; corner++) {
				float nx = CORNER_X[corner];
				float ny = CORNER_Y[corner];
				vertices.putFloat((r00 * nx + (r01 * ny + r02)) * scale + x);
				vertices.putFloat((r10 * nx + (r11 * ny + r12)) * scale + y);
				vertices.putFloat((r20 * nx + (r21 * ny + r22)) * scale + z);
				vertices.putInt(corner < 2 ? u1 : u0);
				vertices.putInt(corner == 1 || corner == 2 ? v0 : v1);
				vertices.putInt(abgr);
				vertices.putInt(light);
			}
		}
		return vertices.array();
	}
	
	private static Object field(Object owner, String name) throws ReflectiveOperationException {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}
}