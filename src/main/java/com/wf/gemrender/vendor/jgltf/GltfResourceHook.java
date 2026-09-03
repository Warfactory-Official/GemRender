package com.wf.gemrender.vendor.jgltf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wf.gemrender.vendor.jgltf.model.io.Buffers;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Lets a glTF asset point at Minecraft resources instead of relative URIs.
 *
 * <p>An asset may carry {@code "extras": { "resourceLocation": "namespace:path" }} on a buffer or an
 * image, and the model creators will resolve it through the client resource manager rather than
 * resolving the URI relative to the file. That is what makes a glTF shippable inside a resource pack:
 * textures and .bin buffers become ordinary pack files that resource pack authors can override.
 *
 * <p>This replaces the equivalent hook that MCglTF patched directly into its vendored copy of jgltf.
 * Keeping it behind this class is what allows the vendored parser to stay otherwise unmodified, so it
 * can be re-synced against upstream jgltf without replaying the patch by hand.
 *
 * <p>Client only: resolution goes through {@link Minecraft#getResourceManager()}. Parsing a glTF that
 * uses the hook on a dedicated server will fail, which is intended -- GemRender is a renderer, and no
 * server-side code has any business loading mesh data.
 */
public final class GltfResourceHook {
	/** The {@code extras} key naming a Minecraft resource. Matches MCglTF's, so assets stay portable. */
	public static final String RESOURCE_LOCATION = "resourceLocation";

	private static final Map<ResourceLocation, ByteBuffer> CACHE = new ConcurrentHashMap<>();

	private GltfResourceHook() {
	}

	public static ResourceLocation resourceLocationFromString(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		return ResourceLocation.parse(s);
	}

	/**
	 * Reads a {@code .bin} buffer named by an image's or buffer's {@code extras.resourceLocation}.
	 *
	 * <p>Buffers only. An <em>image</em> that uses the hook is resolved by name and never read here:
	 * GemRender hands the {@link ResourceLocation} to Flywheel as a texture binding and Minecraft loads the
	 * pixels through its own texture manager, so pulling the PNG bytes in as well would be a second
	 * resident copy that nothing reads. See {@code GltfModelCreatorV2.initImageModels}.
	 *
	 * <p>The returned buffer is shared and cached: callers must slice rather than consume it.
	 */
	public static ByteBuffer getBufferResource(ResourceLocation location) {
		return CACHE.computeIfAbsent(location, loc -> {
			try (InputStream in = Minecraft.getInstance()
					.getResourceManager()
					.getResourceOrThrow(loc)
					.open()) {
				return Buffers.create(in.readAllBytes());
			} catch (IOException e) {
				throw new IllegalStateException("Failed to read glTF resource " + loc, e);
			}
		});
	}

	/** Drops cached resources. Call on resource reload, or pack changes will not take effect. */
	public static void clearCache() {
		CACHE.clear();
	}
}
