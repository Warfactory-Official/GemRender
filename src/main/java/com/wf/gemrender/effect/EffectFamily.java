package com.wf.gemrender.effect;

import com.wf.gemrender.volume.VolumeShape;
import com.wf.gemrender.volume.VolumeStyle;

/** Visual timelines in ticks/blocks. Gameplay, sound and camera shake stay with the caller. */
public enum EffectFamily {
    NUCLEAR, STEAM, FLEIJA, SOLINIUM, RAINBOW, EMP, ANTIMATTER, GROUND_WAVE, IMPACT, TRAIL, BEAM, FALLOUT;

    private static final VolumeStyle SMOKE = VolumeStyle.builder().density(0.22f).tint(0x514A43).ambient(0.16f).steps(96, 4).build();
    private static final VolumeStyle STEAM_STYLE = VolumeStyle.builder().density(0.09f).tint(0xC9CDCC).steps(48, 3).build();
    private static final VolumeStyle ENERGY = VolumeStyle.builder().density(1.8f).tint(0x030C24).ambient(0.3f).steps(64, 0).build();
    private static final VolumeStyle DUST = VolumeStyle.builder().density(0.09f).tint(0x64574A).steps(64, 2).build();
    private static final VolumeStyle FALLOUT_STYLE = VolumeStyle.builder().density(0.025f).tint(0x555347)
            .ambient(0.2f).sunStrength(1.0f).steps(48, 4).build();

    public record Part(VolumeShape shape, VolumeStyle style, float x, float y, float z, float offsetY,
                       float fade, float heat, float emission, int tint, float billow) {}
    public record Frame(Part[] parts, float grade, float flash, int tint) {}

    public Frame frame(float age, float scale, float lifetime, int variant) {
        if (!Float.isFinite(age) || !Float.isFinite(scale) || !Float.isFinite(lifetime)
                || age < 0 || scale <= 0 || lifetime <= 0) {
            throw new IllegalArgumentException("Effect needs finite nonnegative age and positive scale/lifetime");
        }
        float u = Math.min(age / lifetime, 1);
        float fade = smooth((1 - u) / 0.2f);
        float flash = (float) Math.exp(-age / Math.max(4 * scale, 0.5f)) * Math.min(age, 1);
        int colorVariant = variant & 255;
        int tint = colorVariant == 1 ? 0x71FF61 : colorVariant == 2 ? 0xD7BFFF : 0xFFFFFF;
        float fire = (float) Math.exp(-age / Math.max(480 * scale, 1)) * fade;
        float r = Math.max(0.1f, age * 2 * scale);
        Frame result = switch (this) {
            case NUCLEAR, STEAM -> {
                float rise = smooth((age - 25 * scale) / Math.max(135 * scale, 1));
                float ball = Math.max(0.1f, (14 * Math.min(scale * 4, 1) + 16 * scale)
                        * (1 - (float) Math.exp(-age / Math.max(4 * scale, 0.2f))));
                float cap = (9 + age * 0.13f) * scale;
                float height = (4 + age * 0.12f) * scale;
                boolean steam = this == STEAM;
                VolumeStyle style = steam ? STEAM_STYLE : SMOKE;
                float thermal = fire * (1 - smooth((u - 0.55f) / 0.25f));
                float heat = steam ? 0 : Math.min(thermal, 1);
                yield new Frame(new Part[]{
                        part(VolumeShape.ELLIPSOID, style, ball, ball, ball, ball * 0.22f * (1 - rise) + height * rise,
                                fade * (1 - rise), heat, 1.6f, tint, 0.14f),
                        part(VolumeShape.MUSHROOM, style, cap, height, cap, height * 0.75f,
                                fade * rise, heat, 1.6f, tint, 0.14f),
                        part(VolumeShape.GROUND_WAVE, DUST, cap * 2 + age * 0.3f * scale, 6 * scale,
                                cap * 2 + age * 0.3f * scale, 1, fade * 0.45f, heat * 0.3f, 0.3f, tint, 0.12f)
                }, steam ? 0 : Math.min(thermal * 0.85f, 0.75f), steam ? 0 : flash * 2, steam ? 0xD4D8D8 : colorVariant == 0 ? 0xFF4C16 : tint);
            }
            case FLEIJA, SOLINIUM, RAINBOW, EMP, ANTIMATTER -> {
                tint = this == SOLINIUM ? 0x51FFD6 : this == EMP ? 0x619DFF
                        : this == ANTIMATTER ? 0xDFBDFF : 0xFFFFFF;
                float phase = Math.min(age / Math.max(lifetime * 0.42f, 0.1f), 4);
                float expansion = 1 - (float) Math.pow(Math.max(1 - phase, 0), 3);
                float collapse = 1 - smooth((phase - 1) / 0.2f);
                float radius = this == FLEIJA ? lifetime * scale * expansion * collapse
                        : this == ANTIMATTER ? scale * (1 - (float) Math.exp(-age / 3)) + 0.1f : r;
                float shellRadius = this == FLEIJA ? (lifetime * 0.25f + age * 3.2f) * scale : r * 1.4f;
                float shellFade = this == FLEIJA ? (float) Math.pow(Math.max(1 - u, 0), 1.5) * 0.7f : fade * 0.65f;
                float bodyFade = this == FLEIJA ? fade * collapse : fade;
                float collapseFlash = this == FLEIJA ? smooth((phase - 1.15f) / 0.05f)
                        * (float) Math.exp(-Math.max(phase - 1.2f, 0) * 10) * 2 : flash;
                VolumeShape body = this == RAINBOW ? VolumeShape.SPECTRAL : this == EMP ? VolumeShape.ELECTRIC : VolumeShape.ENERGY;
                yield new Frame(new Part[]{
                        part(body, ENERGY, radius / 0.77f, radius / 0.77f, radius / 0.77f, 0,
                                bodyFade * (this == EMP || this == SOLINIUM ? 0.35f : 1), 0.8f, 0.8f, tint, 0),
                        part(VolumeShape.SHELL, ENERGY, shellRadius / 0.73f, shellRadius / 0.73f,
                                shellRadius / 0.73f, 0, shellFade, 0.8f, 0.7f, tint, 0)
                }, fade * (this == FLEIJA ? 0.75f * (phase < 1.2f ? Math.min(phase * 3, 1)
                        : (float) Math.exp(-(phase - 1.2f) * 3)) : 0.45f), collapseFlash,
                        this == FLEIJA ? 0x296FE6 : tint);
            }
            case GROUND_WAVE -> new Frame(new Part[]{part(VolumeShape.GROUND_WAVE, DUST,
                    (2 + age * 1.5f) * scale, (2 + age * 0.06f) * scale,
                    (2 + age * 1.5f) * scale, 1, fade, 0.65f * fade, 0.5f,
                    colorVariant == 1 ? 0xFF1638 : tint, 0.12f)}, 0.12f * fade, flash * 0.25f, tint);
            case IMPACT -> new Frame(new Part[]{
                    part(VolumeShape.ELLIPSOID, SMOKE, 150 * scale, 35 * scale, 150 * scale,
                            10 * scale, fade, fire, 0.6f, tint, 0.14f),
                    part(VolumeShape.GROUND_WAVE, DUST, (age + 10) * scale, (14 + age * 0.1f) * scale,
                            (age + 10) * scale, 2, fade, fire * 0.8f, 0.8f, tint, 0.14f)
            }, fade * 0.4f, flash, 0xFFAA61);
            case TRAIL -> new Frame(new Part[]{part(VolumeShape.TRAIL, SMOKE, 10 * scale,
                    40 * scale, 10 * scale, 32 * scale, fade, 0.85f, 0.7f, tint, 0.12f)}, 0.12f * fade, 0, 0xFFB36C);
            case BEAM -> new Frame(new Part[]{part(VolumeShape.BEAM, ENERGY, 2 * scale,
                    160 * scale, 2 * scale, 132.8f * scale, fade,
                    0.65f + 0.25f * (float) Math.sin(age * 0.4f), 2, 0xFF577C, 0)}, 0.2f * fade, 0, 0xFF577C);
            case FALLOUT -> new Frame(new Part[]{part(VolumeShape.FALLOUT, FALLOUT_STYLE,
                    scale, 85, scale, 65, fade, 0, 0, 0xFFFFFF, 0.05f)}, 0.12f * fade, 0, 0x989875);
        };
        return (variant & Effects.NO_FLASH) == 0 ? result : new Frame(result.parts, result.grade, 0, result.tint);
    }

    private static Part part(VolumeShape shape, VolumeStyle style, float x, float y, float z, float offsetY,
                             float fade, float heat, float emission, int tint, float billow) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(offsetY)) {
            throw new IllegalArgumentException("Effect bounds overflowed");
        }
        return new Part(shape, style, Math.max(x, 0.01f), Math.max(y, 0.01f), Math.max(z, 0.01f),
                offsetY, Math.max(fade, 0), Math.min(Math.max(heat, 0), 1), emission, tint, billow);
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(value, 1));
        return t * t * (3 - 2 * t);
    }
}
