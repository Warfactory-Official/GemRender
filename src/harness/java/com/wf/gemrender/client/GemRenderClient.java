package com.wf.gemrender.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.debug.GemRenderRenderDoc;
import com.wf.gemrender.debug.SamplerProbe;
import com.wf.gemrender.render.BoneBuffer;
import com.wf.gemrender.render.FrameCost;
import com.wf.gemrender.render.MorphBuffer;
import com.wf.gemrender.render.PoseCache;
import com.wf.gemrender.render.PoseLod;
import com.wf.gemrender.render.SkinnedCubeMesh;
import com.wf.gemrender.spike.GltfEffect;
import com.wf.gemrender.spike.GltfVisual;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.particle.ParticleClock;
import com.wf.gemrender.spike.ParticleSpikeEffect;
import com.wf.gemrender.spike.PartsEffect;
import com.wf.gemrender.spike.SpikeAssets;
import com.wf.gemrender.spike.SpikeClock;
import com.wf.gemrender.spike.SpikeEffect;
import com.wf.gemrender.spike.SpikeVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class GemRenderClient {
	private GemRenderClient() {
	}

	private static final int AUTO_SPIKE = Integer.getInteger("gemrender.autospike", 0);

	private static final int AUTO_PARTICLES = Integer.getInteger("gemrender.autoparticles", 0);

	private static final float PARTICLE_EXTENT = 10.0f;

	private static final int AUTO_EXIT_TICKS = Integer.getInteger("gemrender.autoexit", 0);

	private static final int AUTO_RADAR = Integer.getInteger("gemrender.autoradar", 0);

	private static final int AUTO_RIG = Integer.getInteger("gemrender.autorig", 0);

	private static final int AUTO_MORPH = Integer.getInteger("gemrender.automorph", 0);

	private static final int AUTO_GLASS = Integer.getInteger("gemrender.autoglass", 0);

	private static final int AUTO_PBR = Integer.getInteger("gemrender.autopbr", 0);

	private static final int AUTO_PYLON = Integer.getInteger("gemrender.autopylon", 0);

	private static final int AUTO_PYLON_GLTF = Integer.getInteger("gemrender.autopylongltf", 0);

	private static final int AUTO_VEHICLE = Integer.getInteger("gemrender.autovehicle", 0);

	private static final int AUTO_VEHICLE_PARTS = Integer.getInteger("gemrender.autovehicleparts", 0);

	private static final String AUTO_VEHICLE_NAME =
			System.getProperty("gemrender.autovehiclename", "m1a2");

	private static final int AUTO_WATER = Integer.getInteger("gemrender.autowater", 0);

	private static final int AUTO_WATER_COLUMN = Integer.getInteger("gemrender.autowatercolumn", 0);

	private static final boolean AUTO_CONTROLS = Boolean.getBoolean("gemrender.autocontrols");

	private static final String AUTO_GRAPHICS = System.getProperty("gemrender.autographics", "");

	public static final String VERDICT_PREFIX = "GEMRENDER-SPIKE-VERDICT";

	private static final int SCENE_ALTITUDE = 60;

	private static final float CAMERA_BACK_FACTOR = 0.6f;
	private static final float CAMERA_UP_FACTOR = 0.25f;
	private static final int CAMERA_MIN_BACK = 8;

	private static final int AUTO_YAW = Integer.getInteger("gemrender.autoyaw", 0);

	private static final int AUTO_PITCH = Integer.getInteger("gemrender.autopitch", 20);

	private static final int CAMERA_BASE_YAW = -45;

	private static final boolean AUTO_SYNC = Boolean.getBoolean("gemrender.autosync");

	private static final float AUTO_SPIN =
			Float.parseFloat(System.getProperty("gemrender.autospin", "0"));

	private static final int AUTO_SPIN_NODE = Integer.getInteger("gemrender.autospinnode", -1);

	private static final String AUTO_SPIN_BONE = System.getProperty("gemrender.autospinbone", "");

	private static final float AUTO_SPIN_DUTY =
			Float.parseFloat(System.getProperty("gemrender.autospinduty", "1"));

	private static final int AUTO_CAPTURE = Integer.getInteger("gemrender.autocapture", 0);

	private static boolean captureRequested;

	private static final int AUTO_RELOAD = Integer.getInteger("gemrender.autoreload", 0);

	private static boolean autoSpikeDone;
	private static int ticksSinceSpike;

	private static final int ASSET_WAIT_TICKS = 200;

	private static int ticksWaitingForAsset;

	private static BlockPos sceneOrigin;

	private static boolean reloadRequested;
	private static int morphFloatsBeforeReload = -1;

	private static boolean autoIsParts() {
		return AUTO_VEHICLE_PARTS > 0;
	}

	private static void reportLoadState() {
		ResourceLocation asset = autoAsset();

		if (AUTO_PARTICLES > 0) {
			ParticleBuffer particles = ParticleBuffer.getInstance();
			int alive = particles.aliveCount(ParticleClock.seconds());
			SpikeHud.status("want particles  x" + AUTO_PARTICLES + "  buffer="
					+ (particles.isInitialized() ? "live" : "NOT UPLOADED") + "  slots="
					+ particles.capacitySlots() + "  alive=" + alive, !particles.isInitialized());
			return;
		}

		if (asset == null && AUTO_SPIKE <= 0) {
			SpikeHud.status("", false);
			return;
		}

		int wanted = autoCount();
		StringBuilder status = new StringBuilder("want ").append(asset == null ? "cubes" : asset)
				.append("  x")
				.append(wanted);

		boolean loaded;
		if (asset == null) {
			loaded = true;
		} else if (autoIsParts()) {
			com.wf.gemrender.gltf.GemRenderPartsModel parts = SpikeAssets.parts(asset);
			loaded = parts != null;
			status.append("  loaded=")
					.append(loaded ? "yes" : "NO")
					.append("  parts=")
					.append(parts == null ? 0 : parts.partCount());
		} else {
			loaded = SpikeAssets.model(asset) != null;
			status.append("  loaded=")
					.append(loaded ? "yes" : "NO");
		}

		if (!autoIsParts()) {
			status.append("  matrices=")
					.append(BoneBuffer.getInstance()
							.lastUploadedCount())
					.append("  poses=")
					.append(PoseCache.getInstance()
							.evaluationsLastFrame())
					.append('/')
					.append(PoseCache.getInstance()
							.requestsLastFrame())
					.append(" (last frame)");
		}

		if (!loaded) {
			status.append("   <- MODEL DID NOT LOAD");
		}
		SpikeHud.status(status.toString(), !loaded);
	}

	@Nullable
	private static org.joml.Vector4fc autoSphere() {
		ResourceLocation asset = autoAsset();
		if (asset == null) {
			return null;
		}
		if (autoIsParts()) {
			com.wf.gemrender.gltf.GemRenderPartsModel parts = SpikeAssets.parts(asset);
			return parts == null ? null : parts.boundingSphere();
		}
		com.wf.gemrender.gltf.GemRenderGltfModel model = SpikeAssets.model(asset);
		return model == null ? null : model.model()
				.boundingSphere();
	}

	private static ResourceLocation autoAsset() {
		if (AUTO_VEHICLE_PARTS > 0 || AUTO_VEHICLE > 0) {
			return SpikeAssets.vehicle(AUTO_VEHICLE_NAME);
		}
		if (AUTO_PYLON_GLTF > 0) {
			return SpikeAssets.PYLON_GLTF;
		}
		if (AUTO_PYLON > 0) {
			return SpikeAssets.PYLON;
		}
		if (AUTO_PBR > 0) {
			return SpikeAssets.PBR;
		}
		if (AUTO_GLASS > 0) {
			return SpikeAssets.GLASS;
		}
		if (AUTO_MORPH > 0) {
			return SpikeAssets.MORPH;
		}
		if (AUTO_RIG > 0) {
			return SpikeAssets.RIG;
		}
		return AUTO_RADAR > 0 ? SpikeAssets.RADAR : null;
	}

	private static int autoCount() {
		if (AUTO_VEHICLE_PARTS > 0) {
			return AUTO_VEHICLE_PARTS;
		}
		if (AUTO_VEHICLE > 0) {
			return AUTO_VEHICLE;
		}
		if (AUTO_PYLON_GLTF > 0) {
			return AUTO_PYLON_GLTF;
		}
		if (AUTO_PYLON > 0) {
			return AUTO_PYLON;
		}
		if (AUTO_PBR > 0) {
			return AUTO_PBR;
		}
		if (AUTO_GLASS > 0) {
			return AUTO_GLASS;
		}
		if (AUTO_MORPH > 0) {
			return AUTO_MORPH;
		}
		if (AUTO_RIG > 0) {
			return AUTO_RIG;
		}
		if (AUTO_RADAR > 0) {
			return AUTO_RADAR;
		}
		return AUTO_PARTICLES > 0 ? AUTO_PARTICLES : AUTO_SPIKE;
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (autoCount() <= 0) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (!autoSpikeDone) {
			Level level = mc.level;
			if (level == null || mc.player == null || !VisualizationManager.supportsVisualization(level)) {
				return;
			}

			ResourceLocation pending = autoAsset();
			if (pending != null && autoSphere() == null) {
				if (++ticksWaitingForAsset < ASSET_WAIT_TICKS) {
					return;
				}
				GemRender.LOGGER.warn("Auto-spike: {} has not loaded after {} ticks; staging anyway, so the "
						+ "camera framing will not match a run where it did load.", pending, ASSET_WAIT_TICKS);
			}

			autoSpikeDone = true;

			mc.options.pauseOnLostFocus = false;

			applyGraphicsMode(mc);

			BlockPos origin = level.getSharedSpawnPos()
					.offset(3, SCENE_ALTITUDE, 3);
			sceneOrigin = origin;
			ResourceLocation asset = autoAsset();
			queueScene(level, origin);

			var connection = mc.player.connection;
			connection.sendCommand("gamemode spectator");
			connection.sendCommand("time set noon");
			connection.sendCommand("weather clear");

			float extent = asset != null
					? GltfVisual.gridExtentOf(autoSphere(), autoCount())
					: AUTO_PARTICLES > 0 ? PARTICLE_EXTENT : SpikeVisual.gridExtent(AUTO_SPIKE);
			int back = Math.max(CAMERA_MIN_BACK, Math.round(extent * CAMERA_BACK_FACTOR));
			int up = Math.max(1, Math.round(extent * CAMERA_UP_FACTOR));

			clearStagingArea(connection, origin, extent);

			if (AUTO_WATER > 0) {
				buildWaterBasin(connection, origin, extent);
			}

			if (AUTO_WATER_COLUMN > 0) {
				buildWaterColumn(mc, connection, origin, asset, extent, back);
			}

			if (AUTO_CONTROLS) {
				buildVanillaControls(connection, origin, asset);
			}

			connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %d %d %d %d %d",
					origin.getX() - back, origin.getY() + up, origin.getZ() - back,
					CAMERA_BASE_YAW + AUTO_YAW, AUTO_PITCH));

			describeRun(asset);

			GemRender.LOGGER.info("Auto-spike: queued {} x {} at {}",
					autoCount(), asset != null ? asset : "skinned cubes", origin.toShortString());
			return;
		}

		if (AUTO_RELOAD > 0 && !reloadRequested && ticksSinceSpike >= AUTO_RELOAD) {
			reloadRequested = true;
			reloadResources(mc);
		}

		if (AUTO_CAPTURE > 0 && !captureRequested && ticksSinceSpike >= AUTO_CAPTURE) {
			captureRequested = true;

			if (GemRenderRenderDoc.triggerCapture()) {
				GemRender.LOGGER.info("Auto-spike: RenderDoc capture requested at tick {}", ticksSinceSpike);
			} else {
				GemRender.LOGGER.warn("Auto-spike: -PspikeCapture asked for a capture at tick {} but RenderDoc "
						+ "is not attached. Re-run with -PwithRenderDoc.", ticksSinceSpike);
			}
		}

		if (AUTO_EXIT_TICKS <= 0) {
			return;
		}

		if (mc.player != null) {
			float yaw = CAMERA_BASE_YAW + AUTO_YAW;
			mc.player.setYRot(yaw);
			mc.player.yRotO = yaw;
			mc.player.setYHeadRot(yaw);
			mc.player.yHeadRotO = yaw;
			mc.player.setXRot(AUTO_PITCH);
			mc.player.xRotO = AUTO_PITCH;
		}

		if (mc.screen != null) {
			mc.setScreen(null);
		}
		if (mc.gui != null) {
			mc.gui.getChat()
					.clearMessages(true);
		}
		mc.getToasts()
				.clear();

		SpikeHud.progress((AUTO_ROW.isEmpty() ? "" : "[" + AUTO_ROW + "] ") + "tick " + ticksSinceSpike
				+ "/" + AUTO_EXIT_TICKS);
		reportLoadState();

		if (++ticksSinceSpike == AUTO_EXIT_TICKS / 2) {
			FrameCost.getInstance()
					.resetRun();
			ParticleBuffer.getInstance()
					.resetRun();
			com.wf.gemrender.water.WaterSplit.getInstance()
					.resetRun();
			com.wf.gemrender.render.GlAudit.resetRun();
		}

		if (ticksSinceSpike >= AUTO_EXIT_TICKS) {
			Screenshot.grab(mc.gameDirectory, "gemrender-spike.png", mc.getMainRenderTarget(),
					message -> GemRender.LOGGER.info("Spike screenshot: {}", message.getString()));

			BoneBuffer bones = BoneBuffer.getInstance();
			PoseCache poses = PoseCache.getInstance();
			ResourceLocation asset = autoAsset();

			com.wf.gemrender.gltf.GemRenderPartsModel parts = autoIsParts() && asset != null
					? SpikeAssets.parts(asset)
					: null;
			com.wf.gemrender.gltf.GemRenderGltfModel model = asset == null || autoIsParts() ? null
					: SpikeAssets.model(asset);
			int expected = model != null
					? poses.evaluationsLastFrame() * matricesPerPose(model)
					: AUTO_SPIKE * SkinnedCubeMesh.JOINT_COUNT;

			FrameCost cost = FrameCost.getInstance();
			GemRender.LOGGER.info(
					"{} boneBufferInitialised={} matricesLastFrame={} expectedMatrices={} cullSphere={} "
							+ "instances={} poseRequests={} poseEvaluations={} sync={} asset={} backend={} "
							+ "frame={}x{} modelGeneration={} modelsLoaded={} morphFloats={} "
							+ "morphFloatsBefore={} frozenAt={} spin={} spinNode={} lod={} lodMax={} "
							+ "lodMeanCentis={} costFrames={} poseUs={} "
							+ "overheadUs={} uploadUs={} nsPerPose={} instanceWrites={} waterSplit={} units={} "
							+ "glAudit={} "
							+ "path={} parts={} partMeshes={} evalsPerFrame={} spinDuty={} "
							+ "particleBufferInitialised={} particleSlots={} particlesAlive={} "
							+ "particlesWanted={} particleEmitters={} particleBlend={} "
							+ "particleSizeScale={} particleUploadFrames={} particleUploadCalls={} "
							+ "particleUploadBytes={}",
					VERDICT_PREFIX,
					bones.isInitialized(),
					bones.lastUploadedCount(),
					expected,
					asset != null && !autoIsParts() ? GltfVisual.lastCullSphere() : "n/a",
					autoCount(),
					poses.requestsLastFrame(),
					poses.evaluationsLastFrame(),
					AUTO_SYNC,
					asset != null ? asset : "cubes",
					System.getProperty("flw.backend", "<default>"),

					mc.getMainRenderTarget().width,
					mc.getMainRenderTarget().height,
					GemRenderModels.generation(),
					GemRenderModels.cache()
							.loadedCount(),
					MorphBuffer.getInstance()
							.floatCount(),
					morphFloatsBeforeReload,

					SpikeClock.frozenAt(),

					AUTO_SPIN,
					AUTO_SPIN_NODE,

					PoseLod.getInstance(),
					poses.lodMaxLastFrame(),
					poses.lodMeanCentisLastFrame(),
					cost.sampledFrames(),

					cost.meanPoseNanos() / 1000,
					cost.meanOverheadNanos() / 1000,
					cost.meanUploadNanos() / 1000,
					cost.nanosPerPose(),
					cost.meanInstanceWrites(),

					com.wf.gemrender.water.WaterSplit.getInstance()
							.report(),

					SamplerProbe.report(),

					com.wf.gemrender.render.GlAudit.report(),

					autoIsParts() ? "parts" : "skinned",
					parts == null ? 0 : parts.partCount(),
					parts == null ? 0 : parts.meshCount(),
					cost.meanPoseCount(),
					AUTO_SPIN_DUTY,

					ParticleBuffer.getInstance()
							.isInitialized(),
					ParticleBuffer.getInstance()
							.capacitySlots(),
					ParticleBuffer.getInstance()
							.aliveCount(ParticleClock.seconds()),
					AUTO_PARTICLES,
					ParticleSpikeEffect.EMITTERS,
					com.wf.gemrender.spike.ParticleSpikeVisual.BLEND,
					ParticleSpikeEffect.SIZE_SCALE,
					ParticleBuffer.getInstance()
							.uploadFrames(),
					ParticleBuffer.getInstance()
							.uploadCalls(),
					ParticleBuffer.getInstance()
							.uploadBytes());
			mc.stop();
		}
	}

	private static final String AUTO_LABEL = System.getProperty("gemrender.autolabel", "");

	private static final String AUTO_ROW = System.getProperty("gemrender.autorow", "");

	private static void describeRun(@Nullable ResourceLocation asset) {
		java.util.List<String> what = new java.util.ArrayList<>();
		String clip = System.getProperty("gemrender.autoanimation", "running_loop");
		boolean noClip = com.wf.gemrender.spike.PartsVisual.NO_CLIP.equals(clip);

		if (AUTO_PARTICLES > 0) {
			SpikeHud.progress(AUTO_ROW.isEmpty() ? "" : "[" + AUTO_ROW + "]");
			SpikeHud.describe(java.util.List.of(
					(AUTO_LABEL.isEmpty() ? "spike" : AUTO_LABEL) + "  |  " + AUTO_PARTICLES
							+ " x billboard particles  |  CLOSED FORM (nothing written per frame)",
					"life=" + ParticleSpikeEffect.LIFE_SECONDS + "s   one instance per slot, 16 bytes, "
							+ "written once at creation"),
					"the fountain holds ~" + AUTO_PARTICLES + " alive and alive= tracks it");
			return;
		}

		what.add((AUTO_LABEL.isEmpty() ? "spike" : AUTO_LABEL) + "  |  " + autoCount() + " x "
				+ (asset == null ? "skinned cubes" : asset.getPath()
						.substring(asset.getPath()
								.lastIndexOf('/') + 1))
				+ "  |  " + (autoIsParts() ? "RIGID PARTS (one instance per part)"
						: "SKINNED PALETTE (one bone matrix per bone)"));

		StringBuilder detail = new StringBuilder();
		detail.append("clip=")
				.append(noClip ? "<none>" : clip);
		if (AUTO_SPIN != 0.0f) {
			detail.append("   spin=")
					.append(AUTO_SPIN)
					.append(" turns/s on '")
					.append(AUTO_SPIN_BONE.isEmpty() ? "<root>" : AUTO_SPIN_BONE)
					.append('\'');
			if (AUTO_SPIN_DUTY < 1.0f) {
				detail.append(" at ")
						.append(Math.round(AUTO_SPIN_DUTY * 100))
						.append("% duty");
			}
		}
		if (autoIsParts() && asset != null) {
			com.wf.gemrender.gltf.GemRenderPartsModel parts = SpikeAssets.parts(asset);
			if (parts != null) {
				detail.append("   ")
						.append(parts.partCount())
						.append(" parts, ")
						.append(parts.meshCount())
						.append(" distinct meshes");
			}
		}
		what.add(detail.toString());

		SpikeHud.progress(AUTO_ROW.isEmpty() ? "" : "[" + AUTO_ROW + "]");
		SpikeHud.describe(what, expectation(noClip));
	}

	private static String expectation(boolean noClip) {
		if (SpikeClock.isFrozen()) {
			return "NOTHING MOVES. The animation clock is pinned at " + SpikeClock.frozenAt()
					+ "s so the two paths can be diffed as stills.";
		}

		StringBuilder out = new StringBuilder();
		out.append(noClip ? "no clip is playing, so the model is at rest except for"
				: "the clip plays on a loop, each copy at its own phase");

		if (AUTO_SPIN == 0.0f) {
			return noClip ? "NOTHING MOVES. No clip and no spin were asked for." : out.toString();
		}

		out.append(noClip ? " " : ", and ")
				.append("'")
				.append(AUTO_SPIN_BONE.isEmpty() ? "the root" : AUTO_SPIN_BONE)
				.append("' turns at ")
				.append(AUTO_SPIN)
				.append(" turns/s (one revolution every ")
				.append(Math.round(1.0f / Math.abs(AUTO_SPIN)))
				.append("s – slow, watch for a while)");

		if (AUTO_SPIN_DUTY < 1.0f) {
			out.append(", moving for the first ")
					.append(Math.round(AUTO_SPIN_DUTY * 100))
					.append("% of each revolution and holding still for the rest");
		}
		return out.toString();
	}

	private static void applyGraphicsMode(Minecraft mc) {
		if (AUTO_GRAPHICS.isEmpty()) {
			return;
		}

		net.minecraft.client.GraphicsStatus mode = switch (AUTO_GRAPHICS.toLowerCase(java.util.Locale.ROOT)) {
			case "fast" -> net.minecraft.client.GraphicsStatus.FAST;
			case "fancy" -> net.minecraft.client.GraphicsStatus.FANCY;
			case "fabulous" -> net.minecraft.client.GraphicsStatus.FABULOUS;
			default -> null;
		};
		if (mode == null) {
			GemRender.LOGGER.error("Auto-spike: unknown -Pgraphics={}; expected fast, fancy or fabulous.",
					AUTO_GRAPHICS);
			return;
		}

		mc.options.graphicsMode()
				.set(mode);
		mc.options.save();
		mc.levelRenderer.allChanged();

		GemRender.LOGGER.info("Auto-spike: graphics mode {} (shader transparency {}).", mode,
				Minecraft.useShaderTransparency() ? "on" : "off");
	}

	private static void clearStagingArea(net.minecraft.client.multiplayer.ClientPacketListener connection,
			BlockPos origin, float extent) {
		int reach = Math.min(CLEAR_MAX_EXTENT, Math.round(extent));

		fillTiled(connection, origin.getX() - CLEAR_MARGIN, origin.getZ() - CLEAR_MARGIN,
				origin.getX() + reach + CLEAR_MARGIN, origin.getZ() + reach + CLEAR_MARGIN,
				origin.getY() - CLEAR_BELOW, origin.getY() + CLEAR_ABOVE, "minecraft:air");

		connection.sendCommand("kill @e[type=minecraft:slime]");
	}

	private static final int CLEAR_MARGIN = 40;
	private static final int CLEAR_MAX_EXTENT = 128;
	private static final int CLEAR_BELOW = 24;
	private static final int CLEAR_ABOVE = 40;

	private static void buildWaterBasin(net.minecraft.client.multiplayer.ClientPacketListener connection,
			BlockPos origin, float extent) {
		int surface = origin.getY() - AUTO_WATER;
		int span = Math.round(extent) + WATER_MARGIN;
		int x0 = origin.getX() - WATER_MARGIN;
		int z0 = origin.getZ() - WATER_MARGIN;
		int x1 = origin.getX() + span;
		int z1 = origin.getZ() + span;

		fillTiled(connection, x0 - 1, z0 - 1, x1 + 1, z1 + 1, surface - 1, surface, "minecraft:stone");
		fillTiled(connection, x0, z0, x1, z1, surface, surface, "minecraft:water");

		GemRender.LOGGER.info("Auto-spike: water basin at y={}, {} blocks below the scene, {}x{}.",
				surface, AUTO_WATER, x1 - x0, z1 - z0);
	}

	private static void buildWaterColumn(Minecraft mc,
			net.minecraft.client.multiplayer.ClientPacketListener connection, BlockPos origin,
			@Nullable ResourceLocation asset, float extent, int back) {
		float spacing = GltfVisual.spacingOf(autoSphere());
		int stride = GltfVisual.stride(autoCount());

		int rank = Math.max(1, stride - 1);
		int plane = origin.getX() + origin.getZ() + Math.round((rank - 0.5f) * spacing);
		int centreX = origin.getX() + Math.round((rank - 0.5f) * spacing / 2.0f);

		int half = Math.max(0, Math.round((AUTO_WATER_COLUMN * 1.4142f - 1.0f) / 2.0f));

		int reach = Math.min(COLUMN_MAX_REACH, Math.round(extent * 0.7071f) + COLUMN_REACH_MARGIN);

		int yBottom = origin.getY() - Math.max(3, Math.round(spacing * COLUMN_BELOW_FACTOR));
		int yTop = origin.getY() + Math.max(6, Math.round(spacing * COLUMN_ABOVE_FACTOR));

		connection.sendCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d %s",
				origin.getX() - Math.round(spacing), yBottom + 1, origin.getZ(),
				origin.getX() + Math.round(3.0f * spacing), yBottom + 1, origin.getZ(),
				"minecraft:white_concrete"));

		for (int x = centreX - reach; x <= centreX + reach; x++) {
			connection.sendCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d %s",
					x, yBottom, plane - half - x, x, yTop, plane + half - x,
					"minecraft:barrier[waterlogged=true]"));
		}

		double far = Math.hypot(reach, Math.sqrt(2.0) * (back + (rank - 0.5f) * spacing / 2.0f));
		int chunks = Math.max(8, Math.min(32, (int) Math.ceil(far * 1.5 / 16.0)));
		mc.options.renderDistance()
				.set(chunks);

		int inFront = 0;
		for (int i = 0; i < autoCount(); i++) {
			if (i % stride + i / stride < rank) {
				inFront++;
			}
		}

		GemRender.LOGGER.info("Auto-spike: water column {} blocks thick across x+z={}, {} steps wide, "
				+ "y={}..{}, render distance {} chunks; {} copies in front of it, {} behind; vanilla "
				+ "controls in front of it: white concrete (opaque), stained glass (translucent terrain), "
				+ "slime (translucent entity).",
				AUTO_WATER_COLUMN, plane, 2 * reach + 1, yBottom, yTop, chunks, inFront,
				autoCount() - inFront);
	}

	private static final float COLUMN_BELOW_FACTOR = 0.35f;
	private static final float COLUMN_ABOVE_FACTOR = 0.75f;

	private static final int COLUMN_REACH_MARGIN = 10;
	private static final int COLUMN_MAX_REACH = 48;

	private static void buildVanillaControls(net.minecraft.client.multiplayer.ClientPacketListener connection,
			BlockPos origin, @Nullable ResourceLocation asset) {
		float spacing = GltfVisual.spacingOf(autoSphere());
		int rank = Math.max(1, GltfVisual.stride(autoCount()) - 1);
		int controlRank = Math.max(1, rank - 1);
		int yBottom = origin.getY() - Math.max(3, Math.round(spacing * COLUMN_BELOW_FACTOR));

		buildStainedGlassControl(connection, origin, spacing, controlRank, yBottom);
		summonSlimeControl(connection, origin, spacing, controlRank, yBottom);

		GemRender.LOGGER.info("Auto-spike: vanilla translucent controls placed, stained glass (terrain, "
				+ "sorted with the water) and a slime (entity, writes depth before the water pass).");
	}

	private static void buildStainedGlassControl(net.minecraft.client.multiplayer.ClientPacketListener connection,
			BlockPos origin, float spacing, int controlRank, int yBottom) {
		int size = Math.max(2, Math.round(spacing * CONTROL_SIZE_FACTOR));
		int x = origin.getX() - Math.round(spacing);
		int z = origin.getZ() + Math.round(controlRank * spacing);

		connection.sendCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d %s",
				x, yBottom + 2, z, x + size, yBottom + 2 + size, z + size,
				"minecraft:light_blue_stained_glass"));
	}

	private static void summonSlimeControl(net.minecraft.client.multiplayer.ClientPacketListener connection,
			BlockPos origin, float spacing, int controlRank, int yBottom) {
		connection.sendCommand("difficulty easy");

		int size = Math.max(2, Math.round(spacing * CONTROL_SIZE_FACTOR));
		int x = origin.getX() + Math.round((controlRank + 1) * spacing);
		int z = origin.getZ() - Math.round(spacing);

		connection.sendCommand(String.format(java.util.Locale.ROOT,
				"summon minecraft:slime %d %d %d {Size:%d,NoAI:1b,NoGravity:1b,Invulnerable:1b,"
						+ "PersistenceRequired:1b,Silent:1b}",
				x, yBottom + 2, z, size));
	}

	private static final float CONTROL_SIZE_FACTOR = 0.4f;

	private static void fillTiled(net.minecraft.client.multiplayer.ClientPacketListener connection,
			int x0, int z0, int x1, int z1, int yMin, int yMax, String block) {
		int layers = yMax - yMin + 1;
		int tile = Math.max(1, (int) Math.sqrt(32768.0 / layers) - 1);

		for (int x = x0; x <= x1; x += tile) {
			for (int z = z0; z <= z1; z += tile) {
				connection.sendCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d %s",
						x, yMin, z, Math.min(x + tile - 1, x1), yMax, Math.min(z + tile - 1, z1), block));
			}
		}
	}

	private static final int WATER_MARGIN = 24;

	private static int autoSpinNode(ResourceLocation asset) {
		if (AUTO_SPIN_BONE.isEmpty()) {
			return AUTO_SPIN_NODE;
		}

		com.wf.gemrender.gltf.GemRenderGltfModel model = SpikeAssets.model(asset);
		int slot = model == null ? -1 : model.layout()
				.nodeTable()
				.slotOfName(AUTO_SPIN_BONE);
		if (slot < 0) {
			GemRender.LOGGER.error("Auto-spike: {} has no bone named '{}' to spin.", asset, AUTO_SPIN_BONE);
			return AUTO_SPIN_NODE;
		}
		return slot;
	}

	private static void queueScene(Level level, BlockPos origin) {
		ResourceLocation asset = autoAsset();
		if (asset != null && autoIsParts()) {
			VisualizationManager.getOrThrow(level)
					.effects()
					.queueAdd(new PartsEffect(level, origin, asset, autoCount(),
							System.getProperty("gemrender.autoanimation", "running_loop"), AUTO_SYNC,
							AUTO_SPIN, AUTO_SPIN_BONE, AUTO_SPIN_DUTY));
		} else if (asset != null) {
			int spinNode = autoSpinNode(asset);
			VisualizationManager.getOrThrow(level)
					.effects()
					.queueAdd(new GltfEffect(level, origin, asset, autoCount(),
							System.getProperty("gemrender.autoanimation", "running_loop"), AUTO_SYNC, 1.0f,
							AUTO_SPIN, spinNode, AUTO_SPIN_DUTY));
		} else if (AUTO_PARTICLES > 0) {
			VisualizationManager.getOrThrow(level)
					.effects()
					.queueAdd(new ParticleSpikeEffect(level, origin, AUTO_PARTICLES));
		} else {
			VisualizationManager.getOrThrow(level)
					.effects()
					.queueAdd(new SpikeEffect(level, origin, AUTO_SPIKE));
		}
	}

	private static void reloadResources(Minecraft mc) {
		morphFloatsBeforeReload = MorphBuffer.getInstance()
				.floatCount();

		GemRender.LOGGER.info("Auto-spike: reloading resources at tick {} (morph buffer {} floats, "
				+ "model generation {})",
				ticksSinceSpike, morphFloatsBeforeReload, GemRenderModels.generation());

		mc.reloadResourcePacks()
				.thenRun(() -> mc.execute(() -> {
					Level level = mc.level;
					if (level == null || sceneOrigin == null
							|| !VisualizationManager.supportsVisualization(level)) {
						return;
					}
					queueScene(level, sceneOrigin);
					GemRender.LOGGER.info("Auto-spike: rebuilt the scene after reload (morph buffer {} "
							+ "floats, model generation {})",
							MorphBuffer.getInstance()
									.floatCount(),
							GemRenderModels.generation());
				}));
	}

	private static int matricesPerPose(com.wf.gemrender.gltf.GemRenderGltfModel model) {
		int morphFloats = model.morphs()
				.blockFloats();
		return model.jointCount()
				+ (morphFloats + BoneBuffer.FLOATS_PER_MATRIX - 1) / BoneBuffer.FLOATS_PER_MATRIX;
	}

	@SubscribeEvent
	public static void registerClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(Commands.literal(GemRender.MOD_ID)
				.then(Commands.literal("capture")
						.executes(ctx -> capture(ctx.getSource(), 1))
						.then(Commands.argument("frames", IntegerArgumentType.integer(1, 64))
								.executes(ctx -> capture(ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "frames")))))
				.then(Commands.literal("spike")
						.executes(ctx -> spike(ctx.getSource(), 1024))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
								.executes(ctx -> spike(ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "count")))))
				.then(Commands.literal("particles")
						.executes(ctx -> particles(ctx.getSource(), 3000))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 200000))
								.executes(ctx -> particles(ctx.getSource(),
										IntegerArgumentType.getInteger(ctx, "count")))))
				.then(gltfCommand("radar", SpikeAssets.RADAR, "running_loop"))
				.then(gltfCommand("rig", SpikeAssets.RIG, "curl"))
				.then(gltfCommand("morph", SpikeAssets.MORPH, "pump"))
				.then(gltfCommand("glass", SpikeAssets.GLASS, "turn"))
				.then(gltfCommand("pbr", SpikeAssets.PBR, "turn"))
				.then(Commands.literal("status")
						.executes(ctx -> status(ctx.getSource()))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> gltfCommand(
			String name, ResourceLocation asset, String defaultAnimation) {
		return Commands.literal(name)
				.executes(ctx -> gltf(ctx.getSource(), asset, 16, defaultAnimation, false, 0.0f))
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
						.executes(ctx -> gltf(ctx.getSource(), asset,
								IntegerArgumentType.getInteger(ctx, "count"), defaultAnimation, false, 0.0f))
						.then(Commands.argument("animation", StringArgumentType.word())
								.executes(ctx -> gltf(ctx.getSource(), asset,
										IntegerArgumentType.getInteger(ctx, "count"),
										StringArgumentType.getString(ctx, "animation"), false, 0.0f))
								.then(Commands.argument("sync", BoolArgumentType.bool())
										.executes(ctx -> gltf(ctx.getSource(), asset,
												IntegerArgumentType.getInteger(ctx, "count"),
												StringArgumentType.getString(ctx, "animation"),
												BoolArgumentType.getBool(ctx, "sync"), 0.0f))
										.then(Commands.argument("spin", FloatArgumentType.floatArg(-64.0f, 64.0f))
												.executes(ctx -> gltf(ctx.getSource(), asset,
														IntegerArgumentType.getInteger(ctx, "count"),
														StringArgumentType.getString(ctx, "animation"),
														BoolArgumentType.getBool(ctx, "sync"),
														FloatArgumentType.getFloat(ctx, "spin")))))));
	}

	private static int spike(CommandSourceStack source, int count) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			source.sendFailure(Component.literal("No level."));
			return 0;
		}

		if (!VisualizationManager.supportsVisualization(level)) {
			source.sendFailure(Component.literal(
					"Flywheel visualization is off for this level. Check the Flywheel backend is not set to 'off'."));
			return 0;
		}

		BlockPos origin = BlockPos.containing(source.getPosition());
		VisualizationManager.getOrThrow(level)
				.effects()
				.queueAdd(new SpikeEffect(level, origin, count));

		source.sendSuccess(() -> Component.literal(
				"Spawned " + count + " skinned cubes at " + origin.toShortString()
						+ ". Run /gemrender status to confirm the bone buffer is live."), false);
		return count;
	}

	private static int particles(CommandSourceStack source, int count) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			source.sendFailure(Component.literal("No level."));
			return 0;
		}

		if (!VisualizationManager.supportsVisualization(level)) {
			source.sendFailure(Component.literal(
					"Flywheel visualization is off for this level. Check the Flywheel backend is not set to 'off'."));
			return 0;
		}

		BlockPos origin = BlockPos.containing(source.getPosition());
		VisualizationManager.getOrThrow(level)
				.effects()
				.queueAdd(new ParticleSpikeEffect(level, origin, count));

		source.sendSuccess(() -> Component.literal("Spawned a " + count + " particle fountain at "
				+ origin.toShortString() + ". The CPU writes a slot per spawn and nothing per frame."), false);
		return count;
	}

	private static int gltf(CommandSourceStack source, ResourceLocation asset, int count, String animation,
			boolean sync, float spin) {
		Level level = Minecraft.getInstance().level;
		if (level == null || !VisualizationManager.supportsVisualization(level)) {
			source.sendFailure(Component.literal("No level, or Flywheel visualization is off."));
			return 0;
		}

		BlockPos origin = BlockPos.containing(source.getPosition());
		VisualizationManager.getOrThrow(level)
				.effects()
				.queueAdd(new GltfEffect(level, origin, asset, count, animation, sync, 1.0f, spin,
						AUTO_SPIN_NODE));

		source.sendSuccess(() -> Component.literal("Spawned " + count + " x " + asset + " at "
				+ origin.toShortString() + " playing '" + animation + "'"
				+ (spin == 0.0f ? "" : " with a " + spin + " turn/s spin on slot " + AUTO_SPIN_NODE)
				+ (sync ? " in step." : ", each at its own phase.")), false);
		return count;
	}

	private static int status(CommandSourceStack source) {
		BoneBuffer bones = BoneBuffer.getInstance();
		PoseCache poses = PoseCache.getInstance();
		boolean live = bones.isInitialized();

		source.sendSuccess(() -> Component.literal(
				"GemRender: bone buffer " + (live ? "LIVE" : "NOT INITIALISED")
						+ ", " + bones.lastUploadedCount() + " matrices uploaded last frame"
						+ ", texture unit " + BoneBuffer.TEXTURE_UNIT), false);

		source.sendSuccess(() -> Component.literal(
				"  poses: " + poses.evaluationsLastFrame() + " evaluated for "
						+ poses.requestsLastFrame() + " instances last frame, quantum "
						+ poses.quantumSeconds() + "s"), false);

		ModelCache<?> models = GemRenderModels.cache();
		source.sendSuccess(() -> Component.literal(
				"  models: " + models.loadedCount() + " loaded of " + models.wanted()
						.size() + " wanted"
						+ (models.failedCount() == 0 ? "" : ", " + models.failedCount() + " FAILED")
						+ ", generation " + models.generation()
						+ ", morph buffer " + MorphBuffer.getInstance()
								.floatCount() + " floats"), false);

		FrameCost cost = FrameCost.getInstance();
		source.sendSuccess(() -> Component.literal(
				"  cost, last frame: " + cost.poseNanos() / 1000 + "us evaluating poses, "
						+ cost.overheadNanos() / 1000 + "us per-instance, "
						+ cost.uploadNanos() / 1000 + "us uploading, "
						+ cost.instanceWrites() + " instances re-uploaded"), false);
		source.sendSuccess(() -> Component.literal(
				"  cost, mean of " + cost.sampledFrames() + " frames: " + cost.meanPoseNanos() / 1000
						+ "us poses, " + cost.meanOverheadNanos() / 1000 + "us per-instance, "
						+ cost.meanUploadNanos() / 1000 + "us uploading, "
						+ cost.nanosPerPose() + "ns per pose"), false);

		if (!live) {
			source.sendFailure(Component.literal(
					"The bone buffer has never uploaded. Either nothing is being drawn, or DrawManagerMixin "
							+ "is no longer applying to Flywheel's DrawManager."));
		}
		return live ? 1 : 0;
	}

	private static int capture(CommandSourceStack source, int frames) {
		if (!GemRenderRenderDoc.isAvailable()) {
			source.sendFailure(Component.literal(
					"RenderDoc is not attached to this process. Relaunch with: ./gradlew runClient -PwithRenderDoc"));
			return 0;
		}

		boolean triggered = frames == 1
				? GemRenderRenderDoc.triggerCapture()
				: GemRenderRenderDoc.triggerMultiFrameCapture(frames);

		if (!triggered) {
			source.sendFailure(Component.literal("RenderDoc refused the capture request."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal(
				"Capturing " + frames + (frames == 1 ? " frame" : " frames")
						+ ". Convert with: ./gradlew renderDocConvert"), false);
		return frames;
	}
}
