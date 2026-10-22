package dev.engine_room.flywheel.impl;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import dev.engine_room.flywheel.api.Flywheel;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadCallback;
import dev.engine_room.flywheel.api.event.ReloadLevelRendererCallback;
import dev.engine_room.flywheel.backend.compile.FlwProgramsReloader;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.impl.compat.EmbeddiumCompat;
import dev.engine_room.flywheel.impl.event.FlwLevelRenderHooks;
import dev.engine_room.flywheel.impl.visualization.VisualizationEventHandler;
import dev.engine_room.flywheel.lib.util.LevelAttached;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.util.ResourceReloadHolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Flywheel's Fabric entrypoint, the counterpart of FlywheelNeoForge. The id `flywheel` belongs to a
 * metadata-only mod nested in GemRender's jar; this class runs from GemRender's entrypoint list.
 */
public final class FlywheelFabric implements ClientModInitializer {
	@UnknownNullability
	private static Version version;
	@Nullable
	private static ClientLevel attachedLevel;
	@Nullable
	private static String reportedBackend;

	@Override
	public void onInitializeClient() {
		version = FabricLoader.getInstance()
				.getModContainer(Flywheel.ID)
				.orElseThrow()
				.getMetadata()
				.getVersion();

		registerImplEventListeners();
		registerLibEventListeners();
		registerBackendEventListeners();

		FlwImpl.init();

		EmbeddiumCompat.init();
	}

	private static void registerImplEventListeners() {
		ReloadLevelRendererCallback.EVENT.register(BackendManagerImpl::onReloadLevelRenderer);
		ReloadLevelRendererCallback.EVENT.register(level -> reportBackend());

		ClientTickEvents.END_LEVEL_TICK.register(level -> VisualizationEventHandler.onClientTick(Minecraft.getInstance(), level));
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> VisualizationEventHandler.onEntityJoinLevel(level, entity));
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> VisualizationEventHandler.onEntityLeaveLevel(level, entity));

		ClientCommandRegistrationCallback.EVENT.register(FlwCommands::registerClientCommands);

		EndClientResourceReloadCallback.EVENT.register((minecraft, resourceManager, initialReload, error) -> {
			BackendManagerImpl.onEndClientResourceReload(error.isPresent());
			reportBackend();
		});

		FlwLevelRenderHooks.init();

		// Widened: NeoForge registers through RegisterDebugEntriesEvent. The entry is off until enabled
		// in the debug options screen, as vanilla's profile table cannot be extended.
		DebugScreenEntries.register(FlwDebugScreenEntry.ID, new FlwDebugScreenEntry());

		// Client-side only: Registries.COMMAND_ARGUMENT_TYPE is synced, so only the class lookup is filled.
		ArgumentTypeInfos.BY_CLASS.put(BackendArgument.class, BackendArgument.INFO);
		ArgumentTypeInfos.BY_CLASS.put(DebugModeArgument.class, DebugModeArgument.INFO);
		ArgumentTypeInfos.BY_CLASS.put(LightSmoothnessArgument.class, LightSmoothnessArgument.INFO);

		// After Minecraft#<init>, so the backend registry is frozen and every backend id can resolve.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> FabricFlwConfig.INSTANCE.ensureLoaded());
	}

	private static void registerLibEventListeners() {
		// NeoForge's LevelEvent.Unload for the client level: the old one is dropped on change and on disconnect.
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
			if (attachedLevel != null && attachedLevel != level) {
				LevelAttached.invalidateLevel(attachedLevel);
			}
			attachedLevel = level;
		});
		// DISCONNECT can fire on the Netty thread, and invalidation frees GL objects.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			if (attachedLevel != null) {
				LevelAttached.invalidateLevel(attachedLevel);
				attachedLevel = null;
			}
		}));

		EndClientResourceReloadCallback.EVENT.register((minecraft, resourceManager, initialReload, error) -> RendererReloadCache.onReloadLevelRenderer());
		EndClientResourceReloadCallback.EVENT.register((minecraft, resourceManager, initialReload, error) -> ResourceReloadHolder.onEndClientResourceReload());
	}

	private static void registerBackendEventListeners() {
		ReloadLevelRendererCallback.EVENT.register(level -> Uniforms.onReloadLevelRenderer());

		ResourceLoader.get(PackType.CLIENT_RESOURCES)
				.registerReloadListener(Identifier.fromNamespaceAndPath(Flywheel.ID, "programs"), FlwProgramsReloader.INSTANCE);
	}

	// Upstream never logs the chosen backend; a dev run and a player's log both need to say which one drew.
	private static void reportBackend() {
		String backend = BackendManagerImpl.getBackendString();
		if (!backend.equals(reportedBackend)) {
			reportedBackend = backend;
			FlwImpl.LOGGER.info("Flywheel backend: {}", backend);
		}
	}

	public static Version version() {
		return version;
	}
}
