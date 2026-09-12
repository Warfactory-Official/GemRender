package com.wf.gemrender.gltf;

public record Looped(PoseDriver inner, float periodSeconds) implements PoseDriver {
    public static PoseDriver of(PoseDriver inner, float periodSeconds) {
        return periodSeconds <= 0.0f ? inner : new Looped(inner, periodSeconds);
    }

    @Override
    public void apply(float timeSeconds, float[] scratch) {
        float wrapped = timeSeconds % periodSeconds;
        inner.apply(wrapped < 0.0f ? wrapped + periodSeconds : wrapped, scratch);
    }

    @Override
    public float cycleSeconds() {
        return periodSeconds;
    }

    @Override
    public int offset() {
        return inner.offset();
    }
}
