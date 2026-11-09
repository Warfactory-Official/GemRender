package com.wf.gemrender.effect;

import com.wf.gemrender.volume.*;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** Client/render-thread entry points. Tracked submissions expire after two missing frames. */
public final class Effects {
    /** Variant flag for callers that disable flashes or already supply their own HUD flash. */
    public static final int NO_FLASH = 256;
    private static final Map<Object, Entry> LIVE = new IdentityHashMap<>();
    private static Level level;
    private static long frame;
    private static boolean installed;

    private Effects() {}

    public static boolean available(Level world) {
        return installed && world != null && world == level && world == Minecraft.getInstance().level
                && VisualizationManager.supportsVisualization(world) && Volumetrics.getInstance().available();
    }

    /** Stable key (usually an entity); age/lifetime are ticks, scale is the family-specific block multiplier. */
    public static boolean submit(Object key, Level world, String family, double x, double y, double z,
                                 float age, float scale, float lifetime, int variant) {
        if (!available(world)) return false;
        if (key == null) throw new IllegalArgumentException("Tracked effect needs a key");
        checkPosition(x, y, z);
        EffectFamily kind = EffectFamily.valueOf(family);
        EffectFamily.Frame next = kind.frame(age, scale, lifetime, variant);
        Entry entry = LIVE.get(key);
        if (entry == null || entry.family != kind) {
            if (entry != null) remove(entry);
            entry = new Entry(world, kind, false, scale, lifetime, variant);
            LIVE.put(key, entry);
            entry.state = new State(x, y, z, age, next);
            entry.manager.effects().queueAdd(entry);
        }
        entry.lastFrame = frame;
        entry.update(x, y, z, age, next);
        return true;
    }

    /** Starts an autonomous visual. Its clock pauses with the world and is retained across visual rebuilds. */
    public static boolean spawn(Level world, String family, double x, double y, double z,
                                float scale, float lifetime, int variant) {
        if (!available(world)) return false;
        checkPosition(x, y, z);
        EffectFamily kind = EffectFamily.valueOf(family);
        EffectFamily.Frame initial = kind.frame(0, scale, lifetime, variant);
        Entry entry = new Entry(world, kind, true, scale, lifetime, variant);
        entry.state = new State(x, y, z, 0, initial);
        LIVE.put(entry, entry);
        entry.manager.effects().queueAdd(entry);
        return true;
    }

    public static void beginFrame(float partialTick) {
        installed = true;
        frame++;
        Level current = Minecraft.getInstance().level;
        if (current != level) {
            for (Entry entry : LIVE.values()) remove(entry);
            LIVE.clear();
            SceneEffects.clear();
            level = current;
        }
        VisualizationManager manager = available(current) ? VisualizationManager.get(current) : null;
        Iterator<Entry> entries = LIVE.values().iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            // Backend/resource resets replace the manager and drop its non-entity effects.
            if (manager != null && entry.manager != manager) {
                entry.manager = manager;
                manager.effects().queueAdd(entry);
            }
            float age = entry.world.getGameTime() - entry.born + partialTick;
            if ((entry.autonomous && age >= entry.lifetime) || (!entry.autonomous && frame - entry.lastFrame > 2)) {
                remove(entry);
                entries.remove();
            } else if (entry.autonomous) {
                State state = entry.state;
                entry.update(state.x, state.y, state.z, age, entry.family.frame(age, entry.scale, entry.lifetime, entry.variant));
            }
        }
    }

    public static int activeCount() { return LIVE.size(); }

    private static void checkPosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Effect position must be finite");
        }
    }

    private static void remove(Entry entry) {
        entry.manager.effects().queueRemove(entry);
    }

    private record State(double x, double y, double z, float age, EffectFamily.Frame frame) {}

    private static final class Entry implements Effect {
        final Level world;
        final EffectFamily family;
        final boolean autonomous;
        final float scale, lifetime;
        final int variant;
        final long born;
        long lastFrame;
        double trailTop = Double.NEGATIVE_INFINITY;
        volatile State state;
        VisualizationManager manager;

        Entry(Level world, EffectFamily family, boolean autonomous, float scale, float lifetime, int variant) {
            this.world = world;
            this.manager = VisualizationManager.getOrThrow(world);
            this.family = family;
            this.autonomous = autonomous;
            this.scale = scale;
            this.lifetime = lifetime;
            this.variant = variant;
            born = world.getGameTime();
            lastFrame = frame;
        }

        void update(double x, double y, double z, float age, EffectFamily.Frame next) {
            if (family == EffectFamily.TRAIL) {
                trailTop = Math.max(trailTop, y + 64 * scale);
                EffectFamily.Part p = next.parts()[0];
                float height = (float) (trailTop - y) / 1.6f;
                next = new EffectFamily.Frame(new EffectFamily.Part[]{new EffectFamily.Part(p.shape(), p.style(),
                        p.x(), height, p.z(), height * 0.8f, p.fade(), p.heat(), p.emission(), p.tint(), p.billow())},
                        next.grade(), next.flash(), next.tint());
            }
            state = new State(x, y, z, age, next);
            if (available(world)) {
                EffectFamily.Part glow = next.parts()[0];
                if ((family == EffectFamily.NUCLEAR || family == EffectFamily.STEAM)
                        && next.parts()[1].fade() > glow.fade()) glow = next.parts()[1];
                float radius = Math.max(glow.x(), glow.z()) * (glow.shape() == VolumeShape.MUSHROOM ? 0.65f : 0.77f);
                float height = glow.offsetY() + (glow.shape() == VolumeShape.MUSHROOM ? glow.y() * 0.35f : 0);
                float halo = glow.fade() * glow.heat() * glow.emission() * 0.6f;
                SceneEffects.submit(x, y + height, z, next.tint(), next.grade(), next.flash(), radius, halo);
            }
        }

        @Override public LevelAccessor level() { return world; }
        @Override public EffectVisual<?> visualize(VisualizationContext context, float partialTick) {
            return new Visual(context, this, partialTick);
        }
    }

    private static final class Visual extends AbstractVisual implements EffectVisual<Entry>, SimpleDynamicVisual {
        private final Entry entry;
        private final Volume[] volumes;
        private final VolumeInstance[] instances;

        Visual(VisualizationContext context, Entry entry, float partialTick) {
            super(context, entry.world, partialTick);
            this.entry = entry;
            int count = entry.state.frame.parts().length;
            volumes = new Volume[count];
            instances = new VolumeInstance[count];
            var instancer = context.instancerProvider().instancer(GemRenderVolumeTypes.VOLUME, VolumeModels.effect());
            for (int i = 0; i < count; i++) {
                volumes[i] = Volume.create(entry.state.frame.parts()[i].style())
                        .seed((System.identityHashCode(entry) & 0xFFFF) * 0.0001f);
                instances[i] = instancer.createInstance().volume(volumes[i].slot());
            }
            push();
        }

        @Override public void beginFrame(Context context) { push(); }

        private void push() {
            State state = entry.state;
            var origin = renderOrigin();
            for (int i = 0; i < volumes.length; i++) {
                EffectFamily.Part p = state.frame.parts()[i];
                Volume volume = volumes[i];
                if (volume.style() != p.style()) volume.style(p.style());
                volume.shape(p.shape()).extent(p.x(), p.y(), p.z()).fade(p.fade()).billow(p.billow())
                        .heat(p.heat(), p.emission()).emissionTint(p.tint())
                        .animate(state.age * 0.05f, 0.4f);
                instances[i].center((float) (state.x - origin.getX()),
                        (float) (state.y + p.offsetY() - origin.getY()), (float) (state.z - origin.getZ())).setChanged();
            }
        }

        @Override protected void _delete() {
            for (VolumeInstance instance : instances) instance.delete();
            for (Volume volume : volumes) volume.close();
        }
    }
}
