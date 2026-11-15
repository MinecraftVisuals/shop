package com.shockwaves;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class Shockwave {
	public interface CellConsumer {
		void accept(int x, int groundY, int z, float ringWeight, float fade);
	}

	private static final int NONE = Integer.MIN_VALUE;

	private final double cx, cz;
	private final int originY;
	private final float maxRadius;
	private final long startNanos = System.nanoTime();
	private final Map<Long, Integer> surfaceCache = new HashMap<>();

	public Shockwave(double cx, double cz, int originY, float maxRadius) {
		this.cx = cx;
		this.cz = cz;
		this.originY = originY;
		this.maxRadius = maxRadius;
	}

	public float progress() {
		return (System.nanoTime() - startNanos) / 1_000_000f / ShockwavesClient.DURATION_MS;
	}

	public boolean isDone() {
		return progress() >= 1f;
	}

	public void forEachCell(ClientLevel level, CellConsumer consumer) {
		float p = progress();
		if (p >= 1f) return;

		float eased = 1f - (1f - p) * (1f - p);
		float r = maxRadius * eased;
		float front = ShockwavesClient.FRONT_WIDTH;
		float trail = ShockwavesClient.TRAIL_WIDTH;
		float fade = 1f - p;

		float reach = r + Math.max(front, trail);
		int minX = (int) Math.floor(cx - reach), maxX = (int) Math.ceil(cx + reach);
		int minZ = (int) Math.floor(cz - reach), maxZ = (int) Math.ceil(cz + reach);

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
				// signed distance from the ring's leading edge: + is ahead of it, - is behind it
				double s = Math.sqrt(dx * dx + dz * dz) - r;
				if (s > front || s < -trail) continue;

				float weight = s >= 0 ? (float) (1.0 - s / front) : (float) (1.0 + s / trail);

				long key = BlockPos.asLong(x, 0, z);
				Integer cached = surfaceCache.get(key);
				int gy;
				if (cached == null) {
					gy = findSurface(level, x, z);
					surfaceCache.put(key, gy);
				} else {
					gy = cached;
				}
				if (gy == NONE) continue;

				consumer.accept(x, gy, z, weight, fade);
			}
		}
	}

	/**
	 * Finds the air space on top of the first solid block, but only within a small
	 * window around the player's feet, so trees and ledges above are ignored.
	 */
	private int findSurface(ClientLevel level, int x, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int top = originY + ShockwavesClient.STEP_UP - 1;
		int bottom = originY - ShockwavesClient.STEP_DOWN - 1;

		for (int y = top; y >= bottom; y--) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.getCollisionShape(level, pos).isEmpty()) continue;

			pos.set(x, y + 1, z);
			if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return y + 1;
		}
		return NONE;
	}
}
