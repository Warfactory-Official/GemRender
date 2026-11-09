package com.wf.gemrender.effect;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EffectFamilyTest {
    @Test
    void everyTimelineKeepsStablePartsAndFiniteBoundsThroughItsLifetime() {
        for (EffectFamily family : EffectFamily.values()) {
            for (float scale : new float[]{0.01f, 1, 500}) {
                int parts = family.frame(0, scale, 900, 0).parts().length;
                for (float age : new float[]{0, 0.5f, 1, 70, 720, 899, 900, 901}) {
                    var frame = family.frame(age, scale, 900, 0);
                    assertThat(frame.parts()).hasSize(parts);
                    assertThat(Float.isFinite(frame.grade())).isTrue();
                    assertThat(Float.isFinite(frame.flash())).isTrue();
                    for (var part : frame.parts()) {
                        assertThat(part.x()).isPositive().isFinite();
                        assertThat(part.y()).isPositive().isFinite();
                        assertThat(part.z()).isPositive().isFinite();
                        assertThat(part.offsetY()).isFinite();
                        assertThat(part.fade()).isBetween(0f, 1f);
                        assertThat(part.heat()).isBetween(0f, 1f);
                        assertThat(part.emission()).isGreaterThanOrEqualTo(0f).isFinite();
                        if (age >= 900) assertThat(part.fade()).isZero();
                    }
                }
            }
        }
    }

    @Test
    void flashOptOutPreservesColorsAndSteamAndFalloutStayCold() {
        for (EffectFamily family : EffectFamily.values()) {
            var base = family.frame(1, 1, 900, 1);
            var quiet = family.frame(1, 1, 900, 1 | Effects.NO_FLASH);
            assertThat(quiet.parts()).containsExactly(base.parts());
            assertThat(quiet.flash()).isZero();
            assertThat(quiet.tint()).isEqualTo(base.tint());
        }
        for (EffectFamily family : new EffectFamily[]{EffectFamily.STEAM, EffectFamily.FALLOUT}) {
            for (var part : family.frame(1, 1, 900, 0).parts()) assertThat(part.heat()).isZero();
        }
        assertThat(EffectFamily.values()).extracting(Enum::name).doesNotContain("BLACK_HOLE");
    }

    @Test
    void nuclearFireballBecomesMushroomAndFleijaCollapses() {
        var early = EffectFamily.NUCLEAR.frame(1, 1, 900, 0);
        var late = EffectFamily.NUCLEAR.frame(200, 1, 900, 0);
        assertThat(early.parts()[0].fade()).isGreaterThan(early.parts()[1].fade());
        assertThat(late.parts()[0].fade()).isZero();
        assertThat(late.parts()[1].fade()).isEqualTo(1);
        assertThat(EffectFamily.FLEIJA.frame(440, 1, 900, 0).parts()[0].x())
                .isLessThan(EffectFamily.FLEIJA.frame(340, 1, 900, 0).parts()[0].x());
    }

    @Test
    void rejectsInvalidClocksAndOverflowedBounds() {
        for (EffectFamily family : EffectFamily.values()) {
            assertThatThrownBy(() -> family.frame(-1, 1, 100, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> family.frame(1, 0, 100, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> family.frame(1, 1, Float.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> EffectFamily.IMPACT.frame(100, Float.MAX_VALUE, 900, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
