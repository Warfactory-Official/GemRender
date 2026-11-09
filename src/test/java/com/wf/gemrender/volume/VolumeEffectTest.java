package com.wf.gemrender.volume;

import com.wf.gemrender.Ids;
import com.wf.gemrender.gl.ShaderSources;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolumeEffectTest {
    @Test
    void noiseSlicesHaveWrappedFilteringBorders() throws Exception {
        try (var in = getClass().getResourceAsStream("/assets/gemrender/textures/fx/volume_noise.png")) {
            var image = ImageIO.read(in);
            assertThat(image.getWidth()).isEqualTo(528);
            assertThat(image.getHeight()).isEqualTo(528);
            for (int slice = 0; slice < 64; slice++) {
                int x = slice % 8 * 66;
                int y = slice / 8 * 66;
                for (int p = 0; p < 66; p++) {
                    assertThat(image.getRGB(x, y + p)).isEqualTo(image.getRGB(x + 64, y + p));
                    assertThat(image.getRGB(x + 65, y + p)).isEqualTo(image.getRGB(x + 1, y + p));
                    assertThat(image.getRGB(x + p, y)).isEqualTo(image.getRGB(x + p, y + 64));
                    assertThat(image.getRGB(x + p, y + 65)).isEqualTo(image.getRGB(x + p, y + 1));
                }
            }
        }
    }

    @Test
    void rejectsNonfiniteAnimationAndOutOfRangeHeatOrDisplacement() {
        Volume volume = Volume.create(VolumeStyle.builder().build());
        try {
            assertThatThrownBy(() -> volume.animate(Float.NaN, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> volume.animate(1, Float.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> volume.heat(-0.1f, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> volume.heat(1.1f, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> volume.heat(1, -1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> volume.heat(Float.NaN, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> volume.billow(0.2f)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> volume.billow(Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        } finally {
            volume.close();
        }
    }

    @Test
    void flywheelDepthHookMatchesTheInstalledShaderAndRejectsUnknownLayouts() {
        var common = Ids.of("flywheel", "internal/common.frag");
        String source = ShaderSources.read("assets/flywheel/flywheel/internal/common.frag");
        assertThat(VolumeShaders.patch(common, source)).contains("float linearDepth = gemrender_effectViewDepth;");
        assertThat(VolumeShaders.patch(Ids.of("gemrender", "unrelated.frag"), source)).isEqualTo(source);
        assertThatThrownBy(() -> VolumeShaders.patch(common, "unsupported shader"))
                .isInstanceOf(IllegalStateException.class);
    }
}
