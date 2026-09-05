#include "gemrender:volume.glsl"

uniform sampler3D _gemrender_volumeNoise;
uniform sampler2D _gemrender_sceneDepth;
uniform sampler3D _gemrender_volumeField;

const int GEMRENDER_VOLUME_MAX_STEPS = 128;
const int GEMRENDER_VOLUME_MAX_SUN_STEPS = 8;

vec2 gemrender_volumeSpan(in GemRenderVolume v, vec3 center, vec3 origin, vec3 direction) {
    vec3 inverse = 1.0 / direction;
    vec3 a = (center - v.extent - origin) * inverse;
    vec3 b = (center + v.extent - origin) * inverse;

    vec3 low = min(a, b);
    vec3 high = max(a, b);

    return vec2(max(max(max(low.x, low.y), low.z), 0.0), min(min(high.x, high.y), high.z));
}

float gemrender_volumeJitter() {
    return fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
}

float gemrender_sceneDistance(vec3 direction) {
    float viewZ = texelFetch(_gemrender_sceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    float along = -dot(direction, vec3(flw_viewInverse[2]));
    return viewZ / max(along, 1e-3);
}

float gemrender_volumeShape(in GemRenderVolume v, vec3 local) {
    vec3 unit = local / max(v.extent, vec3(1e-3));

    if (v.fieldScale > 0.0) {
        vec3 uvw = v.fieldOrigin + clamp(unit * 0.5 + 0.5, 0.0, 1.0) * v.fieldScale;
        return texture(_gemrender_volumeField, uvw).r;
    }

    float r = length(unit);
    float f = clamp((1.0 - r) / max(v.edge, 1e-3), 0.0, 1.0);
    return f * f * (3.0 - 2.0 * f);
}

float gemrender_volumeDensity(in GemRenderVolume v, vec3 p, vec3 center) {
    float shape = gemrender_volumeShape(v, p - center);
    if (shape <= 0.0) {
        return 0.0;
    }

    float drift = flw_renderSeconds * v.rise;
    vec3 q = (p - vec3(0.0, drift, 0.0)) * v.detail + v.seed;

    vec4 coarse = texture(_gemrender_volumeNoise, q * 0.25);
    vec4 fine = texture(_gemrender_volumeNoise, q * 0.35 - vec3(0.0, drift * 0.35, 0.0));

    float n = coarse.r * 0.55 + coarse.g * 0.25 + fine.b * 0.13 + fine.a * 0.07;

    return max(n * 1.6 - 0.3 - (1.0 - shape) * 1.3, 0.0);
}

float gemrender_volumePhase(float cosine, float g) {
    float gg = g * g;
    float d = max(1.0 + gg - 2.0 * g * cosine, 1e-4);
    return (1.0 - gg) / (d * sqrt(d));
}

float gemrender_volumeSunlight(in GemRenderVolume v, vec3 p, vec3 center, vec3 sun) {
    int steps = min(int(v.sunSteps), GEMRENDER_VOLUME_MAX_SUN_STEPS);
    if (steps <= 0) {
        return 1.0;
    }

    float reach = min(min(v.extent.x, v.extent.y), v.extent.z);
    float stride = max(reach, 0.5) / float(steps);
    float t = stride * 0.5;
    float sum = 0.0;

    for (int i = 0; i < steps; i++) {
        sum += gemrender_volumeDensity(v, p + sun * t, center) * stride;
        t += stride;
        stride *= 1.5;
    }

    return exp(-sum * v.density * v.sunDensity * v.fade);
}

vec4 gemrender_volumeMarch(in GemRenderVolume v, vec3 center, vec3 origin, vec3 direction, float near,
        float far, float jitter) {
    int steps = min(int(v.steps), GEMRENDER_VOLUME_MAX_STEPS);
    float dt = (far - near) / float(steps);

    vec3 sun = normalize(flw_light0Direction);
    float phase = gemrender_volumePhase(dot(direction, sun), v.phase);

    float transmittance = 1.0;
    vec3 scattered = vec3(0.0);
    float t = near + jitter * dt;

    for (int i = 0; i < steps; i++) {
        vec3 p = origin + direction * t;
        float density = gemrender_volumeDensity(v, p, center);

        if (density > 1e-3) {
            float extinction = density * v.density * v.fade;
            float absorbed = 1.0 - exp(-extinction * dt);

            float sunlight = gemrender_volumeSunlight(v, p, center, sun);
            vec3 lit = v.tint * (v.ambient + sunlight * phase * v.sunStrength);

            scattered += transmittance * absorbed * lit;
            transmittance *= 1.0 - absorbed;

            if (transmittance < 0.01) {
                break;
            }
        }

        t += dt;
    }

    float alpha = 1.0 - transmittance;
    return vec4(clamp(scattered / max(alpha, 1e-4), 0.0, 1.0), alpha);
}
