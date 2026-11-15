package com.shockwaves;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

public final class ShockwaveRenderer {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(ShockwavesClient.MOD_ID, "pipeline/shockwave"))
					.withBlend(BlendFunction.TRANSLUCENT)
					.withDepthWrite(false)
					.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static BufferBuilder buffer;
	private static MappableRingBuffer vertexBuffer;

	private ShockwaveRenderer() {}

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || ShockwavesClient.WAVES.isEmpty()) return;

		PoseStack matrices = context.matrices();
		Vec3 cam = context.worldState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4fc pose = matrices.last().pose();

		int c = ShockwavesClient.COLOR;
		float red = ((c >> 16) & 255) / 255f, green = ((c >> 8) & 255) / 255f, blue = (c & 255) / 255f;
		float e = 0.003f;

		for (Shockwave wave : ShockwavesClient.WAVES) {
			wave.forEachCell(level, (x, gy, z, weight, fade) -> {
				float alpha = ShockwavesClient.ALPHA * weight * (0.25f + 0.75f * fade);
				if (alpha < 0.02f) return;
				float lift = ShockwavesClient.LIFT * weight * fade;

				if (buffer == null) {
					buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
				}
				box(pose, buffer, x - e, gy - 1 - e, z - e, x + 1 + e, gy + lift + e, z + 1 + e, red, green, blue, alpha);
			});
		}
		matrices.popPose();

		if (buffer != null) draw(mc);
	}

	private static void box(Matrix4fc m, BufferBuilder b, float x0, float y0, float z0, float x1, float y1, float z1,
							float r, float g, float bl, float a) {
		// front
		b.addVertex(m, x0, y0, z1).setColor(r, g, bl, a); b.addVertex(m, x1, y0, z1).setColor(r, g, bl, a);
		b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a); b.addVertex(m, x0, y1, z1).setColor(r, g, bl, a);
		// back
		b.addVertex(m, x1, y0, z0).setColor(r, g, bl, a); b.addVertex(m, x0, y0, z0).setColor(r, g, bl, a);
		b.addVertex(m, x0, y1, z0).setColor(r, g, bl, a); b.addVertex(m, x1, y1, z0).setColor(r, g, bl, a);
		// left
		b.addVertex(m, x0, y0, z0).setColor(r, g, bl, a); b.addVertex(m, x0, y0, z1).setColor(r, g, bl, a);
		b.addVertex(m, x0, y1, z1).setColor(r, g, bl, a); b.addVertex(m, x0, y1, z0).setColor(r, g, bl, a);
		// right
		b.addVertex(m, x1, y0, z1).setColor(r, g, bl, a); b.addVertex(m, x1, y0, z0).setColor(r, g, bl, a);
		b.addVertex(m, x1, y1, z0).setColor(r, g, bl, a); b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
		// top
		b.addVertex(m, x0, y1, z1).setColor(r, g, bl, a); b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
		b.addVertex(m, x1, y1, z0).setColor(r, g, bl, a); b.addVertex(m, x0, y1, z0).setColor(r, g, bl, a);
	}

	private static void draw(Minecraft client) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> ShockwavesClient.MOD_ID + " vertices",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(
				vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = vertexBuffer.currentBuffer();

		mesh.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
		GpuBuffer indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
		VertexFormat.IndexType indexType = mesh.drawState().indexType();

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> ShockwavesClient.MOD_ID + " render",
				client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
				client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, state.indexCount(), 1);
		}

		mesh.close();
		vertexBuffer.rotate();
		buffer = null;
	}

	public static void close() {
		ALLOCATOR.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
