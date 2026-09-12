package com.wf.gemrender.asset;

import com.wf.gemrender.GemRender;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads each asset once, off the render thread, and hands out {@link Handle}s that stay valid across
 * resource reloads.
 *
 * <h2>Loading is a request, not a call</h2>
 *
 * <p>{@link Handle#get()} never blocks. It returns the asset if it is loaded and otherwise starts
 * loading it and returns {@code null}, so the frame that first asks for a model draws without it and
 * a later one draws with it. That is the only shape that works here: importing the radar model takes
 * about 300 ms, and the alternative is a render thread stalled for a third of a second at whatever
 * moment a player first looks at something.
 *
 * <p>So a caller must handle {@code null} -- but every caller already had to, because an asset can be
 * missing or broken. Nothing new is being asked of them; the answer "not yet" simply joins "not ever".
 *
 * <p>Nothing here touches the GPU. An import parses, bakes its material maps, stitches an atlas and
 * builds meshes, all of which is arithmetic; the two places it does reach for a GL object --
 * registering a texture, uploading a compressed atlas -- already route themselves through
 * {@code RenderSystem.recordRenderCall}, and the vertex upload is separate and happens on the first
 * draw. See {@code ResidentModels}.
 *
 * <h2>Reloading</h2>
 *
 * <p>A reload is a generation bump: every handle is disposed, the counter moves, and everything that
 * was wanted is loaded again in parallel. Handles are stable across it, so a mod holds one in a
 * {@code static final} and never learns that the model behind it was replaced.
 *
 * <p>{@link #quiesce()} exists for the state a reload has to reset that is not owned by any one cache
 * -- the morph buffer, the glTF buffer cache. Resetting those while an import is still running would
 * append the old generation's data to the new generation's buffer, so a reload stops accepting
 * requests and waits for the imports in flight before it touches them.
 */
public final class ModelCache<T> {
    private static final CompletableFuture<Void> COMPLETED = CompletableFuture.completedFuture(null);
    private final String name;
    private final Loader<T> loader;
    private final Disposer<T> disposer;
    private final Executor executor;
    private final Map<ResourceLocation, Handle<T>> handles = new ConcurrentHashMap<>();
    /**
     * Guards {@link #inFlight} and {@link #quiesced} together, and is what {@link #awaitIdle} waits on.
     */
    private final Object idle = new Object();
    private volatile int generation;
    private int inFlight;
    private boolean quiesced;

    public ModelCache(String name, Loader<T> loader, Disposer<T> disposer) {
        this(name, loader, disposer, Pool.LOADERS);
    }

    /**
     * As above, on a caller's executor.
     *
     * <p>{@code Runnable::run} makes every load synchronous and every {@link Handle#get()} immediate,
     * which is what a test wants and what production must not have.
     */
    public ModelCache(String name, Loader<T> loader, Disposer<T> disposer, Executor executor) {
        this.name = name;
        this.loader = loader;
        this.disposer = disposer;
        this.executor = executor;
    }

    public Handle<T> handle(ResourceLocation id) {
        return handles.computeIfAbsent(id, key -> new Handle<>(this, key));
    }

    /**
     * The asset if it is loaded; otherwise {@code null}, having asked for it.
     */
    @Nullable
    public T get(ResourceLocation id) {
        return handle(id).get();
    }

    /**
     * The asset, waiting for the import if one is running. Never from a loader thread.
     */
    @Nullable
    public T getBlocking(ResourceLocation id) {
        return handle(id).getBlocking();
    }

    /**
     * Disposes every loaded asset, moves the generation on, and re-imports everything wanted.
     *
     * <p>Disposal is synchronous and complete before any import of the new generation starts, because
     * disposal frees GL objects and the caller is the render thread; the imports themselves run on the
     * pool. The returned future completes, with the new generation, once they all have.
     */
    public CompletableFuture<Integer> reload() {
        quiesce();

        List<CompletableFuture<?>> loads;
        int disposed = 0;
        int generation;

        synchronized (this) {
            List<ResourceLocation> wanted = new ArrayList<>(handles.keySet());

            for (Handle<T> handle : handles.values()) {
                if (handle.dispose()) {
                    disposed++;
                }
            }

            generation = ++this.generation;
            resume();

            loads = new ArrayList<>(wanted.size());
            for (ResourceLocation id : wanted) {
                loads.add(handle(id).request());
            }
        }

        int freed = disposed;
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    int failed = failedCount();
                    GemRender.LOGGER.info("{} generation {}: disposed {}, reloaded {}{}",
                            name, generation, freed, loadedCount(),
                            failed == 0 ? "" : ", " + failed + " failed");
                    return generation;
                });
    }

    /**
     * Stops accepting load requests and waits for the ones already running.
     *
     * <p>The window a reload needs in order to reset state that imports write into. Requests made while
     * quiesced are dropped rather than queued -- the reload about to happen will ask for all of them
     * again anyway -- so {@link Handle#get()} answers {@code null} for the moment it lasts.
     *
     * <p>Cleared by {@link #reload()} once the new generation has started. A caller that quiesces and
     * then does not reload has stopped the cache for good, which is why this is not public policy: it
     * is the first half of a reload, not a pause button.
     */
    public void quiesce() {
        synchronized (idle) {
            quiesced = true;
            waitForIdle();
        }
    }

    /**
     * Waits for every import in flight, without stopping new ones.
     */
    public void awaitIdle() {
        synchronized (idle) {
            waitForIdle();
        }
    }

    private void waitForIdle() {
        boolean interrupted = false;
        while (inFlight > 0) {
            try {
                idle.wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread()
                    .interrupt();
        }
    }

    private void resume() {
        synchronized (idle) {
            quiesced = false;
        }
    }

    /**
     * Claims a slot for one import, or refuses because the cache is quiesced.
     *
     * <p>The check and the count move together under one monitor, which is what makes {@link #quiesce}
     * airtight: a request that reads "not quiesced" has already been counted by the time the reload can
     * observe the count, so it cannot slip in behind {@link #waitForIdle} and publish into the
     * generation that follows.
     */
    private boolean beginLoad() {
        synchronized (idle) {
            if (quiesced) {
                return false;
            }
            inFlight++;
            return true;
        }
    }

    private void endLoad() {
        synchronized (idle) {
            if (--inFlight == 0) {
                idle.notifyAll();
            }
        }
    }

    public int generation() {
        return generation;
    }

    public int loadedCount() {
        int count = 0;
        for (Handle<T> handle : handles.values()) {
            if (handle.isLoaded()) {
                count++;
            }
        }
        return count;
    }

    public int failedCount() {
        int count = 0;
        for (Handle<T> handle : handles.values()) {
            if (handle.hasFailed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * How many imports are running right now.
     */
    public int loadingCount() {
        synchronized (idle) {
            return inFlight;
        }
    }

    public Collection<ResourceLocation> wanted() {
        return List.copyOf(handles.keySet());
    }

    @FunctionalInterface
    public interface Loader<T> {
        T load(ResourceLocation id) throws Exception;
    }

    @FunctionalInterface
    public interface Disposer<T> {
        void dispose(ResourceLocation id, T value);
    }

    /**
     * The shared loader pool, created on the first import and never on a launch that has none.
     *
     * <p>Deliberately small. An import is mostly single-threaded work, but the one expensive step --
     * BC7 encoding an atlas -- already runs on half the cores inside the encoder, so a wide pool would
     * oversubscribe the machine rather than load anything sooner. Daemon threads, because a model that
     * is still importing must never be a reason the game will not close.
     */
    private static final class Pool {
        static final ExecutorService LOADERS = Executors.newFixedThreadPool(
                Mth.clamp(Runtime.getRuntime()
                        .availableProcessors() - 1, 1, 4),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable work) {
                        Thread thread = new Thread(work,
                                "GemRender model loader #" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                });
    }

    public static final class Handle<T> {
        private final ModelCache<T> cache;
        private final ResourceLocation id;

        private volatile int loadedGeneration = -1;
        @Nullable
        private volatile T value;
        private volatile boolean failed;

        private int pendingGeneration = -1;
        @Nullable
        private CompletableFuture<Void> pending;

        private Handle(ModelCache<T> cache, ResourceLocation id) {
            this.cache = cache;
            this.id = id;
        }

        public ResourceLocation id() {
            return id;
        }

        /**
         * The asset if it is loaded; otherwise {@code null}, having started loading it.
         *
         * <p>Asking again next frame is the whole protocol. The import is started once and a second
         * caller joins the first rather than starting another, so a screen full of the same model asks
         * for one import and a broken asset is parsed once and not once a frame.
         */
        @Nullable
        public T get() {
            if (loadedGeneration == cache.generation) {
                return value;
            }

            request();

            // Re-read rather than returning null outright: on a synchronous executor the import has
            // already finished by now, and a caller that could have had the asset should have it.
            return loadedGeneration == cache.generation ? value : null;
        }

        /**
         * The asset, waiting for the import if one is running.
         *
         * <p>For a caller that genuinely cannot proceed without it -- a test, a command reporting state.
         * Never call it from a loader thread, and never from the render thread in a frame: that is the
         * stall this class exists to remove.
         */
        @Nullable
        public T getBlocking() {
            request().join();
            return loadedGeneration == cache.generation ? value : null;
        }

        /**
         * The import for the current generation: the one already running, or one started here.
         */
        CompletableFuture<Void> request() {
            int generation = cache.generation;
            if (loadedGeneration == generation) {
                return COMPLETED;
            }

            synchronized (this) {
                if (loadedGeneration == generation) {
                    return COMPLETED;
                }

                CompletableFuture<Void> running = pending;
                if (running != null && pendingGeneration == generation) {
                    return running;
                }

                if (!cache.beginLoad()) {
                    return COMPLETED;
                }

                pendingGeneration = generation;
                CompletableFuture<Void> started = CompletableFuture
                        .runAsync(() -> load(generation), cache.executor)
                        .whenComplete((ignored, error) -> cache.endLoad());
                pending = started;
                return started;
            }
        }

        private void load(int generation) {
            T loaded = null;
            boolean broke = false;

            try {
                loaded = cache.loader.load(id);
            } catch (Exception | LinkageError e) {
                GemRender.LOGGER.error("Could not load {}; it will not render until the next resource "
                        + "reload", id, e);
                broke = true;
            }

            publish(generation, loaded, broke);
        }

        private synchronized void publish(int generation, @Nullable T loaded, boolean broke) {
            if (cache.generation != generation) {
                // Unreachable: a reload quiesces, and a quiesced cache starts no import. Left in place
                // because the alternative to noticing is publishing into a generation nothing will ever
                // dispose. The value is dropped rather than disposed -- disposal frees GL objects and
                // this is a loader thread, where that is an assertion failure, not a leak.
                GemRender.LOGGER.error("Dropping {} imported for generation {}, now {}", id, generation,
                        cache.generation);
                return;
            }

            value = loaded;
            failed = broke;
            loadedGeneration = generation;
        }

        public boolean isLoaded() {
            return loadedGeneration == cache.generation && value != null;
        }

        /**
         * Whether an import is running for this handle right now.
         */
        public synchronized boolean isLoading() {
            return loadedGeneration != cache.generation && pendingGeneration == cache.generation;
        }

        public boolean hasFailed() {
            return loadedGeneration == cache.generation && failed;
        }

        private synchronized boolean dispose() {
            T disposing = value;
            value = null;
            failed = false;
            loadedGeneration = -1;
            pendingGeneration = -1;
            pending = null;

            if (disposing == null) {
                return false;
            }
            cache.disposer.dispose(id, disposing);
            return true;
        }
    }
}
