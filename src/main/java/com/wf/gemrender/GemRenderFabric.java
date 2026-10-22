package com.wf.gemrender;

//? if fabric {
/*import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.fabric.DirectHooks;
import com.wf.gemrender.fabric.IrisHooks;
import com.wf.gemrender.fabric.LevelStageHooks;
import com.wf.gemrender.fabric.LightHooks;
import dev.engine_room.flywheel.api.event.EndClientResourceReloadCallback;
import net.fabricmc.api.ClientModInitializer;

/^*
 * The Fabric entrypoint.
 *
 * <p>Separate from {@link GemRender} because the loaders disagree about what a mod class is: a
 * {@code @Mod}-annotated constructor there, an interface to implement here. It also carries the
 * subscriptions the other two get from annotations — Flywheel raises the same events on every
 * loader, but only Forge and NeoForge go looking for handlers.
 *^/
public final class GemRenderFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        GemRender.LOGGER.info("GemRender loading");
        EndClientResourceReloadCallback.EVENT.register(GemRenderModels::onEndClientResourceReload);
        LevelStageHooks.init();
        DirectHooks.init();
        IrisHooks.init();
        LightHooks.init();
        attachHarness();
        attachBench();
    }

    private static void attachHarness() {
        try {
            Class.forName("com.wf.gemrender.fabric.HarnessHooks")
                    .getMethod("init")
                    .invoke(null);
            GemRender.LOGGER.info("GemRender spike harness attached");
        } catch (ClassNotFoundException notADevRun) {
            return;
        } catch (ReflectiveOperationException broken) {
            GemRender.LOGGER.error("The harness is on the classpath but would not start.", broken);
        }
    }

    private static void attachBench() {
        try {
            Class.forName("com.wf.gemrender.fabric.BenchHooks")
                    .getMethod("init")
                    .invoke(null);
            GemRender.LOGGER.info("GemRender bench attached");
        } catch (ClassNotFoundException notABenchRun) {
            return;
        } catch (ReflectiveOperationException broken) {
            GemRender.LOGGER.error("The bench is on the classpath but would not start.", broken);
        }
    }
}
*///?}
