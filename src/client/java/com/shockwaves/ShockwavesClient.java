package com.shockwaves;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public class ShockwavesClient implements ClientModInitializer {
	public static final String MOD_ID = "shockwaves";

	// ---- settings ----
	public static final int COLOR = 0xFF00FF;          // fuchsia
	public static final float ALPHA = 0.55f;
	public static final float MAX_RADIUS = 6f;
	public static final float RING_THICKNESS = 2f;
	public static final float LIFT = 0.6f;             // how far the wave front rises
	public static final long DURATION_MS = 1100;
	public static final int STEP_UP = 1;               // blocks above your feet it can climb
	public static final int STEP_DOWN = 2;             // blocks below your feet it can follow
	public static final boolean ON_JUMP = true;
	public static final boolean ON_LAND = true;

	public static boolean enabled = true;
	static final List<Shockwave> WAVES = new ArrayList<>();

	private static boolean wasOnGround = true;
	private static int airTicks = 0;
	private static double fallSpeed = 0;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.shockwaves.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggle.consumeClick()) {
				enabled = !enabled;
				if (client.player != null) {
					client.player.displayClientMessage(
							Component.literal("Shockwaves: " + (enabled ? "ON" : "OFF")), true);
				}
			}

			LocalPlayer p = client.player;
			if (p == null || client.level == null) {
				WAVES.clear();
				wasOnGround = true;
				return;
			}

			boolean ground = p.onGround();
			if (!ground) {
				airTicks++;
				fallSpeed = Math.max(fallSpeed, -p.getDeltaMovement().y);
			}

			if (enabled) {
				if (ON_JUMP && wasOnGround && !ground && p.getDeltaMovement().y > 0) {
					spawn(p, 0.6f);
				}
				if (ON_LAND && !wasOnGround && ground && airTicks >= 3) {
					spawn(p, (float) Math.min(1.0, 0.5 + fallSpeed));
				}
			}

			if (ground) {
				airTicks = 0;
				fallSpeed = 0;
			}
			wasOnGround = ground;
			WAVES.removeIf(Shockwave::isDone);
		});

		WorldRenderEvents.BEFORE_TRANSLUCENT.register(ShockwaveRenderer::render);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ShockwaveRenderer.close());
	}

	private static void spawn(LocalPlayer p, float strength) {
		WAVES.add(new Shockwave(p.getX(), p.getZ(), Mth.floor(p.getY() + 0.05), MAX_RADIUS * strength));
	}
}
