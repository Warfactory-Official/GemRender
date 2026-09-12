package dev.engine_room.flywheel.backend.engine.uniform;

import org.joml.Vector3f;

import dev.engine_room.flywheel.api.backend.RenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * 26.1 moved everything atmospheric out of {@code ClientLevel} and into the environment attribute
 * system: the sky colour, sun angle and moon phase are now probed per-position and per-frame off
 * {@link net.minecraft.world.attribute.EnvironmentAttributeProbe}, which the camera carries. Day time
 * moved too -- there is no single counter any more, each dimension names a {@code WorldClock}.
 */
public final class LevelUniforms extends UniformWriter {
	private static final int SIZE = 16 * 4 + 4 * 12;
	static final UniformBuffer BUFFER = new UniformBuffer(Uniforms.LEVEL_INDEX, SIZE);

	// The two directional lights vanilla lights the level by, copied from Lighting where they are
	// private. Upstream caught them from GlStateManager#setupLevelDiffuseLighting, which 26.1 deleted
	// along with the imperative lighting path: a Lighting UBO is written once per dimension now, and
	// which pair goes into it is chosen by the dimension's CardinalLighting.Type. That type is public
	// and already read below, so the same choice is made here rather than hooked. Normalised at class
	// init exactly as vanilla does.
	private static final Vector3f LIGHT0_DIRECTION = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
	private static final Vector3f LIGHT1_DIRECTION = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
	private static final Vector3f NETHER_LIGHT0_DIRECTION = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
	private static final Vector3f NETHER_LIGHT1_DIRECTION = new Vector3f(-0.2f, -1.0f, 0.7f).normalize();

	private static final long TICKS_PER_DAY = 24000L;

	private LevelUniforms() {
	}

	public static void update(RenderContext context) {
		long ptr = BUFFER.ptr();

		ClientLevel level = context.level();
		float partialTick = context.partialTick();
		var environment = context.camera()
				.attributeProbe();

		int skyColor = environment.getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
		int cloudColor = environment.getValue(EnvironmentAttributes.CLOUD_COLOR, partialTick);
		ptr = writeColor(ptr, skyColor);
		ptr = writeColor(ptr, cloudColor);

		boolean nether = level.dimensionType()
				.cardinalLightType() == CardinalLighting.Type.NETHER;
		ptr = writeVec3(ptr, nether ? NETHER_LIGHT0_DIRECTION : LIGHT0_DIRECTION);
		ptr = writeVec3(ptr, nether ? NETHER_LIGHT1_DIRECTION : LIGHT1_DIRECTION);

		long dayTime = dayTime(level);
		long levelDay = dayTime / TICKS_PER_DAY;
		float timeOfDay = (float) (dayTime - levelDay * TICKS_PER_DAY) / TICKS_PER_DAY;
		ptr = writeInt(ptr, (int) (levelDay % 0x7FFFFFFFL));
		ptr = writeFloat(ptr, timeOfDay);

		ptr = writeInt(ptr, level.dimensionType()
				.hasSkyLight() ? 1 : 0);

		// The attribute is in degrees; ClientLevel#getSunAngle used to hand back radians.
		ptr = writeFloat(ptr, environment.getValue(EnvironmentAttributes.SUN_ANGLE, partialTick) * Mth.DEG_TO_RAD);

		MoonPhase moonPhase = environment.getValue(EnvironmentAttributes.MOON_PHASE, partialTick);
		ptr = writeFloat(ptr, DimensionType.MOON_BRIGHTNESS_PER_PHASE[moonPhase.index()]);
		ptr = writeInt(ptr, moonPhase.index());

		ptr = writeInt(ptr, level.isRaining() ? 1 : 0);
		ptr = writeFloat(ptr, level.getRainLevel(partialTick));
		ptr = writeInt(ptr, level.isThundering() ? 1 : 0);
		ptr = writeFloat(ptr, level.getThunderLevel(partialTick));

		// SKY_LIGHT_FACTOR is what ClientLevel#getSkyDarken(partialTick) computed: a unit float that
		// falls to 0.24 at night and is alpha-blended down again by rain and thunder.
		ptr = writeFloat(ptr, environment.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick));

		// DimensionSpecialEffects#constantAmbientLight became the dimension's cardinal lighting type;
		// NETHER is the flat one the old flag selected.
		ptr = writeInt(ptr, nether ? 1 : 0);

		// TODO: use defines for custom dimension ids
		int dimensionId;
		ResourceKey<Level> dimension = level.dimension();
		if (Level.OVERWORLD.equals(dimension)) {
			dimensionId = 0;
		} else if (Level.NETHER.equals(dimension)) {
			dimensionId = 1;
		} else if (Level.END.equals(dimension)) {
			dimensionId = 2;
		} else {
			dimensionId = -1;
		}
		ptr = writeInt(ptr, dimensionId);

		BUFFER.markDirty();
	}

	private static long writeColor(long ptr, int argb) {
		return writeVec4(ptr, ARGB.red(argb) / 255f, ARGB.green(argb) / 255f, ARGB.blue(argb) / 255f, 1f);
	}

	/**
	 * 26.1 replaced the level-wide day time with per-dimension clocks, so a dimension without one
	 * (a fixed-time dimension, for instance) genuinely has no day time.
	 */
	private static long dayTime(ClientLevel level) {
		return level.dimensionType()
				.defaultClock()
				.map(clock -> level.clockManager()
						.getTotalTicks(clock))
				.orElse(0L);
	}
}
