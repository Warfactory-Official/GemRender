package com.wf.gemrender.particle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;

public final class ParticleEmitter {
    /**
     * Decal offset bound. Float offset from origin: half-ulp at 2048 = 2^-13 = {@link ParticleShapes#DECAL_LIFT}/16;
     * at 16384 it equals half the lift => z-fight.
     */
    public static final int DECAL_REACH = 2048;

    private final int style;

    private final int base;

    private final int capacity;

    private final Vec3i origin;

    private int cursor;

    private float latestDeath;

    private boolean closed;

    // Probe cache kept only while the clock stands still: an older cache answers for blocks since mined.
    private ParticleCollision.Probe probe;

    private BlockGetter probeLevel;

    private float probeClock = Float.NaN;

    private ParticleEmitter(int style, int base, int capacity, Vec3i origin) {
        this.style = style;
        this.base = base;
        this.capacity = capacity;
        this.origin = origin;
    }

    public static ParticleEmitter create(int style, int capacity, double x, double y, double z) {
        Vec3i origin = BlockPos.containing(x, y, z);
        int base = ParticleBuffer.getInstance()
                .allocate(capacity);
        return new ParticleEmitter(style, base, capacity, origin);
    }

    public int style() {
        return style;
    }

    public int slotBase() {
        return base;
    }

    public int capacity() {
        return capacity;
    }

    public Vec3i origin() {
        return origin;
    }

    public ParticleCollision.Contact spawn(double x, double y, double z, double velocityX, double velocityY,
                                           double velocityZ, float life, float sizeScale) {
        return spawn(x, y, z, velocityX, velocityY, velocityZ, life, sizeScale, 0.0f, 1.0f);
    }

    public ParticleCollision.Contact spawn(double x, double y, double z, double velocityX, double velocityY,
                                           double velocityZ, float life, float sizeScale, float spinPhase,
                                           float tintScale) {
        return spawn(x, y, z, velocityX, velocityY, velocityZ, life, sizeScale, spinPhase, tintScale,
                ParticleLook.NONE);
    }

    /** @param look {@link ParticleLook} packing: per-particle colour and light */
    public ParticleCollision.Contact spawn(double x, double y, double z, double velocityX, double velocityY,
                                           double velocityZ, float life, float sizeScale, float spinPhase,
                                           float tintScale, int look) {
        ParticleCollision.Contact contact = ParticleCollision.none(life);
        write(x, y, z, velocityX, velocityY, velocityZ, sizeScale, spinPhase, tintScale, contact, look, true);
        return contact;
    }

    /**
     * A {@link GemRenderParticleTypes#DECAL}: flat on the plane through {@code (x, y, z)} facing
     * {@code (normalX, normalY, normalZ)}, rolled by {@code roll}. Never swept, never moves.
     *
     * @throws IllegalArgumentException {@code (x, y, z)} more than {@link #DECAL_REACH} from {@link #origin()} on
     *                                  any axis
     */
    public void spawnDecal(double x, double y, double z, double normalX, double normalY, double normalZ, float life,
                           float sizeScale, float roll, int look) {
        requireDecalReach(origin, x, y, z);
        write(x, y, z, normalX, normalY, normalZ, sizeScale, roll, 1.0f, ParticleCollision.none(life), look, false);
    }

    static void requireDecalReach(Vec3i origin, double x, double y, double z) {
        if (Math.abs(x - origin.getX()) > DECAL_REACH || Math.abs(y - origin.getY()) > DECAL_REACH
                || Math.abs(z - origin.getZ()) > DECAL_REACH) {
            throw new IllegalArgumentException("decal at (%s, %s, %s) beyond %d blocks of emitter origin %s"
                    .formatted(x, y, z, DECAL_REACH, origin));
        }
    }

    /**
     * Spawns a particle that knows what it is going to run into.
     *
     * <p>The arc is swept against {@code level} once, here, and what the sweep finds rides in the particle's
     * own slot, so the flight still costs nothing per frame, and a style with no
     * {@link ContactResponse} does not pay for the sweep at all.
     *
     * <p>Reads blocks, so call it from the thread that owns the level.
     */
    public ParticleCollision.Contact spawn(BlockGetter level, double x, double y, double z, double velocityX,
                                           double velocityY, double velocityZ, float life, float sizeScale) {
        return spawn(level, x, y, z, velocityX, velocityY, velocityZ, life, sizeScale, 0.0f, 1.0f, 0.0f);
    }

    /**
     * @param radius how far from a surface the particle's centre comes to rest. A billboard wants 0; a mesh
     *               wants about half its extent, or it lands half inside the floor
     */
    public ParticleCollision.Contact spawn(BlockGetter level, double x, double y, double z, double velocityX,
                                           double velocityY, double velocityZ, float life, float sizeScale,
                                           float spinPhase, float tintScale, float radius) {
        return spawn(probeFor(level), x, y, z, velocityX, velocityY, velocityZ, life, sizeScale, spinPhase,
                tintScale, radius, ParticleLook.NONE);
    }

    public ParticleCollision.Contact spawn(BlockGetter level, double x, double y, double z, double velocityX,
                                           double velocityY, double velocityZ, float life, float sizeScale,
                                           float spinPhase, float tintScale, float radius, int look) {
        return spawn(probeFor(level), x, y, z, velocityX, velocityY, velocityZ, life, sizeScale, spinPhase,
                tintScale, radius, look);
    }

    /**
     * The same, against a probe the caller owns.
     *
     * <p>Worth reaching for when a single burst is large: a probe remembers the blocks it has already looked
     * at, and a thousand particles leaving one point walk mostly the same ground. Build one with
     * {@link LevelContactProbe#of}, spawn the burst through it, and drop it.
     *
     * @return what the sweep found, so a caller can put something of its own where the particle ends up: a
     *         sound, a decal, or the burst a shattering particle leaves behind. Feed it to
     *         {@link ParticleCollision#positionAt} with the same spawn state to get the place. A particle the
     *         emitter declined to write (it is closed, or the life came back non-positive) still reports its
     *         contact; check {@link ParticleCollision.Contact#hits()} before acting on one.
     */
    public ParticleCollision.Contact spawn(ParticleCollision.Probe probe, double x, double y, double z,
                                           double velocityX, double velocityY, double velocityZ, float life,
                                           float sizeScale, float spinPhase, float tintScale, float radius) {
        return spawn(probe, x, y, z, velocityX, velocityY, velocityZ, life, sizeScale, spinPhase, tintScale, radius,
                ParticleLook.NONE);
    }

    public ParticleCollision.Contact spawn(ParticleCollision.Probe probe, double x, double y, double z,
                                           double velocityX, double velocityY, double velocityZ, float life,
                                           float sizeScale, float spinPhase, float tintScale, float radius,
                                           int look) {
        ParticleStyle particleStyle = ParticleBuffer.getInstance()
                .style(style);

        ParticleCollision.Contact contact = particleStyle == null
                ? ParticleCollision.none(life)
                : ParticleCollision.predict(probe, particleStyle, x, y, z,
                (float) velocityX, (float) velocityY, (float) velocityZ, life, radius);

        write(x, y, z, velocityX, velocityY, velocityZ, sizeScale, spinPhase, tintScale, contact, look, true);
        return contact;
    }

    private ParticleCollision.Probe probeFor(BlockGetter level) {
        float now = ParticleClock.seconds();
        if (probe == null || probeLevel != level || probeClock != now) {
            probe = LevelContactProbe.of(level);
            probeLevel = level;
            probeClock = now;
        }
        return probe;
    }

    private void write(double x, double y, double z, double velocityX, double velocityY, double velocityZ,
                       float sizeScale, float spinPhase, float tintScale, ParticleCollision.Contact contact,
                       int look, boolean thin) {
        if (closed || contact.life() <= 0.0f) {
            return;
        }
        float keep = thin ? ParticleThinning.keep(x, y, z, style, sizeScale) : 1.0f;
        if (keep < 1.0f) {
            if (ParticleThinning.drop(keep)) {
                return;
            }
            sizeScale *= (float) (1.0 / Math.sqrt(keep));
        }

        float now = ParticleClock.seconds();

        ParticleBuffer.getInstance()
                .write(base + cursor,
                        (float) (x - origin.getX()),
                        (float) (y - origin.getY()),
                        (float) (z - origin.getZ()),
                        now,
                        (float) velocityX, (float) velocityY, (float) velocityZ,
                        contact.life(), style, sizeScale, spinPhase, tintScale,
                        contact.contactAge(), contact.normal(), contact.restAge(), look);

        cursor = (cursor + 1) % capacity;
        latestDeath = Math.max(latestDeath, now + contact.life());
    }

    public boolean isIdle() {
        return ParticleClock.seconds() >= latestDeath;
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        ParticleBuffer.getInstance()
                .release(base, capacity);
    }
}
