package dev.engine_room.flywheel.impl;

import java.util.Optional;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.jetbrains.annotations.UnknownNullability;

import dev.engine_room.flywheel.api.Flywheel;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import dev.engine_room.flywheel.api.event.ReloadLevelRendererEvent;
import dev.engine_room.flywheel.backend.compile.FlwProgramsReloader;
import dev.engine_room.flywheel.backend.engine.uniform.FogUniforms;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.impl.compat.EmbeddiumCompat;
import dev.engine_room.flywheel.impl.event.FlwLevelRenderHooks;
import dev.engine_room.flywheel.impl.visualization.VisualizationEventHandler;
import dev.engine_room.flywheel.lib.util.LevelAttached;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.util.ResourceReloadHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(value = Flywheel.ID, dist = Dist.CLIENT)
public final class FlywheelNeoForge {
	@UnknownNullability
	private static ArtifactVersion version;

	public FlywheelNeoForge(IEventBus modEventBus, ModContainer modContainer) {
		version = modContainer
				.getModInfo()
				.getVersion();

		IEventBus gameEventBus = NeoForge.EVENT_BUS;

		NeoForgeFlwConfig.INSTANCE.registerSpecs(modContainer);

		registerImplEventListeners(gameEventBus, modEventBus);
		registerLibEventListeners(gameEventBus, modEventBus);
		registerBackendEventListeners(gameEventBus, modEventBus);

		CrashReportCallables.registerCrashCallable("Flywheel Backend", BackendManagerImpl::getBackendString);
		FlwImpl.init();

		EmbeddiumCompat.init();
	}

	private static void registerImplEventListeners(IEventBus gameEventBus, IEventBus modEventBus) {
		gameEventBus.addListener((ReloadLevelRendererEvent e) -> BackendManagerImpl.onReloadLevelRenderer(e.level()));

		gameEventBus.addListener((LevelTickEvent.Post e) -> {
			// Make sure we don't tick on the server somehow.
			if (e.getLevel().isClientSide()) {
				VisualizationEventHandler.onClientTick(Minecraft.getInstance(), e.getLevel());
			}
		});
		gameEventBus.addListener((EntityJoinLevelEvent e) -> VisualizationEventHandler.onEntityJoinLevel(e.getLevel(), e.getEntity()));
		gameEventBus.addListener((EntityLeaveLevelEvent e) -> VisualizationEventHandler.onEntityLeaveLevel(e.getLevel(), e.getEntity()));

		gameEventBus.addListener(FlwCommands::registerClientCommands);

		// Flywheel's own EndClientResourceReloadEvent, raised from NeoForge's rather than from a pair of
		// synthetic lambdas inside Minecraft -- see MinecraftMixin for why those cannot be targeted on
		// 26.1. NeoForge fires this only on a reload that SUCCEEDED, so the error is always empty; every
		// listener in the tree already returns early when it is present, so the behaviour is the same and
		// the failure case is now simply that nothing is told a reload finished, which is correct.
		gameEventBus.addListener((ClientResourceLoadFinishedEvent e) -> {
			Minecraft minecraft = Minecraft.getInstance();
			ModLoader.postEvent(new EndClientResourceReloadEvent(minecraft, minecraft.getResourceManager(),
					e.isInitial(), Optional.empty()));
		});

		// The level render hooks. See FlwLevelRenderHooks for why these are events on 26.1 rather than
		// injections into LevelRenderer#renderLevel.
		gameEventBus.addListener(FlwLevelRenderHooks::onAfterSky);
		gameEventBus.addListener(FlwLevelRenderHooks::onAfterOpaqueFeatures);
		gameEventBus.addListener(FlwLevelRenderHooks::onAfterTranslucentBlocks);
		gameEventBus.addListener(FlwLevelRenderHooks::onAfterLevel);

		// 26.1 replaced CustomizeGuiOverlayEvent.DebugText with the debug-screen entry registry, so
		// Flywheel's F3 block is now a registered, individually toggleable entry.
		modEventBus.addListener((RegisterDebugEntriesEvent e) -> {
			e.register(FlwDebugScreenEntry.ID, new FlwDebugScreenEntry());
			e.includeInProfile(FlwDebugScreenEntry.ID, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.IN_OVERLAY);
			e.includeInProfile(FlwDebugScreenEntry.ID, DebugScreenProfile.PERFORMANCE, DebugScreenEntryStatus.NEVER);
		});

		modEventBus.addListener((EndClientResourceReloadEvent e) -> BackendManagerImpl.onEndClientResourceReload(e.error().isPresent()));

		modEventBus.addListener((FMLCommonSetupEvent e) -> {
			// We can't register anything to Registries.COMMAND_ARGUMENT_TYPE because it is a synced registry but
			// Flywheel is a client-side only mod.
			ArgumentTypeInfos.registerByClass(BackendArgument.class, BackendArgument.INFO);
			ArgumentTypeInfos.registerByClass(DebugModeArgument.class, DebugModeArgument.INFO);
			ArgumentTypeInfos.registerByClass(LightSmoothnessArgument.class, LightSmoothnessArgument.INFO);
		});
	}

	private static void registerLibEventListeners(IEventBus gameEventBus, IEventBus modEventBus) {
		gameEventBus.addListener((LevelEvent.Unload e) -> LevelAttached.invalidateLevel(e.getLevel()));

		modEventBus.addListener((EndClientResourceReloadEvent e) -> RendererReloadCache.onReloadLevelRenderer());
		modEventBus.addListener((EndClientResourceReloadEvent e) -> ResourceReloadHolder.onEndClientResourceReload());

	}

	private static void registerBackendEventListeners(IEventBus gameEventBus, IEventBus modEventBus) {
		gameEventBus.addListener((ReloadLevelRendererEvent e) -> Uniforms.onReloadLevelRenderer());

		modEventBus.addListener((AddClientReloadListenersEvent e) -> {
			e.addListener(ResourceLocation.fromNamespaceAndPath(Flywheel.ID, "programs"), FlwProgramsReloader.INSTANCE);
		});

		// 26.1 packs the fog into a UBO Flywheel cannot read back, so catch it on the way past.
		gameEventBus.addListener((ViewportEvent.RenderFog e) -> FogUniforms.onRenderFog(e));
	}

	public static ArtifactVersion version() {
		return version;
	}
}
