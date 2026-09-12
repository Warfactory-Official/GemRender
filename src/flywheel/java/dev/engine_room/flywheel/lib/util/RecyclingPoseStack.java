package dev.engine_room.flywheel.lib.util;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * A {@link PoseStack} that recycles {@link PoseStack.Pose} objects.
 *
 * <p>Nothing to add on 26.1: vanilla's own {@code PoseStack} does this now. It keeps a growing
 * {@code List<Pose>} and an index rather than a stack it pops from, so {@code pushPose} reuses the
 * {@code Pose} that is already there and {@code popPose} only moves the index. Upstream's version
 * reached into the deque behind {@code PoseStack} through an accessor to do the same thing by hand;
 * that field no longer exists and the accessor went with it.
 *
 * <p>The class stays because it is part of Flywheel's public lib surface and because the caveat it
 * documents still holds: you <em>CANNOT</em> safely store a Pose object outside the stack that
 * created it, on any version.
 */
public class RecyclingPoseStack extends PoseStack {
}
