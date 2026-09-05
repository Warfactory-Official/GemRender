package com.wf.gemrender.volume;

import java.util.Locale;

public enum VolumeQuality {
	LOW(24, 0),
	MEDIUM(48, 4),
	HIGH(96, 6);

	public static final VolumeQuality DEFAULT = parse(System.getProperty("gemrender.volumequality"));

	private final int steps;

	private final int sunSteps;

	VolumeQuality(int steps, int sunSteps) {
		this.steps = steps;
		this.sunSteps = sunSteps;
	}

	public int steps() {
		return steps;
	}

	public int sunSteps() {
		return sunSteps;
	}

	public static VolumeQuality parse(String name) {
		if (name == null || name.isEmpty()) {
			return MEDIUM;
		}
		try {
			return valueOf(name.trim()
					.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return MEDIUM;
		}
	}
}
