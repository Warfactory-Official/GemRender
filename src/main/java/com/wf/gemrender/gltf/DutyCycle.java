package com.wf.gemrender.gltf;

public record DutyCycle(PoseDriver inner, float cycleSeconds, float duty) implements PoseDriver {
    public static PoseDriver of(PoseDriver inner, float duty) {
        if (duty >= 1.0f || inner.cycleSeconds() <= 0.0f) {
            return inner;
        }
        return new DutyCycle(inner, inner.cycleSeconds(), Math.max(0.0f, duty));
    }

    public static float held(float timeSeconds, float cycleSeconds, float duty) {
        if (duty >= 1.0f || cycleSeconds <= 0.0f) {
            return timeSeconds;
        }

        float phase = timeSeconds % cycleSeconds;
        if (phase < 0.0f) {
            phase += cycleSeconds;
        }

        float window = cycleSeconds * duty;
        return phase <= window ? timeSeconds : timeSeconds - phase + window;
    }

    @Override
    public void apply(float timeSeconds, float[] scratch) {
        inner.apply(held(timeSeconds, cycleSeconds, duty), scratch);
    }

    @Override
    public int offset() {
        return inner.offset();
    }
}
