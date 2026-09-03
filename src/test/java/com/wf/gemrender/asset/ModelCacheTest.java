package com.wf.gemrender.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ModelCacheTest {

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("gemrender", path);
	}

	private static final class Recorder {
		final AtomicInteger loads = new AtomicInteger();
		final List<ResourceLocation> disposed = Collections.synchronizedList(new ArrayList<>());
		final List<String> failing = Collections.synchronizedList(new ArrayList<>());

		final List<Integer> disposedWhenLoaded = Collections.synchronizedList(new ArrayList<>());

		ModelCache<String> cache() {
			return new ModelCache<>("test", this::load, this::dispose);
		}

		String load(ResourceLocation location) {
			loads.incrementAndGet();
			disposedWhenLoaded.add(disposed.size());
			if (failing.contains(location.getPath())) {
				throw new IllegalStateException("deliberately broken: " + location);
			}
			return location.getPath() + "#" + loads.get();
		}

		void dispose(ResourceLocation location, String value) {
			disposed.add(location);
		}
	}

	@Test
	@DisplayName("one handle and one load per id, however many callers there are")
	void assetsAreShared() {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		ModelCache.Handle<String> first = cache.handle(id("a"));
		ModelCache.Handle<String> second = cache.handle(id("a"));

		assertThat(second).isSameAs(first);
		assertThat(first.get()).isSameAs(second.get());
		assertThat(cache.get(id("a"))).isSameAs(first.get());
		assertThat(recorder.loads).hasValue(1);
	}

	@Test
	@DisplayName("declaring an asset does not load it")
	void handlesAreLazy() {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		cache.handle(id("a"));

		assertThat(recorder.loads).hasValue(0);
		assertThat(cache.loadedCount()).isZero();
		assertThat(cache.wanted()).containsExactly(id("a"));
	}

	@Test
	@DisplayName("a broken asset yields null instead of an exception")
	void failuresDoNotPropagate() {
		Recorder recorder = new Recorder();
		recorder.failing.add("broken");
		ModelCache<String> cache = recorder.cache();

		assertThat(cache.get(id("broken"))).isNull();
		assertThat(cache.handle(id("broken"))
				.hasFailed()).isTrue();
		assertThat(cache.failedCount()).isEqualTo(1);
		assertThat(cache.loadedCount()).isZero();
	}

	@Test
	@DisplayName("a failure is cached, so a broken asset is not re-parsed every frame")
	void failuresAreNotRetried() {
		Recorder recorder = new Recorder();
		recorder.failing.add("broken");
		ModelCache<String> cache = recorder.cache();

		for (int i = 0; i < 50; i++) {
			assertThat(cache.get(id("broken"))).isNull();
		}

		assertThat(recorder.loads)
				.as("a visual rebuilt every frame would otherwise parse a broken file every frame")
				.hasValue(1);
	}

	@Test
	@DisplayName("a reload retries what failed, because that is when the answer can have changed")
	void reloadRetriesFailures() {
		Recorder recorder = new Recorder();
		recorder.failing.add("broken");
		ModelCache<String> cache = recorder.cache();

		assertThat(cache.get(id("broken"))).isNull();

		recorder.failing.clear();
		cache.reload();

		assertThat(cache.get(id("broken"))).isNotNull();
		assertThat(cache.failedCount()).isZero();
	}

	@Test
	@DisplayName("a reload disposes every loaded value exactly once")
	void reloadDisposesWhatWasLoaded() {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		cache.get(id("a"));
		cache.get(id("b"));
		cache.reload();

		assertThat(recorder.disposed).containsExactlyInAnyOrder(id("a"), id("b"));
	}

	@Test
	@DisplayName("a reload does not dispose what was never loaded")
	void reloadDoesNotDisposeUnloadedHandles() {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		cache.handle(id("a"));
		cache.reload();

		assertThat(recorder.disposed)
				.as("nothing was loaded before the reload, so nothing was there to free")
				.isEmpty();
	}

	@Test
	@DisplayName("every old value is disposed before any new one is loaded")
	void disposalCompletesBeforeReloading() {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		cache.get(id("a"));
		cache.get(id("b"));
		cache.get(id("c"));
		recorder.disposedWhenLoaded.clear();

		cache.reload();

		assertThat(recorder.disposedWhenLoaded)
				.as("each load of the new generation should see all 3 old values already disposed")
				.containsExactly(3, 3, 3);
	}

	@Test
	@DisplayName("a handle held across a reload returns the new model, not the freed one")
	void handlesSurviveReloads() {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		ModelCache.Handle<String> handle = cache.handle(id("a"));
		String before = handle.get();

		cache.reload();

		assertThat(handle.get()).isNotEqualTo(before);
		assertThat(cache.handle(id("a")))
				.as("the handle itself is stable, only what is behind it changes")
				.isSameAs(handle);
	}

	@Test
	@DisplayName("a reload loads everything wanted, including handles nothing has asked for yet")
	void reloadLoadsEverythingDeclared() {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		cache.handle(id("a"));
		cache.handle(id("b"));
		assertThat(recorder.loads).hasValue(0);

		cache.reload();

		assertThat(recorder.loads).hasValue(2);
		assertThat(cache.loadedCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("the generation counts reloads")
	void generationAdvances() {
		ModelCache<String> cache = new Recorder().cache();

		assertThat(cache.generation()).isZero();
		assertThat(cache.reload()).isEqualTo(1);
		assertThat(cache.reload()).isEqualTo(2);
		assertThat(cache.generation()).isEqualTo(2);
	}

	@Test
	@DisplayName("many threads racing for a cold asset load it once")
	void concurrentGetsLoadOnce() throws InterruptedException {
		Recorder recorder = new Recorder();
		ModelCache<String> cache = recorder.cache();

		int threads = 16;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		List<String> results = Collections.synchronizedList(new ArrayList<>());

		for (int i = 0; i < threads; i++) {
			new Thread(() -> {
				try {
					start.await();
					results.add(cache.get(id("a")));
				} catch (InterruptedException e) {
					Thread.currentThread()
							.interrupt();
				} finally {
					done.countDown();
				}
			}).start();
		}

		start.countDown();
		assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

		assertThat(recorder.loads).hasValue(1);
		assertThat(results).hasSize(threads)
				.containsOnly(results.get(0));
	}
}
