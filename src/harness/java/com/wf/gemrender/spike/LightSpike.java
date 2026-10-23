package com.wf.gemrender.spike;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.Ids;
import com.wf.gemrender.light.LightCookie;
import com.wf.gemrender.light.LightFrame;
import com.wf.gemrender.light.LightSink;
import com.wf.gemrender.light.Lights;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Locale;

/**
 * Lit room at night under a sweep of light counts; one {@code GEMRENDER-LIGHTS} line + screenshot per step.
 * Camera {@code origin + (0, 6, -14)} facing +Z (needs {@code -Pyaw=45}).
 *
 * <p>{@code overlap}: every light covers the same patch (worst case, each lit pixel loops all N).
 * {@code spread}: one patch per light. {@code behind}: all lights behind the camera (frustum cull).
 * {@code wall}: lights behind a 2-high wall at {@code z+6}, aimed at the camera side (shadowed => that floor dark).
 * {@code spam}: {@code overlap} + a 32x4x32 block toggle every tick in the beam; logs occupancy ns/block change.
 * {@code cast}: side flashlight across a stone pillar and cube (shadow shape, contact acne).
 */
public final class LightSpike implements Lights.Provider {
	public static final String SWEEP = System.getProperty("gemrender.lightsweep", "");

	public static final String LAYOUT = System.getProperty("gemrender.lightlayout", "overlap");

	public static final String KIND = System.getProperty("gemrender.lightkind", "spot");

	public static final boolean COOKIE = !"false".equalsIgnoreCase(System.getProperty("gemrender.lightcookie"));

	public static final boolean SHADOW = !"false".equalsIgnoreCase(System.getProperty("gemrender.lightshadow"));

	public static final int STEP_TICKS = Integer.getInteger("gemrender.lightstep", 160);

	private static final int WARM_TICKS = 60;

	private static final LightSpike INSTANCE = new LightSpike();

	private final int[] counts = SWEEP.isEmpty() ? new int[0]
			: Arrays.stream(SWEEP.split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();

	private BlockPos origin;
	private ClientPacketListener connection;
	private boolean spamSolid;
	private int step = -1;
	private int tick;
	private long measureStart;
	private long framesAtStart;
	private LightCookie cookie;

	public static boolean wanted() {
		return !SWEEP.isEmpty();
	}

	public static int totalTicks() {
		return INSTANCE.counts.length * STEP_TICKS;
	}

	/** After the harness's own staging: room, props, night, camera. */
	public static void stage(ClientPacketListener connection, BlockPos origin) {
		INSTANCE.origin = origin;
		INSTANCE.connection = connection;
		INSTANCE.cookie = COOKIE ? Lights.cookie(Ids.of(GemRender.MOD_ID, "textures/light/flashlight.png")) : null;
		int x = origin.getX(), y = origin.getY(), z = origin.getZ();
		command(connection, "fill %d %d %d %d %d %d minecraft:stone_bricks", x - 24, y - 1, z - 20, x + 24, y - 1, z + 20);
		command(connection, "fill %d %d %d %d %d %d minecraft:air", x - 24, y, z - 20, x + 24, y + 11, z + 17);
		command(connection, "fill %d %d %d %d %d %d minecraft:white_concrete", x - 24, y, z + 18, x + 24, y + 12, z + 18);
		command(connection, "fill %d %d %d %d %d %d minecraft:oak_log", x - 7, y, z + 6, x - 7, y + 5, z + 6);
		command(connection, "fill %d %d %d %d %d %d minecraft:oak_log", x + 7, y, z + 6, x + 7, y + 5, z + 6);
		if (LAYOUT.equals("cast")) {
			command(connection, "fill %d %d %d %d %d %d minecraft:stone", x - 2, y, z + 1, x - 2, y + 2, z + 1);
			command(connection, "setblock %d %d %d minecraft:stone", x + 2, y, z - 1);
		}
		if (LAYOUT.equals("wall")) {
			command(connection, "fill %d %d %d %d %d %d minecraft:stone", x - 24, y, z + 6, x + 24, y + 1, z + 6);
		}
		command(connection, "kill @e[type=minecraft:armor_stand]");
		command(connection, "kill @e[type=minecraft:cow]");
		command(connection, "summon minecraft:armor_stand %d %d %d {NoGravity:1b,ShowArms:1b,ArmorItems:[{id:\"minecraft:iron_boots\",count:1},{id:\"minecraft:iron_leggings\",count:1},{id:\"minecraft:iron_chestplate\",count:1},{id:\"minecraft:iron_helmet\",count:1}]}",
				x - 3, y, z + 10);
		command(connection, "summon minecraft:cow %d %d %d {NoAI:1b,Silent:1b,Rotation:[180f,0f]}", x + 3, y, z + 10);
		command(connection, "time set 18000");
		command(connection, "tp @s %d %d %d 0 20", x, y + 6, z - 14);
		Lights.register(INSTANCE);
	}

	private static void command(ClientPacketListener connection, String format, Object... args) {
		connection.sendCommand(String.format(Locale.ROOT, format, args));
	}

	/** Client tick, after staging. */
	public static void tick() {
		INSTANCE.advance();
	}

	private void advance() {
		if (origin == null || step >= counts.length) {
			return;
		}
		if (LAYOUT.equals("spam") && step >= 0) {
			spamSolid = !spamSolid;
			command(connection, "fill %d %d %d %d %d %d minecraft:%s", origin.getX() - 16, origin.getY(), origin.getZ(),
					origin.getX() + 15, origin.getY() + 3, origin.getZ() + 31, spamSolid ? "stone" : "air");
		}
		if (step < 0 || ++tick >= STEP_TICKS) {
			if (step >= 0) {
				finish();
			}
			step++;
			tick = 0;
			return;
		}
		if (tick == WARM_TICKS) {
			LightFrame.getInstance().resetRun();
			measureStart = System.nanoTime();
			framesAtStart = LightFrame.getInstance().frames();
		}
	}

	private void finish() {
		LightFrame frame = LightFrame.getInstance();
		double seconds = (System.nanoTime() - measureStart) / 1.0e9;
		long frames = frame.frames() - framesAtStart;
		int n = counts[step];
		GemRender.LOGGER.info("GEMRENDER-LIGHTS n={} layout={} kind={} cookie={} shadow={} fps={} {}", n, LAYOUT, KIND,
				cookie != null, SHADOW, String.format(Locale.ROOT, "%.1f", frames / seconds), frame.report());
		Minecraft mc = Minecraft.getInstance();
		if (LAYOUT.equals("spam")) {
			benchBlockChanged();
		}
		String name = String.format(Locale.ROOT, "lights-%s-%s-%s-n%02d.png", LAYOUT, KIND, SHADOW ? "shadow" : "lit", n);
		//? if >=26.1 {
		/*Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> {
		});
		*///?} else {
		Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
		});
		//?}
	}

	/** Occupancy hook alone: alternating stone/air over the spam region. */
	private void benchBlockChanged() {
		net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
		net.minecraft.world.level.block.state.BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int calls = 1 << 22;
		long start = 0;
		for (int pass = 0; pass < 2; pass++) {
			start = System.nanoTime();
			for (int i = 0; i < calls; i++) {
				pos.set(origin.getX() - 16 + (i & 31), origin.getY() + (i >> 10 & 3), origin.getZ() + (i >> 5 & 31));
				LightFrame.blockChanged(pos, (i >> 12 & 1) == 0 ? stone : air);
			}
		}
		GemRender.LOGGER.info("GEMRENDER-LIGHTS occupancy blockChanged {} ns/call",
				String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / (double) calls));
	}

	@Override
	public void collect(LightSink sink, float partialTick) {
		if (step < 0 || step >= counts.length) {
			return;
		}
		int n = counts[step];
		boolean spot = KIND.equals("spot") || KIND.equals("mixed");
		Vector3f up = new Vector3f(0, 1, 0);
		for (int i = 0; i < n; i++) {
			boolean thisSpot = KIND.equals("mixed") ? (i & 1) == 0 : spot;
			double lx, ly, lz;
			Vector3f direction;
			float range;
			float intensity;
			int rgb;
			switch (LAYOUT) {
				case "spread" -> {
					int side = (int) Math.ceil(Math.sqrt(n));
					double u = side == 1 ? 0.5 : (i % side) / (side - 1.0);
					double v = side == 1 ? 0.5 : (i / side) / (side - 1.0);
					lx = origin.getX() - 20 + 40 * u;
					ly = origin.getY() + 5;
					lz = origin.getZ() - 2 + 18 * v;
					direction = new Vector3f(0.0f, -1.0f, 0.15f).normalize();
					range = thisSpot ? 12.0f : 7.0f;
					intensity = 60.0f;
					rgb = java.awt.Color.HSBtoRGB(i / (float) Math.max(1, n), 0.5f, 1.0f) & 0xFFFFFF;
				}
				case "cast" -> {
					lx = origin.getX() - 12.0;
					ly = origin.getY() + 4.0 + 0.05 * i;
					lz = origin.getZ() + 0.5;
					direction = new Vector3f(1.0f, -0.35f, 0.0f).normalize();
					range = 30.0f;
					intensity = 300.0f / n;
					rgb = 0xFFF2E0;
				}
				case "wall" -> {
					lx = origin.getX() + (n == 1 ? 0.0 : -12.0 + 24.0 * i / (n - 1.0));
					ly = origin.getY() + 1.5;
					lz = origin.getZ() + 10.5;
					direction = new Vector3f(0.0f, -0.25f, -1.0f).normalize();
					range = 24.0f;
					intensity = 120.0f;
					rgb = 0xFFF2E0;
				}
				case "behind" -> {
					lx = origin.getX() + (i % 8) * 2 - 7;
					ly = origin.getY() + 6;
					lz = origin.getZ() - 40 - (i / 8) * 2;
					direction = new Vector3f(0.0f, 0.0f, -1.0f);
					range = 12.0f;
					intensity = 6.0f;
					rgb = 0xFFFFFF;
				}
				default -> {
					lx = origin.getX() + 0.05 * (i % 8);
					ly = origin.getY() + 5.5 + 0.05 * (i / 8);
					lz = origin.getZ() - 13;
					direction = new Vector3f(0.0f, (float) -Math.sin(Math.toRadians(20)),
							(float) Math.cos(Math.toRadians(20)));
					range = 40.0f;
					intensity = 600.0f / n;
					rgb = 0xFFF2E0;
				}
			}
			if (thisSpot) {
				boolean wide = !LAYOUT.equals("spread") && !LAYOUT.equals("behind");
				sink.spot(lx, ly, lz, direction, up, range, wide ? 10.0f : 12.0f, wide ? 40.0f : 28.0f, rgb, intensity,
						cookie, SHADOW);
			} else if (LAYOUT.equals("wall")) {
				sink.point(lx, ly, lz, range * 0.6f, rgb, intensity * 0.5f, SHADOW);
			} else {
				sink.point(lx, ly + 2, lz + (LAYOUT.equals("overlap") ? 17 : 8), range * 0.75f, rgb, intensity * 0.5f,
						SHADOW);
			}
		}
	}
}
