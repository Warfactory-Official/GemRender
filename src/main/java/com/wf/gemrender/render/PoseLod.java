package com.wf.gemrender.render;

public final class PoseLod {
	public static final float DEFAULT_NEAR_BLOCKS = 32.0f;

	public static final int MAX_LEVEL = 8;

	public static final PoseLod OFF = new PoseLod(0.0f, 0.0f);

	private static final PoseLod INSTANCE = new PoseLod(
			Float.parseFloat(System.getProperty("gemrender.poselod",
					Float.toString(DEFAULT_NEAR_BLOCKS))),
			Float.parseFloat(System.getProperty("gemrender.posefreeze", "0")));

	private final float nearBlocks;
	private final float freezeBlocks;

	private final double nearSquared;
	private final double freezeSquared;

	public PoseLod(float nearBlocks, float freezeBlocks) {
		this.nearBlocks = nearBlocks;
		this.freezeBlocks = freezeBlocks;
		this.nearSquared = (double) nearBlocks * nearBlocks;
		this.freezeSquared = (double) freezeBlocks * freezeBlocks;
	}

	public static PoseLod getInstance() {
		return INSTANCE;
	}

	public float nearBlocks() {
		return nearBlocks;
	}

	public float freezeBlocks() {
		return freezeBlocks;
	}

	public boolean enabled() {
		return nearBlocks > 0.0f;
	}

	public int levelAt(double distanceSquared) {
		if (!enabled() || distanceSquared <= nearSquared) {
			return 0;
		}

		int level = (int) Math.floor(Math.log(distanceSquared / nearSquared) / (2.0 * Math.log(2.0)));
		return Math.min(MAX_LEVEL, Math.max(0, level));
	}

	public boolean frozenAt(double distanceSquared) {
		return freezeBlocks > 0.0f && distanceSquared > freezeSquared;
	}

	public static int quantumScale(int level) {
		return 1 << Math.min(MAX_LEVEL, Math.max(0, level));
	}

	@Override
	public String toString() {
		if (!enabled()) {
			return "off";
		}
		return "near" + nearBlocks + "/freeze" + (freezeBlocks > 0.0f ? freezeBlocks : "never");
	}
}
