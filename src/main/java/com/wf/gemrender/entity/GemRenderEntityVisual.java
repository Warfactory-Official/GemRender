package com.wf.gemrender.entity;

import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.render.*;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.ComponentEntityVisual;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A GemRender model on an entity: a mob, a vehicle, a projectile, anything that moves.
 *
 * <p>Everything the block-entity path does applies here -- one draw per model per batch, skinning in
 * the vertex shader, shared palettes between copies at the same instant, temporal LOD -- and the two
 * differences are what this class exists to handle.
 *
 * <p><b>An entity moves, so its transform is written every frame</b> rather than once in the
 * constructor. That is the cheap half: an instance write is a few dozen bytes and Flywheel uploads
 * only the instances that changed. The expensive half is the pose, and it is expensive exactly when
 * a crowd disagrees about the time -- three hundred mobs mid-stride at three hundred different points
 * in a walk cycle is three hundred palette evaluations, because that is genuinely three hundred
 * different poses. {@link PoseLod} is what makes that survivable at distance and it is on by default;
 * what is under a consumer's control is {@link #animate}, which decides how many distinct instants
 * the frame contains. See {@code docs/INTEGRATION.md} for the counting argument.
 *
 * <p><b>The model may not be loaded yet.</b> A handle imports on first use, on a loader thread, so
 * the first few frames of an entity that has just come into view can answer {@code null}. This class
 * therefore acquires its instancer in {@link #beginFrame} rather than in its constructor, and keeps
 * asking until the model arrives. A visual that resolved its model once, in its constructor, would
 * draw nothing for the entity's whole life if it happened to be built during the import.
 *
 * <p>Shadow, fire and hitbox are Flywheel's own components and are not added by default, because a
 * flying machine wants none of them. Add what the entity should have:
 *
 * <pre>{@code
 * addComponent(new ShadowComponent(visualizationContext, entity).radius(0.7f));
 * addComponent(new FireComponent(visualizationContext, entity));
 * }</pre>
 */
public abstract class GemRenderEntityVisual<T extends Entity> extends ComponentEntityVisual<T> {
    private final ModelCache.Handle<GemRenderGltfModel> handle;
    private final Matrix4f transform = new Matrix4f();
    @Nullable
    private GemRenderGltfModel gltf;
    @Nullable
    private GemRenderInstance instance;
    private GltfAnimation[] clips = new GltfAnimation[0];
    private float[] times = new float[0];
    @Nullable
    private PoseCache.Pose posed;

    protected GemRenderEntityVisual(VisualizationContext ctx, T entity, float partialTick,
                                    ModelCache.Handle<GemRenderGltfModel> model) {
        super(ctx, entity, partialTick);
        this.handle = model;
    }

    /**
     * This frame's animation: write a clip and an instant into each layer.
     *
     * <p>One layer is the common case and {@code clips[0]} is all there is to fill. A layer left null
     * sits out, so a clip that only sometimes applies costs nothing when it does not.
     *
     * <p>Both arrays are {@link #layers()} long, are reused between frames, and belong to this visual
     * -- do not keep them, and do not assume they are clear.
     *
     * <p>The instant decides the cost of the frame. A quantity integrated per entity -- elapsed time
     * in a state, an accumulating angle -- puts every copy in a bucket of its own; a quantity derived
     * from the world clock, or from a value drawn from a small fixed set, lets a crowd share.
     */
    protected abstract void animate(float partialTick, GltfAnimation[] clips, float[] times);

    /**
     * How many clips {@link #animate} is given. One unless the entity's parts answer to different things.
     */
    protected int layers() {
        return 1;
    }

    /**
     * Where the model goes, relative to the render origin. Position and facing by default.
     *
     * <p>Yaw comes from {@link Entity#getYRot()}, which is where the entity is looking. A mob whose
     * body and head turn separately usually wants {@code yBodyRot} here and a head bone driven from
     * the difference; a vehicle wants pitch and roll as well. Override and compose whatever the entity
     * actually has -- the matrix is applied after skinning, so it moves the posed model as a whole.
     */
    protected void transform(Matrix4f pose, float partialTick) {
        Vector3f at = getVisualPosition(partialTick);
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());

        pose.translation(at)
                .rotateY(-yaw * Mth.DEG_TO_RAD);
    }

    /**
     * The pose drawn this frame, or {@code null} before the model has loaded.
     *
     * <p>{@link PoseCache.Pose#boneMatrix} off this is how anything gets attached to a bone -- a
     * muzzle, a held item, a light, a particle emitter. Model space, so compose it with the same
     * transform the instance carries.
     */
    @Nullable
    public PoseCache.Pose pose() {
        return posed;
    }

    /**
     * The model, or {@code null} while it is still importing.
     */
    @Nullable
    public GemRenderGltfModel model() {
        return gltf;
    }

    /**
     * Whether this frame's update can be skipped because the entity is far away.
     *
     * <p>Off by default, and the default is the one to keep for anything a player watches move.
     * Flywheel's limiter is quantised in <em>ticks</em>, not frames -- past about 45 blocks it answers
     * true on one tick and false on the next -- so limiting the transform makes a moving entity visibly
     * stutter. What it is for is a crowd: three hundred mobs each cost a transform, an instance write
     * and an upload whether or not the eye can tell they moved, and halving that at distance is the
     * difference between a swarm that runs and one that does not.
     *
     * <p>The counterpart of {@code AbstractBlockEntityVisual#doDistanceLimitThisFrame}, with the
     * distance passed in because this class has already computed it.
     *
     * <pre>{@code
     * protected boolean doDistanceLimitThisFrame(DynamicVisual.Context ctx, double distanceSquared) {
     *     return !ctx.limiter().shouldUpdate(distanceSquared);
     * }
     * }</pre>
     */
    protected boolean doDistanceLimitThisFrame(DynamicVisual.Context ctx, double distanceSquared) {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Flywheel calls neither {@link #isVisible} nor {@link #doDistanceLimitThisFrame} itself</b>
     * -- they are helpers a visual is expected to apply to its own update, and a visual that only
     * overrides them has written dead code. This is where they are applied, so a subclass gets both by
     * existing.
     */
    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        float partialTick = ctx.partialTick();

        // 26.1 renamed Camera#getPosition to position(), matching Entity. Same Vec3, same frame.
        //? if >=26.1 {
		/*var camera = ctx.camera()
				.position();
		*///?} else {
        var camera = ctx.camera()
                .getPosition();
        //?}
        double distanceSquared = distanceSquared(camera.x, camera.y, camera.z);

        // Before acquiring anything: an entity that has never been on screen has nothing to draw, and
        // the visibility test needs neither the model nor the instance to answer.
        if (!isVisible(ctx.frustum()) || doDistanceLimitThisFrame(ctx, distanceSquared)) {
            return;
        }

        super.beginFrame(ctx);

        if (instance == null && !acquire()) {
            return;
        }

        GemRenderGltfModel model = gltf;
        GemRenderInstance target = instance;
        if (model == null || target == null) {
            return;
        }

        transform(transform, partialTick);
        target.pose.set(transform);

        for (int layer = 0; layer < clips.length; layer++) {
            clips[layer] = null;
            times[layer] = 0.0f;
        }
        animate(partialTick, clips, times);

        PoseCache.Pose pose = PoseCache.getInstance()
                .pose(model.layout(), model.bounds(), model.morphs(), clips, times,
                        PoseLod.getInstance()
                                .levelAt(distanceSquared));
        posed = pose;

        target.boneBase = pose.boneBase();
        target.morphBase = pose.morphBase();
        target.boneSphere.set(pose.sphere());

        // Lighting is the one thing that need not be exact every frame: a mob crossing a light
        // boundary is a step change either way, and Flywheel's limiter is what stops a crowd
        // re-sampling the lightmap for all of them at once.
        if (ctx.limiter()
                .shouldUpdate(distanceSquared)) {
            relight(partialTick, target);
        }

        // Unconditional, unlike a machine's: the transform has already changed by the time we are
        // here, so there is nothing to compare against that would ever say otherwise.
        target.setChanged();
    }

    /**
     * Takes an instance once the model has imported. False while it has not.
     *
     * <p>Asking the handle every frame is what makes a late import recover. It is a field read and a
     * generation compare once the model is in, which is why this is not gated behind a retry counter.
     */
    private boolean acquire() {
        GemRenderGltfModel model = handle.get();
        if (model == null) {
            return false;
        }

        this.gltf = model;
        this.clips = new GltfAnimation[Math.max(1, layers())];
        this.times = new float[clips.length];

        GemRenderInstance created = instancerProvider()
                .instancer(GemRenderInstanceTypes.SKINNED, model.model())
                .createInstance();
        created.colorArgb(0xFFFFFFFF);

        this.instance = created;
        return true;
    }

    /**
     * Whether to run this frame, tested against the model's bound as well as the entity's box.
     *
     * <p>Flywheel asks {@link dev.engine_room.flywheel.lib.visual.EntityVisibilityTester}, which
     * inflates the entity's own bounding box a little. That is right for a model drawn at the size of
     * the thing carrying it and wrong in general: a GemRender model is under no obligation to be
     * entity-sized, and a two-block entity wearing a twenty-block machine would stop being updated the
     * moment the entity's box left the frustum. What that looks like is a model frozen mid-animation
     * with most of it still on screen -- the same mistake as leaving {@code boneSphere} at its default,
     * one step earlier in the pipeline and without the GPU culler to catch it.
     *
     * <p>The union of the two, not a replacement: the sphere is last frame's, so for something moving
     * quickly the entity's own box is the half that is up to date.
     */
    @Override
    public boolean isVisible(FrustumIntersection frustum) {
        if (super.isVisible(frustum)) {
            return true;
        }

        PoseCache.Pose last = posed;
        return last != null && PosedBound.test(frustum, transform, last.sphere());
    }

    @Override
    protected void _delete() {
        super._delete();
        if (instance != null) {
            instance.delete();
            instance = null;
        }
    }
}
