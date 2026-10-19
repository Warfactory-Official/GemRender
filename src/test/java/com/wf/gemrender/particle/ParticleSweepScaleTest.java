package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a burst of a thousand colliding particles costs, and that it is still a burst of a thousand.
 *
 * <p>The number that matters is not in here — real chunk lookups are what a real level charges, and this
 * stands a flat world up out of an array. What it does measure honestly is the part the sweep controls:
 * how many blocks it asks about, how many times it asks about the same one, and what the walk costs around
 * that. The clipping probe below is the obvious implementation of the same thing, kept as the thing to beat.
 */
class ParticleSweepScaleTest {
	/**
	 * {@link Blocks} cannot class-initialise until the registries exist, and only the 1.21.1 test rig
	 * stands them up on its own — on 1.20.1 the first {@code Blocks.STONE} is an
	 * {@code ExceptionInInitializerError} out of {@code Registries}, and every test here fails before it
	 * has run a line.
	 *
	 * <p>The bootstrap is allowed to throw. On 1.20.1 it reaches Forge's {@code NetworkHooks}, which
	 * wants a mod-loading environment a unit test has no business standing up — but it has already built
	 * the block registry by then, which is the whole of what this file needs. So the assertion, not the
	 * absence of a throw, is what decides whether the bootstrap did its job.
	 */
	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		try {
			Bootstrap.bootStrap();
		} catch (Throwable pastWhatTheRegistriesNeeded) {
			// Checked below rather than here.
		}
		assertThat(Blocks.STONE.defaultBlockState().isAir()).isFalse();
	}

	private static final int PARTICLES = 1000;

	private static final int FLOOR = 64;

	private static final int WARMUP_ROUNDS = 3;

	private static final int MEASURED_ROUNDS = 5;

	/** A world that is solid below {@link #FLOOR} and empty above it, counting what is asked of it. */
	private static final class FlatLevel implements BlockGetter {
		int lookups;

		private final BlockState stone = Blocks.STONE.defaultBlockState();

		private final BlockState air = Blocks.AIR.defaultBlockState();

		@Nullable
		@Override
		public BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			lookups++;
			return pos.getY() < FLOOR ? stone : air;
		}

		@Override
		public FluidState getFluidState(BlockPos pos) {
			return getBlockState(pos).getFluidState();
		}

		@Override
		public int getHeight() {
			return 384;
		}

		// 26.1 renamed LevelHeightAccessor#getMinBuildHeight to getMinY.
		//? if >=26.1 {
		/*@Override
		public int getMinY() {
			return -64;
		}
		*///?} else {
		@Override
		public int getMinBuildHeight() {
			return -64;
		}
		//?}
	}

	/** The obvious probe: hand every segment to the level and let it do the walking. */
	private static ParticleCollision.Probe clipping(BlockGetter level) {
		return (fx, fy, fz, tx, ty, tz, into) -> {
			Vec3 from = new Vec3(fx, fy, fz);
			Vec3 to = new Vec3(tx, ty, tz);

			//? if <1.21 {
			/*BlockHitResult hit = BlockGetter.traverseBlocks(from, to, CollisionContext.empty(),
					(context, pos) -> {
						BlockState state = level.getBlockState(pos);
						return level.clipWithInteractionOverride(from, to, pos,
								state.getCollisionShape(level, pos, context), state);
					}, context -> {
						Vec3 delta = from.subtract(to);
						return BlockHitResult.miss(to, Direction.getNearest(delta.x, delta.y, delta.z),
								BlockPos.containing(to));
					});
			*///?} else {
			BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, CollisionContext.empty()));
			//?}
			if (hit.getType() == HitResult.Type.MISS) {
				return false;
			}

			double length = from.distanceTo(to);
			into.fraction = length > 1.0e-6 ? (float) (from.distanceTo(hit.getLocation()) / length) : 0.0f;
			into.normal = LevelContactProbe.encode(hit.getDirection());
			return true;
		};
	}

	private static ParticleStyle rubble() {
		return ParticleStyle.builder()
				.drag(ParticleStyle.dragFromPerTickFactor(0.99f))
				.gravity(20.0f)
				.stopsOnContact()
				.build();
	}

	private static int burst(ParticleCollision.Probe probe, ParticleStyle style, long seed) {
		return burst(probe, style, seed, PARTICLES);
	}

	/** One explosion's worth of debris: a cone of velocities out of a single point above the floor. */
	private static int burst(ParticleCollision.Probe probe, ParticleStyle style, long seed, int count) {
		java.util.Random random = new java.util.Random(seed);
		int landed = 0;

		for (int i = 0; i < count; i++) {
			double elevation = Math.toRadians(35.0 + random.nextDouble() * 40.0);
			double azimuth = random.nextDouble() * Math.PI * 2.0;
			double speed = 8.0 + random.nextDouble() * 8.0;

			float vx = (float) (speed * Math.cos(elevation) * Math.cos(azimuth));
			float vy = (float) (speed * Math.sin(elevation));
			float vz = (float) (speed * Math.cos(elevation) * Math.sin(azimuth));

			ParticleCollision.Contact contact = ParticleCollision.predict(probe, style,
					0.0, FLOOR + 2.0, 0.0, vx, vy, vz, 5.0f, 0.25f);
			if (contact.hits()) {
				landed++;
			}
		}

		return landed;
	}

	private static double best(java.util.function.Supplier<ParticleCollision.Probe> probes, ParticleStyle style) {
		for (int round = 0; round < WARMUP_ROUNDS; round++) {
			burst(probes.get(), style, round);
		}

		double best = Double.MAX_VALUE;
		for (int round = 0; round < MEASURED_ROUNDS; round++) {
			ParticleCollision.Probe probe = probes.get();
			long start = System.nanoTime();
			burst(probe, style, 99);
			best = Math.min(best, (System.nanoTime() - start) / 1.0e6);
		}
		return best;
	}

	@Test
	@DisplayName("a thousand colliding particles all find the ground, and the walking probe costs no more than twice clipping")
	void aThousandParticlesLand() {
		FlatLevel level = new FlatLevel();
		ParticleStyle style = rubble();

		int landed = burst(LevelContactProbe.of(level), style, 99);
		assertThat(landed).as("a burst fired upward over solid ground has to come down")
				.isEqualTo(PARTICLES);

		// The two probes are answering the same question, so they must answer it the same way.
		int clipped = burst(clipping(level), style, 99);
		assertThat(clipped).isEqualTo(landed);

		// Wall-clock on a shared runner: a noisy neighbour can push one measurement past the factor, so the
		// pathological case has to show on every attempt.
		double walking = 0;
		double clippingMs = 0;
		for (int attempt = 0; attempt < 3; attempt++) {
			walking = best(() -> LevelContactProbe.of(level), style);
			clippingMs = best(() -> clipping(level), style);

			System.out.printf("[gemrender] %d colliding spawns: walking probe %.2f ms, clipping probe %.2f ms "
					+ "(%.1fx)%n", PARTICLES, walking, clippingMs, clippingMs / walking);
			if (walking < clippingMs * 2.0) {
				break;
			}
		}

		// The flat array level makes a lookup nearly free, so clipping is at its cheapest here and a shared
		// runner can put the walk either side of it. The factor catches a pathological walk; the lookup
		// count test below is what pins the win.
		assertThat(walking).as("the cached walk must not cost a multiple of handing every segment to the level")
				.isLessThan(clippingMs * 2.0);
	}

	@Test
	@DisplayName("a burst asks the level about far fewer blocks than it has particles")
	void theCacheCollapsesTheLookups() {
		ParticleStyle style = rubble();

		FlatLevel walked = new FlatLevel();
		burst(LevelContactProbe.of(walked), style, 99);

		FlatLevel clipped = new FlatLevel();
		burst(clipping(clipped), style, 99);

		System.out.printf("[gemrender] %d colliding spawns: walking probe %d block lookups, clipping probe %d "
				+ "(%.1fx)%n", PARTICLES, walked.lookups, clipped.lookups,
				clipped.lookups / (double) walked.lookups);

		assertThat(walked.lookups).as("the walk must ask for a fraction of what clipping asks for")
				.isLessThan(clipped.lookups / 10);

		// The point of the cache: a burst leaving one point crosses the same ground over and over, so what
		// the level is asked is bounded by the volume the burst covers rather than by how many particles it
		// has. Doubling the particles fills the same volume more densely, so the lookups barely move -- which
		// is the property that decides whether ten thousand would also be affordable.
		FlatLevel dense = new FlatLevel();
		burst(LevelContactProbe.of(dense), style, 99, PARTICLES * 2);

		System.out.printf("[gemrender] %d colliding spawns: %d block lookups (%.2fx the lookups for %d)%n",
				PARTICLES * 2, dense.lookups, dense.lookups / (double) walked.lookups, PARTICLES);

		assertThat(dense.lookups).as("twice the particles must not cost twice the lookups")
				.isLessThan(walked.lookups * 3 / 2);
	}

	@Test
	@DisplayName("a particle that flies straight is not cut into segments it has no curve to need")
	void straightFlightIsOneSegment() {
		int[] segments = new int[1];
		ParticleCollision.Probe counting = (fx, fy, fz, tx, ty, tz, into) -> {
			segments[0]++;
			return false;
		};

		ParticleCollision.predict(counting, ParticleStyle.builder()
				.stopsOnContact()
				.build(), 0.0, FLOOR + 2.0, 0.0, 40.0f, 0.0f, 0.0f, 5.0f, 0.0f);
		assertThat(segments[0]).as("nothing bends this one, however far it goes")
				.isEqualTo(1);

		segments[0] = 0;
		ParticleCollision.predict(counting, rubble(), 0.0, FLOOR + 2.0, 0.0, 12.0f, 12.0f, 0.0f, 5.0f, 0.0f);
		assertThat(segments[0]).as("a ballistic arc has to be followed")
				.isGreaterThan(8);
	}

	@Test
	@DisplayName("the two probes agree on where a particle stops, not just on whether it stopped")
	void probesAgreeOnTheContact() {
		FlatLevel level = new FlatLevel();
		ParticleStyle style = rubble();

		java.util.Random random = new java.util.Random(7);
		ParticleCollision.Probe walking = LevelContactProbe.of(level);
		ParticleCollision.Probe clipping = clipping(level);

		for (int i = 0; i < 200; i++) {
			float vx = (float) (random.nextGaussian() * 6.0);
			float vy = (float) (2.0 + random.nextDouble() * 10.0);
			float vz = (float) (random.nextGaussian() * 6.0);

			ParticleCollision.Contact a = ParticleCollision.predict(walking, style,
					0.0, FLOOR + 2.0, 0.0, vx, vy, vz, 5.0f, 0.0f);
			ParticleCollision.Contact b = ParticleCollision.predict(clipping, style,
					0.0, FLOOR + 2.0, 0.0, vx, vy, vz, 5.0f, 0.0f);

			assertThat(a.contactAge()).as("contact age for velocity %.2f %.2f %.2f", vx, vy, vz)
					.isCloseTo(b.contactAge(), org.assertj.core.api.Assertions.within(1.0e-4f));
			assertThat(a.normal()).isEqualTo(b.normal());
		}
	}
}
