package com.wf.gemrender.volume;

public final class Volume {
    private final int slot;

    private VolumeStyle style;

    private float extentX = 1.0f;

    private float extentY = 1.0f;

    private float extentZ = 1.0f;

    private float fade = 1.0f;

    private float seed;

    private VolumeField field;

    private VolumeShape shape = VolumeShape.ELLIPSOID;
    private float time;
    private float flow;
    private float billow = 0.1f;
    private float temperature;
    private float emission;
    private int emissionTint = 0xFFFFFF;

    private boolean closed;

    private boolean hidden;

    private Volume(int slot, VolumeStyle style) {
        this.slot = slot;
        this.style = style;
    }

    public static Volume create(VolumeStyle style) {
        Volume volume = new Volume(VolumeBuffer.getInstance()
                .allocate(), style);
        volume.flush();
        return volume;
    }

    public int slot() {
        return slot;
    }

    public Volume style(VolumeStyle value) {
        style = value;
        flush();
        return this;
    }

    public Volume extent(float x, float y, float z) {
        extentX = x;
        extentY = y;
        extentZ = z;
        flush();
        return this;
    }

    public Volume fade(float value) {
        fade = value;
        flush();
        return this;
    }

    public Volume seed(float value) {
        seed = value;
        flush();
        return this;
    }

    public Volume field(VolumeField value) {
        field = value;
        flush();
        return this;
    }

    public VolumeField field() {
        return field;
    }

    /** Shapes, animation and heat are drawn by {@link VolumeModels#effect()}. */
    public Volume shape(VolumeShape value) {
        shape = java.util.Objects.requireNonNull(value);
        flush();
        return this;
    }

    /** Effect-local seconds and roll speed in radians/second; independent of world uptime. */
    public Volume animate(float seconds, float roll) {
        if (!Float.isFinite(seconds) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("Volume animation must be finite");
        }
        time = seconds;
        flow = roll;
        flush();
        return this;
    }

    /** Surface displacement as a fraction of the smallest extent, 0..0.15; 0 gives a smooth shell. */
    public Volume billow(float value) {
        if (!Float.isFinite(value) || value < 0.0f || value > 0.15f) {
            throw new IllegalArgumentException("Volume billow must be in 0..0.15");
        }
        billow = value;
        flush();
        return this;
    }

    /** Temperature 0..1 (red through white), and nonnegative HDR emission strength. */
    public Volume heat(float value, float strength) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f
                || !Float.isFinite(strength) || strength < 0.0f) {
            throw new IllegalArgumentException("Volume heat needs temperature 0..1 and finite, nonnegative strength");
        }
        temperature = value;
        emission = strength;
        flush();
        return this;
    }

    public Volume emissionTint(int rgb) {
        emissionTint = rgb;
        flush();
        return this;
    }

    public VolumeStyle style() {
        return style;
    }

    public float fade() {
        return fade;
    }

    public float minExtent() {
        return Math.min(extentX, Math.min(extentY, extentZ));
    }

    /** Hidden: drawn with fade 0, {@link #fade()} kept ({@link com.wf.gemrender.medium.CameraMedium}). */
    public Volume hidden(boolean value) {
        if (hidden != value) {
            hidden = value;
            flush();
        }
        return this;
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        if (field != null) {
            field.close();
            field = null;
        }
        VolumeBuffer.getInstance()
                .release(slot);
    }

    private void flush() {
        if (closed) {
            return;
        }

        VolumeAtlas atlas = VolumeAtlas.getInstance();
        VolumeField current = field;

        VolumeBuffer.getInstance()
                .write(slot, style, extentX, extentY, extentZ, hidden ? 0.0f : fade, seed,
                        current == null ? 0.0f : atlas.originU(current.tile()),
                        current == null ? 0.0f : atlas.originV(current.tile()),
                        current == null ? 0.0f : atlas.originW(current.tile()),
                        current == null ? 0.0f : atlas.scale());
        VolumeBuffer.getInstance().writeEffect(slot, shape, time, flow, billow, temperature,
                ((emissionTint >> 16) & 255) / 255.0f * emission,
                ((emissionTint >> 8) & 255) / 255.0f * emission,
                (emissionTint & 255) / 255.0f * emission);
    }
}
