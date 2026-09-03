package com.wf.gemrender.bench;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.spike.GltfVisual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class BenchClient {
	private static final String RIVAL = System.getProperty("gemrender.bench.rival", "");
	private static final boolean ENABLED = System.getProperty("gemrender.bench.count") != null;
	private static final int COUNT = Integer.getInteger("gemrender.bench.count", 0);
	private static final int EXIT_TICKS = Integer.getInteger("gemrender.bench.exit", 0);

	private static final int FRAME_COUNT = Integer.getInteger("gemrender.bench.frame", COUNT);

	private static final float CAMERA_BACK_FACTOR = 0.6f;
	private static final int CAMERA_MIN_BACK = 8;
	private static final int CAMERA_BASE_YAW = -45;

	private static final float CAMERA_UP_FACTOR = 0.45f;
	private static final int CAMERA_PITCH = 16;

	private static final float RESET_FRACTION = 0.5f;

	@Nullable
	private static BenchDriver driver;

	private static final int ASSET_WAIT_TICKS = 200;

	private static int ticksWaitingForAsset;

	private static boolean staged;
	private static boolean reset;
	private static boolean finished;
	private static int ticks;
	private static long measurementStartNanos;

	private BenchClient() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		if (RIVAL.isEmpty()) {
			return;
		}
		event.enqueueWork(() -> {
			if (BenchGrid.isGltf() != "mcgltf".equals(RIVAL)) {
				GemRender.LOGGER.error("Bench: -Prival={} cannot draw -PbenchAsset={}. MCglTF reads glTF "
						+ "only; GeckoLib and Simple Bedrock Models read Bedrock geometry only.",
						RIVAL, BenchGrid.asset());
				return;
			}

			driver = switch (RIVAL) {
				case "mcgltf" -> {
					McgltfDriver d = new McgltfDriver(COUNT);
					d.register();
					yield d;
				}
				case "geckolib" -> new GeckolibDriver(COUNT);
				case "sbm" -> new SbmDriver(COUNT);
				default -> null;
			};
			if (driver == null) {
				GemRender.LOGGER.error("Bench: unknown -Prival={}", RIVAL);
			} else {
				GemRender.LOGGER.info("Bench: driver '{}' registered for {} copies of {}.", driver.name(),
						COUNT, BenchGrid.asset());
			}
		});
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (!enabled()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		Player player = mc.player;
		if (level == null || player == null) {
			return;
		}

		if (!staged) {
			BenchGrid.requestModel();
			if (BenchGrid.model() == null) {
				if (++ticksWaitingForAsset > ASSET_WAIT_TICKS) {
					GemRender.LOGGER.error("BENCH framework={} asset={} count={} FAILED: {} did not load "
							+ "within {} ticks. The importer's error is above this line.",
							driver == null ? "gemrender" : driver.name(), BenchGrid.assetName(), COUNT,
							BenchGrid.asset(), ASSET_WAIT_TICKS);
					finished = true;
					mc.stop();
				}
				return;
			}
			unlimitFrames(mc);

			float extent = GltfVisual.gridExtent(BenchGrid.model(), FRAME_COUNT);
			int back = Math.max(CAMERA_MIN_BACK, Math.round(extent * CAMERA_BACK_FACTOR));
			widenRenderDistance(mc, extent, back);

			stage(mc, level, extent, back);
			staged = true;
			measurementStartNanos = System.nanoTime();
			return;
		}

		ticks++;

		if (!reset && EXIT_TICKS > 0 && ticks >= EXIT_TICKS * RESET_FRACTION) {
			float extent = GltfVisual.gridExtent(BenchGrid.model(), FRAME_COUNT);
			stage(mc, level, extent, Math.max(CAMERA_MIN_BACK, Math.round(extent * CAMERA_BACK_FACTOR)));

			BenchFrameTimer.getInstance().resetRun();
			measurementStartNanos = System.nanoTime();
			reset = true;
		}

		if (EXIT_TICKS > 0 && ticks >= EXIT_TICKS && !finished) {
			finished = true;
			verdict();
			if (driver != null) {
				driver.close();
			}
			mc.stop();
		}
	}

	private static void stage(Minecraft mc, Level level, float extent, int back) {
		mc.options.pauseOnLostFocus = false;

		BlockPos origin = BenchGrid.origin(level);
		int up = Math.round(extent * CAMERA_UP_FACTOR);

		var connection = mc.player.connection;
		connection.sendCommand("gamemode spectator");
		connection.sendCommand("time set noon");
		connection.sendCommand("weather clear");
		connection.sendCommand(String.format(Locale.ROOT, "tp @s %d %d %d %d %d",
				origin.getX() - back, origin.getY() + up, origin.getZ() - back,
				CAMERA_BASE_YAW, CAMERA_PITCH));

		GemRender.LOGGER.info("Bench: staged {} x {} at {} in a {} grid, camera {} back {} up, spacing {}.",
				COUNT, BenchGrid.asset(), origin.toShortString(), BenchGrid.stride(COUNT), back, up,
				BenchGrid.spacing());
	}

	@SubscribeEvent
	public static void onFrameStart(RenderFrameEvent.Pre event) {
		if (enabled()) {
			BenchFrameTimer.getInstance().frameStart();
		}
	}

	@SubscribeEvent
	public static void onFrameEnd(RenderFrameEvent.Post event) {
		if (enabled()) {
			BenchFrameTimer.getInstance().frameEnd();
		}
	}

	@SubscribeEvent
	public static void onRenderLevel(RenderLevelStageEvent event) {
		if (driver == null || !staged || finished
				|| event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		if (!driver.load()) {
			return;
		}

		Level level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		var camera = event.getCamera().getPosition();
		BlockPos origin = BenchGrid.origin(level);
		float x = (float) (origin.getX() - camera.x);
		float y = (float) (origin.getY() - camera.y);
		float z = (float) (origin.getZ() - camera.z);

		Matrix4f pose = new Matrix4f().translation(x, y, z);
		Matrix4f modelView = new Matrix4f(event.getModelViewMatrix()).translate(x, y, z);

		float seconds = (level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false))
				/ 20.0f;
		driver.render(modelView, pose, seconds);
	}

	private static void unlimitFrames(Minecraft mc) {
		mc.options.enableVsync().set(false);
		mc.options.framerateLimit().set(260);
		mc.options.save();
	}

	private static void widenRenderDistance(Minecraft mc, float extent, int back) {
		int up = Math.round(extent * CAMERA_UP_FACTOR);
		double diagonal = Math.sqrt(2.0 * (extent + back) * (extent + back) + (double) up * up);
		int chunks = Math.clamp((int) Math.ceil(diagonal * 1.5 / 16.0), 8, 32);
		mc.options.renderDistance().set(chunks);
		mc.options.save();
		GemRender.LOGGER.info("Bench: render distance {} chunks for an extent of {} blocks at {} back.",
				chunks, Math.round(extent), back);
	}

	private static void verdict() {
		BenchFrameTimer timer = BenchFrameTimer.getInstance();
		long wallMicros = (System.nanoTime() - measurementStartNanos) / 1000L;
		long frames = timer.sampledFrames();

		long accountedPercent = wallMicros == 0L ? 0L : frames * timer.meanMicros() * 100L / wallMicros;

		GemRender.LOGGER.info(
				"BENCH framework={} asset={} count={} frames={} meanUs={} p50Us={} p95Us={} wallMs={} "
						+ "accounted={}%",
				driver == null ? "gemrender" : driver.name(),
				BenchGrid.assetName(),
				COUNT,
				frames,
				timer.meanMicros(),
				timer.percentileMicros(0.50),
				timer.percentileMicros(0.95),
				wallMicros / 1000L,
				accountedPercent);

		Minecraft mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory,
				"bench-" + (driver == null ? "gemrender" : driver.name())
						+ "-" + BenchGrid.assetName() + "-" + COUNT + ".png",
				mc.getMainRenderTarget(),
				message -> GemRender.LOGGER.info("Bench screenshot: {}", message.getString()));
	}

	private static boolean enabled() {
		return ENABLED;
	}
}
