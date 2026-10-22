package dev.engine_room.flywheel.impl.mixin.fabric;

import java.util.Optional;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.engine_room.flywheel.api.event.EndClientResourceReloadCallback;
import net.minecraft.client.Minecraft;

/**
 * Raises EndClientResourceReloadCallback when a resource reload finishes. Fabric API has no such event,
 * and upstream's injects into numbered lambdas do not survive 26.1, so the overlay's completion
 * callback is wrapped where each of the two call sites builds it.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	private static final String LOADING_OVERLAY = "Lnet/minecraft/client/gui/screens/LoadingOverlay;<init>("
			+ "Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/resources/ReloadInstance;"
			+ "Ljava/util/function/Consumer;Z)V";

	@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = LOADING_OVERLAY), index = 2)
	private static Consumer<Optional<Throwable>> flywheel$onEndInitialResourceReload(Consumer<Optional<Throwable>> onFinish) {
		return flywheel$wrap(onFinish, true);
	}

	@ModifyArg(method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
			at = @At(value = "INVOKE", target = LOADING_OVERLAY), index = 2)
	private static Consumer<Optional<Throwable>> flywheel$onEndManualResourceReload(Consumer<Optional<Throwable>> onFinish) {
		return flywheel$wrap(onFinish, false);
	}

	private static Consumer<Optional<Throwable>> flywheel$wrap(Consumer<Optional<Throwable>> onFinish, boolean initialReload) {
		return error -> {
			Minecraft minecraft = Minecraft.getInstance();
			EndClientResourceReloadCallback.EVENT.invoker()
					.onEndClientResourceReload(minecraft, minecraft.getResourceManager(), initialReload, error);
			onFinish.accept(error);
		};
	}
}
