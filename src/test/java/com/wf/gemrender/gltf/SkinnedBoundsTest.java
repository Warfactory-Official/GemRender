package com.wf.gemrender.gltf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.wf.gemrender.gltf.skin.SkinnedBounds;
import com.wf.gemrender.gltf.skin.VertexSkinning;

class SkinnedBoundsTest {
	private static final float FLYWHEEL_EPSILON = 1e-4f;

	private static SkinnedBounds bounds() {
		return RigFixture.bounds();
	}

	@Test
	@DisplayName("Flywheel's own sphere would cull the rig while a third of it is on screen")
	void theModelSphereDoesNotContainThePosedModel() {
		Vector4f flywheel = flywheelMeshSpaceSphere();
		Vector3f centre = new Vector3f(flywheel.x, flywheel.y, flywheel.z);

		float[] posed = allPosedVertices(RigFixture.pose(RigFixture.animation(), RigFixture.MID_CLIP));

		int outside = 0;
		float worst = 0.0f;
		for (int v = 0; v < posed.length / 3; v++) {
			float distance = centre.distance(posed[v * 3], posed[v * 3 + 1], posed[v * 3 + 2]);
			worst = Math.max(worst, distance);
			if (distance > flywheel.w) {
				outside++;
			}
		}

		assertThat(outside)
				.as("vertices outside Flywheel's mesh-space sphere at the curled pose, of %d",
						posed.length / 3)
				.isGreaterThan(posed.length / 3 / 4);
		assertThat(worst / flywheel.w)
				.as("how far past Flywheel's radius the curled rig reaches")
				.isGreaterThan(1.25f);
	}

	@Test
	@DisplayName("every posed vertex stays inside the sphere, throughout the clip")
	void everyVertexIsContainedAtEveryPointInTheClip() {
		SkinnedBounds bounds = bounds();
		GltfAnimation animation = RigFixture.animation();
		Vector4f sphere = new Vector4f();

		int steps = 41;
		for (int step = 0; step < steps; step++) {
			float time = RigFixture.CLIP_SECONDS * step / (steps - 1);
			Matrix4f[] palette = RigFixture.pose(animation, time);
			bounds.evaluate(palette, sphere);

			Vector3f centre = new Vector3f(sphere.x, sphere.y, sphere.z);
			float[] posed = allPosedVertices(palette);
			for (int v = 0; v < posed.length / 3; v++) {
				assertThat(centre.distance(posed[v * 3], posed[v * 3 + 1], posed[v * 3 + 2]))
						.as("t=%.2f, vertex %d outside the cull sphere %s", time, v, sphere)
						.isLessThanOrEqualTo(sphere.w + 1e-4f);
			}
		}
	}

	@Test
	@DisplayName("the sphere is barely larger than the geometry it has to contain")
	void theSphereIsNotWastefullyLarge() {
		SkinnedBounds bounds = bounds();
		GltfAnimation animation = RigFixture.animation();
		Vector4f sphere = new Vector4f();

		for (int step = 0; step <= 8; step++) {
			float time = RigFixture.CLIP_SECONDS * step / 8.0f;
			Matrix4f[] palette = RigFixture.pose(animation, time);
			bounds.evaluate(palette, sphere);

			Vector3f centre = new Vector3f(sphere.x, sphere.y, sphere.z);
			float[] posed = allPosedVertices(palette);

			float needed = 0.0f;
			for (int v = 0; v < posed.length / 3; v++) {
				needed = Math.max(needed, centre.distance(posed[v * 3], posed[v * 3 + 1], posed[v * 3 + 2]));
			}

			assertThat(sphere.w / needed)
					.as("t=%.2f: radius %.4f against the %.4f actually needed", time, sphere.w, needed)
					.isBetween(1.0f, 1.2f);
		}
	}

	@Test
	@DisplayName("a bone gets a box around the vertices it moves, not around the whole model")
	void boxesAreRestrictedToTheVerticesABoneInfluences() {
		SkinnedBounds bounds = bounds();

		assertThat(bounds.size()).isEqualTo(5);

		float[] box = new float[6];
		int rootBox = indexOfSlot(bounds, RigFixture.SKIN_BASE);
		bounds.box(rootBox, box);

		assertThat(box[4])
				.as("root bone's box reaches up to y=%.2f of a four-metre column", box[4])
				.isLessThan(2.0f)
				.isGreaterThan(1.0f);
		assertThat(box[1])
				.as("root bone's box starts at the foot of the column")
				.isEqualTo(0.0f, within(1e-6f));
	}

	@Test
	@DisplayName("bending about the model's own centre never inflates the sphere much")
	void articulationThatMovesNoGeometryOutwardCostsLittle() {
		SkinnedBounds cube = new SkinnedBounds.Builder()
				.addBox(0, -0.5f, 0.0f, -0.5f, 0.5f, 0.0f, 0.5f)
				.addBox(1, -0.5f, 1.0f, -0.5f, 0.5f, 1.0f, 0.5f)
				.build();

		float circumradius = (float) (Math.sqrt(3) * 0.5);
		Matrix4f[] palette = { new Matrix4f(), new Matrix4f() };
		Vector4f sphere = new Vector4f();

		for (float angle : new float[] { 0.0f, 0.3f, 0.6f, -0.6f, 1.5f, 3.1f }) {
			palette[0].identity();
			palette[1].translation(0.0f, 0.5f, 0.0f)
					.rotateX(angle)
					.translate(0.0f, -0.5f, 0.0f);
			cube.evaluate(palette, sphere);

			Vector3f centre = new Vector3f(sphere.x, sphere.y, sphere.z);
			for (int corner = 0; corner < 8; corner++) {
				Vector3f v = new Vector3f((corner & 1) != 0 ? 0.5f : -0.5f, (corner & 2) != 0 ? 1.0f : 0.0f,
						(corner & 4) != 0 ? 0.5f : -0.5f);
				palette[(corner & 2) != 0 ? 1 : 0].transformPosition(v);
				assertThat(centre.distance(v))
						.as("corner %d at %.2f rad", corner, angle)
						.isLessThanOrEqualTo(sphere.w + 1e-5f);
			}

			assertThat(sphere.w)
					.as("radius at %.2f rad against the cube's circumradius", angle)
					.isLessThanOrEqualTo(circumradius * 1.2f);
		}

		palette[1].identity();
		cube.evaluate(palette, sphere);
		assertThat(sphere.w)
				.as("unbent, the sphere is exactly the cube's circumradius")
				.isEqualTo(circumradius, within(1e-5f));
		assertThat(new Vector3f(sphere.x, sphere.y, sphere.z).distance(0.0f, 0.5f, 0.0f))
				.as("and centred on the cube")
				.isLessThan(1e-5f);
	}

	@Test
	@DisplayName("the sphere follows the pose rather than sitting where the model started")
	void theSphereMovesWithTheAnimation() {
		SkinnedBounds bounds = bounds();
		Vector4f rest = new Vector4f();
		Vector4f curled = new Vector4f();

		bounds.evaluate(RigFixture.pose(null, 0.0f), rest);
		bounds.evaluate(RigFixture.pose(RigFixture.animation(), RigFixture.MID_CLIP), curled);

		assertThat(new Vector3f(rest.x, rest.y, rest.z).distance(curled.x, curled.y, curled.z))
				.as("centre moved between the rest pose and the curled one")
				.isGreaterThan(0.5f);
	}

	private static Vector4f flywheelMeshSpaceSphere() {
		float[] column = RigFixture.positions(RigFixture.NODE_ARMATURE);
		float[] flag = RigFixture.positions(RigFixture.NODE_FLAG);

		Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
		Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
		for (float[] set : new float[][] { column, flag }) {
			for (int v = 0; v < set.length / 3; v++) {
				min.min(new Vector3f(set[v * 3], set[v * 3 + 1], set[v * 3 + 2]));
				max.max(new Vector3f(set[v * 3], set[v * 3 + 1], set[v * 3 + 2]));
			}
		}

		Vector3f centre = new Vector3f(min).add(max)
				.mul(0.5f);
		float radius = 0.0f;
		for (float[] set : new float[][] { column, flag }) {
			for (int v = 0; v < set.length / 3; v++) {
				radius = Math.max(radius, centre.distance(set[v * 3], set[v * 3 + 1], set[v * 3 + 2]));
			}
		}
		return new Vector4f(centre, radius + FLYWHEEL_EPSILON);
	}

	private static float[] allPosedVertices(Matrix4f[] palette) {
		VertexSkinning column = RigFixture.columnSkinning();
		VertexSkinning flag = RigFixture.flagSkinning();

		float[] posedColumn = RigFixture.posedPositions(RigFixture.NODE_ARMATURE, column, palette);
		float[] posedFlag = RigFixture.posedPositions(RigFixture.NODE_FLAG, flag, palette);

		float[] all = new float[posedColumn.length + posedFlag.length];
		System.arraycopy(posedColumn, 0, all, 0, posedColumn.length);
		System.arraycopy(posedFlag, 0, all, posedColumn.length, posedFlag.length);
		return all;
	}

	private static int indexOfSlot(SkinnedBounds bounds, int slot) {
		for (int i = 0; i < bounds.size(); i++) {
			if (bounds.slot(i) == slot) {
				return i;
			}
		}
		throw new AssertionError("no box for palette slot " + slot);
	}
}
