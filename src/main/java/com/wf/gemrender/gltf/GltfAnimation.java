package com.wf.gemrender.gltf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.wf.gemrender.GemRender;
import com.wf.gemrender.vendor.mcgltf.animation.GltfAnimationCreator.ChannelBinding;

/** A clip: an ordered list of {@link PoseDriver}s, with value equality so poses can be shared. */
public final class GltfAnimation {
	private final String name;
	private final List<PoseDriver> drivers;
	private final float duration;
	private final int hash;

	public GltfAnimation(String name, List<PoseDriver> drivers, float duration) {
		this.name = name;
		this.drivers = List.copyOf(drivers);
		this.duration = duration;
		this.hash = Objects.hash(name, this.drivers, duration);
	}

	public static GltfAnimation of(String name, List<ChannelBinding> bindings, NodeTable table) {
		List<PoseDriver> drivers = new ArrayList<>(bindings.size());
		float duration = 0.0f;

		for (ChannelBinding binding : bindings) {
			int slot = table.slotOf(binding.node());
			if (slot < 0) {
				GemRender.LOGGER.warn("Animation '{}' targets a node that is not part of the model; skipping",
						name);
				continue;
			}

			int offset = table.offsetFor(slot, binding.path());

			int count = Math.min(binding.components(), table.componentsFor(slot, binding.path()));
			if (offset < 0 || count <= 0) {
				GemRender.LOGGER.warn("Animation '{}' drives '{}' on a node with no room for it; skipping",
						name, binding.path());
				continue;
			}

			PoseChannel channel = new PoseChannel(binding.channel(), offset, count);
			drivers.add(channel);
			duration = Math.max(duration, channel.cycleSeconds());
		}

		return new GltfAnimation(name, drivers, duration);
	}

	public static GltfAnimation procedural(String name, PoseDriver... drivers) {
		float duration = 0.0f;
		for (PoseDriver driver : drivers) {
			duration = Math.max(duration, driver.cycleSeconds());
		}
		return new GltfAnimation(name, List.of(drivers), duration);
	}

	/**
	 * A procedural clip of a stated length, rather than the length its longest driver happens to want.
	 *
	 * <p>Duration is what {@link AnimationDrive} and {@link AnimationPhase} scale a parameter onto, so a
	 * clip whose drivers disagree about their natural cycles needs it said out loud. A bite that lasts
	 * one unit but whose head tilt is half of a two-unit sine is the case: taking the longest driver
	 * would stretch the parameter over twice the clip and put the peak in the wrong place.
	 */
	public static GltfAnimation procedural(String name, float duration, PoseDriver... drivers) {
		return new GltfAnimation(name, List.of(drivers), duration);
	}

	/**
	 * Appends drivers, widening the clip to whichever of them takes longest.
	 *
	 * <p>Merging is the only way a path with one clock can carry two animations, and it is lossy: the
	 * merged clip runs at the longer period, so this clip's own drivers are put on a loop of their own
	 * to stop them clamping at their last keyframe for the rest of it. Two layers whose periods are not
	 * multiples of each other still cannot be merged exactly, which is the limit of the approach rather
	 * than of this method.
	 */
	public GltfAnimation with(PoseDriver... extra) {
		if (extra.length == 0) {
			return this;
		}

		float merged = duration;
		for (PoseDriver driver : extra) {
			merged = Math.max(merged, driver.cycleSeconds());
		}

		List<PoseDriver> combined = new ArrayList<>(drivers.size() + extra.length);
		for (PoseDriver driver : drivers) {
			combined.add(merged > duration ? Looped.of(driver, duration) : driver);
		}
		combined.addAll(List.of(extra));

		return new GltfAnimation(name, combined, merged);
	}

	public void apply(float timeSeconds, float[] scratch) {
		for (int i = 0; i < drivers.size(); i++) {
			drivers.get(i)
					.apply(timeSeconds, scratch);
		}
	}

	public float loop(float timeSeconds) {
		if (duration <= 0.0f) {
			return 0.0f;
		}
		float wrapped = timeSeconds % duration;
		return wrapped < 0.0f ? wrapped + duration : wrapped;
	}

	public String name() {
		return name;
	}

	public List<PoseDriver> drivers() {
		return drivers;
	}

	public float duration() {
		return duration;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		return other instanceof GltfAnimation that && hash == that.hash
				&& Float.compare(duration, that.duration) == 0 && name.equals(that.name)
				&& drivers.equals(that.drivers);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return "GltfAnimation[" + name + ", " + drivers.size() + " drivers, " + duration + "s]";
	}
}
