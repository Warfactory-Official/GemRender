package com.wf.gemrender.asset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.wf.gemrender.GemRender;

import net.minecraft.resources.ResourceLocation;

/** Loads each asset once and hands out {@link Handle}s that stay valid across resource reloads. */
public final class ModelCache<T> {
	@FunctionalInterface
	public interface Loader<T> {
		T load(ResourceLocation id) throws Exception;
	}

	@FunctionalInterface
	public interface Disposer<T> {
		void dispose(ResourceLocation id, T value);
	}

	private final String name;
	private final Loader<T> loader;
	private final Disposer<T> disposer;

	private final Map<ResourceLocation, Handle<T>> handles = new ConcurrentHashMap<>();

	private volatile int generation;

	public ModelCache(String name, Loader<T> loader, Disposer<T> disposer) {
		this.name = name;
		this.loader = loader;
		this.disposer = disposer;
	}

	public Handle<T> handle(ResourceLocation id) {
		return handles.computeIfAbsent(id, key -> new Handle<>(this, key));
	}

	@Nullable
	public T get(ResourceLocation id) {
		return handle(id).get();
	}

	public synchronized int reload() {
		List<ResourceLocation> wanted = new ArrayList<>(handles.keySet());

		int disposed = 0;
		for (Handle<T> handle : handles.values()) {
			if (handle.dispose()) {
				disposed++;
			}
		}

		generation++;

		int loaded = 0;
		int failed = 0;
		for (ResourceLocation id : wanted) {
			if (handles.get(id)
					.get() != null) {
				loaded++;
			} else {
				failed++;
			}
		}

		GemRender.LOGGER.info("{} generation {}: disposed {}, reloaded {}{}",
				name, generation, disposed, loaded, failed == 0 ? "" : ", " + failed + " failed");
		return generation;
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

	public Collection<ResourceLocation> wanted() {
		return List.copyOf(handles.keySet());
	}

	public static final class Handle<T> {
		private final ModelCache<T> cache;
		private final ResourceLocation id;

		private volatile int loadedGeneration = -1;
		@Nullable
		private volatile T value;
		private volatile boolean failed;

		private Handle(ModelCache<T> cache, ResourceLocation id) {
			this.cache = cache;
			this.id = id;
		}

		public ResourceLocation id() {
			return id;
		}

		@Nullable
		public T get() {
			if (loadedGeneration == cache.generation) {
				return value;
			}

			synchronized (this) {
				int generation = cache.generation;
				if (loadedGeneration == generation) {
					return value;
				}

				try {
					value = cache.loader.load(id);
					failed = false;
				} catch (Exception | LinkageError e) {
					GemRender.LOGGER.error("Could not load {}; it will not render until the next resource "
							+ "reload", id, e);
					value = null;
					failed = true;
				}

				loadedGeneration = generation;
				return value;
			}
		}

		public boolean isLoaded() {
			return loadedGeneration == cache.generation && value != null;
		}

		public boolean hasFailed() {
			return loadedGeneration == cache.generation && failed;
		}

		private synchronized boolean dispose() {
			T disposing = value;
			value = null;
			failed = false;
			loadedGeneration = -1;

			if (disposing == null) {
				return false;
			}
			cache.disposer.dispose(id, disposing);
			return true;
		}
	}
}
