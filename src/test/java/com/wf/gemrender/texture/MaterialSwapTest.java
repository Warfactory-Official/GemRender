package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class MaterialSwapTest {
	private static ResourceLocation at(String path) {
		return ResourceLocation.fromNamespaceAndPath("gemrender", path);
	}

	private static final ResourceLocation BASE = at("textures/base.png");
	private static final ResourceLocation NORMAL = at("textures/normal.png");
	private static final ResourceLocation RED = at("textures/base_red.png");

	private static MaterialMaps pbr() {
		return new MaterialMaps(BASE, NORMAL, null, null, null, 0.5f, 0.5f, 0.5f, 1.0f, 0.25f, 0.75f,
				2.0f, 1.0f, 0.0f, 0.0f, 0.0f);
	}

	@Test
	@DisplayName("a swap replaces every map that names the substituted texture")
	void swapsByLocation() {
		MaterialMaps both = new MaterialMaps(BASE, BASE, null, null, null, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
				1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f);

		MaterialMaps swapped = both.swapped(Map.of(BASE, RED));

		assertThat(swapped.baseColor()).isEqualTo(RED);
		assertThat(swapped.normal())
				.as("keyed on the texture, not on the slot, so one entry reskins everything sharing it")
				.isEqualTo(RED);
	}

	@Test
	@DisplayName("a map the variant does not mention keeps the texture it had")
	void unmentionedMapsSurvive() {
		MaterialMaps swapped = pbr().swapped(Map.of(BASE, RED));

		assertThat(swapped.baseColor()).isEqualTo(RED);
		assertThat(swapped.normal())
				.as("a variant that repaints the colour should not lose the model's normal map")
				.isEqualTo(NORMAL);
	}

	@Test
	@DisplayName("an empty swap is the material itself")
	void emptySwapIsIdentity() {
		MaterialMaps material = pbr();
		assertThat(material.swapped(Map.of())).isSameAs(material);
		assertThat(material.swapped(Map.of(at("textures/absent.png"), RED))).isEqualTo(material);
	}

	@Test
	@DisplayName("the baked factors are not a variant's to change")
	void factorsAreUntouched() {
		MaterialMaps swapped = pbr().swapped(Map.of(BASE, RED));

		assertThat(swapped.metallicFactor()).isEqualTo(0.25f);
		assertThat(swapped.roughnessFactor()).isEqualTo(0.75f);
		assertThat(swapped.normalScale()).isEqualTo(2.0f);
		assertThat(swapped.baseColorR()).isEqualTo(0.5f);
		assertThat(swapped.pbr())
				.as("and a swapped material is still the same kind of material, so the band count holds")
				.isEqualTo(pbr().pbr());
	}

	@Test
	@DisplayName("a null map stays null rather than picking up a substitution")
	void nullMapsStayNull() {
		MaterialMaps plain = MaterialMaps.plain(BASE);

		MaterialMaps swapped = plain.swapped(Map.of(BASE, RED));

		assertThat(swapped.baseColor()).isEqualTo(RED);
		assertThat(swapped.normal()).isNull();
		assertThat(swapped.emissive()).isNull();
	}
}
