package com.wf.gemrender.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParseException;

import com.wf.gemrender.bedrock.BedrockGeometry.Bone;
import com.wf.gemrender.bedrock.BedrockGeometry.Cube;
import com.wf.gemrender.bedrock.BedrockGeometry.Face;

class BedrockGeometryTest {

	@Test
	@DisplayName("the current form is read out of minecraft:geometry")
	void currentForm() {
		BedrockGeometry geometry = BedrockFixture.geometry(BedrockFixture.CHAIN);

		assertThat(geometry.identifier()).isEqualTo("geometry.chain");
		assertThat(geometry.textureWidth()).isEqualTo(64);
		assertThat(geometry.textureHeight()).isEqualTo(32);
		assertThat(geometry.bones()).hasSize(3);

		Bone arm = geometry.bones()
				.get(1);
		assertThat(arm.name()).isEqualTo("arm");
		assertThat(arm.parent()).isEqualTo("root");
		assertThat(arm.pivot()).containsExactly(7.0f, 11.0f, 13.0f);
		assertThat(arm.rotation()).containsExactly(-5.0f, 0.0f, 45.0f);
		assertThat(arm.cubes()
				.get(0)
				.inflate()).isEqualTo(0.5f);
	}

	@Test
	@DisplayName("the pre-1.12 form is read whatever its geometry is called")
	void legacyForm() {
		BedrockGeometry geometry = BedrockFixture.geometry(BedrockFixture.LEGACY);

		assertThat(geometry.textureWidth()).as("the legacy spelling is 'texturewidth'")
				.isEqualTo(64);
		assertThat(geometry.textureHeight()).isEqualTo(32);
		assertThat(geometry.bones()).hasSize(1);
		assertThat(geometry.bones()
				.get(0)
				.mirror()).isTrue();
	}

	@Test
	@DisplayName("a cube's uv is either a net corner or a set of faces, and both are read")
	void uvIsAUnion() {
		String json = """
				{"format_version": "1.12.0", "minecraft:geometry": [{
				  "description": {"texture_width": 16, "texture_height": 16},
				  "bones": [{"name": "b", "pivot": [0,0,0], "cubes": [
				    {"origin": [0,0,0], "size": [1,1,1], "uv": [3, 4]},
				    {"origin": [0,0,0], "size": [1,1,1], "uv": {
				       "north": {"uv": [1,2], "uv_size": [3,4], "uv_rotation": 90},
				       "up": {"uv": [5,6], "uv_size": [7,8]}}},
				    {"origin": [0,0,0], "size": [1,1,1]}
				  ]}]}]}
				""";

		BedrockGeometry geometry = BedrockGeometry.parse(BedrockFixture.json(json), "test");
		var cubes = geometry.bones()
				.get(0)
				.cubes();

		assertThat(cubes.get(0)
				.boxUv()).containsExactly(3.0f, 4.0f);
		assertThat(cubes.get(0)
				.faceUv()).isNull();

		Cube perFace = cubes.get(1);
		assertThat(perFace.boxUv()).isNull();
		assertThat(perFace.faceUv()).containsOnlyKeys(Face.NORTH, Face.UP);
		assertThat(perFace.faceUv()
				.get(Face.NORTH)
				.uvSize()).containsExactly(3.0f, 4.0f);
		assertThat(perFace.faceUv()
				.get(Face.NORTH)
				.rotation()).isEqualTo(90);

		assertThat(cubes.get(2)
				.boxUv()).containsExactly(0.0f, 0.0f);
	}

	@Test
	@DisplayName("a per-face entry missing a rectangle takes the sheet's corner, and does not throw")
	void perFaceEntriesTolerateMissingRectangles() {
		String json = """
				{"format_version": "1.12.0", "minecraft:geometry": [{
				  "description": {"texture_width": 16, "texture_height": 16},
				  "bones": [{"name": "b", "pivot": [0,0,0], "cubes": [
				    {"origin": [0,0,0], "size": [1,1,1], "uv": {
				       "north": {"uv_size": [3,4]},
				       "south": {"uv": [1,2]},
				       "up": {}}}]}]}]}
				""";

		BedrockGeometry geometry = BedrockGeometry.parse(BedrockFixture.json(json), "test");
		Cube cube = geometry.bones()
				.get(0)
				.cubes()
				.get(0);

		assertThat(cube.faceUv()).containsOnlyKeys(Face.NORTH, Face.SOUTH, Face.UP);
		assertThat(cube.faceUv()
				.get(Face.NORTH)
				.uv()).containsExactly(0.0f, 0.0f);
		assertThat(cube.faceUv()
				.get(Face.SOUTH)
				.uvSize()).containsExactly(0.0f, 0.0f);
		assertThat(cube.faceUv()
				.get(Face.UP)
				.uv()).containsExactly(0.0f, 0.0f);
	}

	@Test
	@DisplayName("mirror is three-valued, because a cube inherits its bone's only when it is silent")
	void mirrorDistinguishesAbsentFromFalse() {
		String json = """
				{"format_version": "1.12.0", "minecraft:geometry": [{
				  "description": {"texture_width": 16, "texture_height": 16},
				  "bones": [{"name": "b", "pivot": [0,0,0], "mirror": true, "cubes": [
				    {"origin": [0,0,0], "size": [1,1,1], "uv": [0,0]},
				    {"origin": [0,0,0], "size": [1,1,1], "uv": [0,0], "mirror": false}
				  ]}]}]}
				""";

		BedrockGeometry geometry = BedrockGeometry.parse(BedrockFixture.json(json), "test");
		var cubes = geometry.bones()
				.get(0)
				.cubes();

		assertThat(cubes.get(0)
				.mirror()).as("silent, so the bone's true applies")
				.isNull();
		assertThat(cubes.get(1)
				.mirror()).as("an explicit false must survive the bone's true")
				.isFalse();

		BedrockSkeleton skeleton = BedrockSkeleton.of(geometry);
		assertThat(skeleton.parts()
				.get(0)
				.mirror()).isTrue();
		assertThat(skeleton.parts()
				.get(1)
				.mirror()).isFalse();
	}

	@Test
	@DisplayName("a bone's inflate is the default for cubes that do not state one")
	void boneInflateIsInherited() {
		String json = """
				{"format_version": "1.12.0", "minecraft:geometry": [{
				  "description": {"texture_width": 16, "texture_height": 16},
				  "bones": [{"name": "b", "pivot": [0,0,0], "inflate": 0.25, "cubes": [
				    {"origin": [0,0,0], "size": [1,1,1], "uv": [0,0]},
				    {"origin": [0,0,0], "size": [1,1,1], "uv": [0,0], "inflate": 2}
				  ]}]}]}
				""";

		BedrockSkeleton skeleton = BedrockSkeleton.of(BedrockGeometry.parse(BedrockFixture.json(json),
				"test"));

		assertThat(skeleton.parts()
				.get(0)
				.inflate()).isEqualTo(0.25f);
		assertThat(skeleton.parts()
				.get(1)
				.inflate()).isEqualTo(2.0f);
	}

	@Test
	@DisplayName("GemRender's own description keys carry what Bedrock geometry cannot")
	void gemRenderKeys() {
		String json = """
				{"format_version": "1.21.0", "minecraft:geometry": [{
				  "description": {"texture_width": 16, "texture_height": 16,
				    "gemrender:texture": "example:textures/thing.png",
				    "gemrender:alpha": "BLEND", "gemrender:cull": true},
				  "bones": [{"name": "b", "pivot": [0,0,0]}]}]}
				""";

		BedrockGeometry geometry = BedrockGeometry.parse(BedrockFixture.json(json), "test");
		assertThat(geometry.texture()).isEqualTo("example:textures/thing.png");
		assertThat(geometry.alpha()).isEqualTo("blend");
		assertThat(geometry.cull()).isTrue();

		BedrockGeometry plain = BedrockFixture.geometry(BedrockFixture.UNIT);
		assertThat(plain.texture()).isNull();
		assertThat(plain.alpha()).isEqualTo("cutout");
		assertThat(plain.cull()).isFalse();
	}

	@Test
	@DisplayName("a missing texture size falls back rather than dividing by zero")
	void missingTextureSizeIsSurvivable() {
		String json = """
				{"format_version": "1.12.0", "minecraft:geometry": [{
				  "description": {},
				  "bones": [{"name": "b", "pivot": [0,0,0]}]}]}
				""";

		BedrockGeometry geometry = BedrockGeometry.parse(BedrockFixture.json(json), "test");
		assertThat(geometry.textureWidth()).isEqualTo(16);
		assertThat(geometry.textureHeight()).isEqualTo(16);
	}

	@Test
	@DisplayName("a file that is neither shape is refused with a message that says why")
	void unrecognisedFileIsRefused() {
		assertThatThrownBy(() -> BedrockGeometry.parse(
				BedrockFixture.json("{\"format_version\": \"1.12.0\", \"animations\": {}}"), "clip.json"))
				.isInstanceOf(JsonParseException.class)
				.hasMessageContaining("minecraft:geometry")
				.hasMessageContaining("clip.json");
	}
}
