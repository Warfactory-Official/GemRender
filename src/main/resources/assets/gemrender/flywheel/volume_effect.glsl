#include "gemrender:volume_march.glsl"

#define GEMRENDER_VOLUME_EFFECT
float gemrender_effectViewDepth;

// 64^3 RGBA noise, 8x8 slices with a one-texel wrapped border; tools/make_volume_noise.py.
vec4 gemrender_effectNoise(vec3 p) {
    vec3 uvw = fract(p);
    float slice = uvw.z * 64.0 - 0.5;
    int z = int(floor(slice));
    int a = (z + 64) & 63;
    int b = (a + 1) & 63;
    vec2 uv = uvw.xy * 64.0 + 1.0;
    vec2 tileA = vec2(float(a & 7), float(a >> 3));
    vec2 tileB = vec2(float(b & 7), float(b >> 3));
    return mix(texture(flw_diffuseTex, (tileA * 66.0 + uv) / 528.0),
            texture(flw_diffuseTex, (tileB * 66.0 + uv) / 528.0), fract(slice));
}

float gemrender_effectUnion(float a, float b, float width) {
    float h = max(width - abs(a - b), 0.0) / width;
    return min(a, b) - h * h * width * 0.25;
}

// Shape IDs match VolumeShape. All surfaces leave room inside extent for billow displacement.
float gemrender_effectDistance(in GemRenderVolume v, vec3 p) {
    vec3 q = p / max(v.extent, vec3(1e-3));
    float d;
    if (v.shape == 1) {
        d = length(vec2(length(q.xz) - 0.54, q.y)) - 0.23;
    } else if (v.shape == 2) {
        vec2 cylinder = vec2(length(q.xz) - 0.58, abs(q.y) - 0.73);
        d = length(max(cylinder, 0.0)) + min(max(cylinder.x, cylinder.y), 0.0);
    } else if (v.shape == 3 || v.shape == 6 || v.shape == 7) {
        d = abs(length(q) - 0.73) - 0.07;
    } else if (v.shape == 8) {
        d = length(vec2((length(q.xz) - 0.67) * 1.6, q.y)) - 0.2;
    } else if (v.shape == 9) {
        d = max(length(q.xz) - (0.26 + 0.22 * (q.y + 0.8)), abs(q.y) - 0.8);
    } else if (v.shape == 10) {
        d = max(length(q.xz) - 0.82, abs(q.y) - 0.83);
    } else if (v.shape == 11) {
        d = max(length(q.xz) - 0.6, abs(q.y) - 0.83);
    } else if (v.shape == 4) {
        vec3 cap = q - vec3(0.0, 0.34, 0.0);
        float ring = length(vec2(length(cap.xz) - 0.48, cap.y)) - 0.22;
        float dome = (length((q - vec3(0.0, 0.46, 0.0)) / vec3(0.68, 0.29, 0.68)) - 1.0) * 0.29;
        float stem = max(length(q.xz) - (0.14 + 0.1 * exp(-4.0 * max(q.y + 0.75, 0.0))),
                abs(q.y + 0.2) - 0.55);
        float skirt = length(vec2(length(q.xz) - 0.61, (q.y + 0.72) * 1.5)) - 0.11;
        d = gemrender_effectUnion(gemrender_effectUnion(ring, dome, 0.12), stem, 0.14);
        d = gemrender_effectUnion(d, skirt, 0.1);
    } else {
        d = length(q) - 0.77;
    }
    return d * min(min(v.extent.x, v.extent.y), v.extent.z);
}

vec3 gemrender_effectFlow(in GemRenderVolume v, vec3 p) {
    vec3 q = p / max(v.extent, vec3(1e-3));
    float angle = v.time * v.flow;
    if (v.shape == 1 || v.shape == 4) {
        float radius = length(q.xz);
        vec2 radial = radius > 1e-5 ? q.xz / radius : vec2(1.0, 0.0);
        float capY = v.shape == 4 ? 0.34 : 0.0;
        vec2 section = vec2(radius - 0.5, q.y - capY);
        angle *= exp(-dot(section, section) * 6.0);
        float c = cos(angle), s = sin(angle);
        section = mat2(c, s, -s, c) * section;
        q = vec3(radial.x * (0.5 + section.x), section.y + capY, radial.y * (0.5 + section.x));
    } else {
        float c = cos(angle), s = sin(angle);
        q.xz = mat2(c, s, -s, c) * q.xz;
    }
    return q * v.extent - vec3(0.0, v.time * v.rise, 0.0);
}

float gemrender_effectDensity(in GemRenderVolume v, vec3 p, out float heat) {
    float extent = min(min(v.extent.x, v.extent.y), v.extent.z);
    // Billow size follows the effect, so enlarging a cloud does not tile small identical cells.
    vec3 q = gemrender_effectFlow(v, p) / max(extent * 1.8, 0.02);
    q *= max(v.detail / 0.6, 0.05);
    vec3 drift = vec3(v.time * 0.008, -v.time * 0.014, v.time * 0.005);
    q += drift + v.seed;
    vec3 warp = gemrender_effectNoise(q * 0.71 + vec3(0.17, 0.39, 0.63)).rgb - 0.5;
    q += warp * 0.18;
    vec4 coarse = gemrender_effectNoise(q);
    vec4 fine = gemrender_effectNoise(q * 2.03 + vec3(0.19, 0.43, 0.71));
    float billow = smoothstep(0.35, 0.8, coarse.r);
    float detail = smoothstep(0.35, 0.8, fine.r);
    float displacement = ((0.5 - billow) * 1.5 + (0.5 - detail) * 0.5) * v.billow * extent;
    float edge = max(extent * v.edge * 0.15, 0.01);
    float density = 1.0 - smoothstep(-edge, edge, gemrender_effectDistance(v, p) + displacement);
    density = max(density - coarse.g * v.billow * 2.0 * (1.0 - density), 0.0);
    float pockets = smoothstep(0.38, 0.74, coarse.b * 0.55 + detail * 0.6);
    float core = exp(-dot(p / max(v.extent, vec3(0.01)), p / max(v.extent, vec3(0.01))) * 1.7);
    heat = v.temperature * core * (0.04 + 1.35 * pockets * pockets);
    vec3 unit = p / max(v.extent, vec3(1e-3));
    if (v.shape == 3 || v.shape == 5 || v.shape == 6 || v.shape == 7 || v.shape == 11) {
        heat = v.temperature * (0.45 + 0.55 * pockets);
        if (v.shape == 6) {
            float arcs = 1.0 - smoothstep(0.01, 0.055, abs(fine.g - 0.43));
            heat *= 0.4 + 1.5 * arcs;
        }
    } else if (v.shape == 8) {
        heat *= exp(-max(unit.y + 0.1, 0.0) * 5.0);
    } else if (v.shape == 9) {
        heat *= exp(-(unit.y + 0.8) * 2.5);
    } else if (v.shape == 10) {
        vec4 cover = gemrender_effectNoise(vec3(q.x, 0.37, q.z));
        float deck = smoothstep(0.25, 0.45, unit.y) * (1.0 - smoothstep(0.65, 0.83, unit.y));
        float gaps = smoothstep(0.33, 0.65, cover.r);
        float curtain = smoothstep(0.45, 0.7, cover.g) * (0.04 + 0.14 * fine.a);
        density *= 0.012 + gaps * (deck + (1.0 - deck) * curtain);
        heat = 0.0;
    }
    return density;
}

vec3 gemrender_effectSpectrum(float hue) {
    return clamp(abs(fract(hue + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

vec3 gemrender_effectHeat(float heat) {
    heat = clamp(heat, 0.0, 1.0);
    vec3 red = vec3(0.6, 0.015, 0.002);
    vec3 orange = vec3(2.5, 0.35, 0.02);
    vec3 yellow = vec3(4.0, 1.8, 0.3);
    vec3 white = vec3(5.0, 4.2, 3.0);
    vec3 color = heat < 0.4 ? mix(red, orange, heat / 0.4)
            : heat < 0.75 ? mix(orange, yellow, (heat - 0.4) / 0.35)
            : mix(yellow, white, (heat - 0.75) / 0.25);
    return color * heat * heat;
}

vec3 gemrender_effectEmission(in GemRenderVolume v, vec3 p, float heat) {
    if (v.shape == 3 || v.shape == 5 || v.shape == 6 || v.shape == 7 || v.shape == 11) {
        vec3 color = v.shape == 6 ? vec3(0.3, 0.65, 1.0) : vec3(0.55, 0.95, 1.0);
        if (v.shape == 7) {
            color = gemrender_effectSpectrum(atan(p.z, p.x) / 6.2831853
                    + p.y / max(v.extent.y, 0.001) * 0.3 + v.time * 0.12);
        }
        return mix(color, vec3(1.0), smoothstep(0.7, 1.2, heat)) * heat * heat * 3.0 * v.emission;
    }
    return gemrender_effectHeat(heat) * v.emission;
}

float gemrender_effectSun(in GemRenderVolume v, vec3 p, vec3 sun) {
    float extent = min(min(v.extent.x, v.extent.y), v.extent.z);
    float stride = extent * 0.12;
    float opticalDepth = 0.0;
    int steps = min(int(v.sunSteps), GEMRENDER_VOLUME_MAX_SUN_STEPS);
    for (int k = 0; k < steps; k++) {
        p += sun * stride;
        float fill = clamp(-gemrender_effectDistance(v, p) / max(extent * 0.12, 0.01), 0.0, 1.0);
        if (v.shape == 10) {
            float heat;
            fill = gemrender_effectDensity(v, p, heat);
        }
        opticalDepth += fill * stride;
        stride *= 1.7;
    }
    return exp(-opticalDepth * v.density * min(1.0, 12.0 / max(extent, 0.01)) * v.sunDensity * v.fade);
}

// Analytic intervals keep a smooth limb without relying on a proxy face or an outside-only normal.
vec4 gemrender_energyMarch(in GemRenderVolume v, vec3 center, vec3 origin, vec3 direction,
        float near, float far, out vec3 emission, out float firstHit) {
    vec3 ro = (origin - center) / v.extent;
    vec3 rd = direction / v.extent;
    float radius = v.shape == 3 ? 0.73 : 0.77;
    float a = dot(rd, rd), b = dot(ro, rd);
    float discriminant = b * b - a * (dot(ro, ro) - radius * radius);
    emission = vec3(0.0);
    firstHit = far;
    if (discriminant <= 0.0) return vec4(0.0);
    float root = sqrt(discriminant);
    float enter = (-b - root) / a, leave = (-b + root) / a;
    if (leave <= near || enter >= far) return vec4(0.0);
    if (v.shape == 3) {
        float surface = enter >= near ? enter : leave;
        if (surface >= far) return vec4(0.0);
        firstHit = surface;
        float closestT = max(-b / a, 0.0);
        float impact = length(ro + rd * closestT) / radius;
        float limb = exp(-pow((impact - 1.0) / 0.045, 2.0));
        float alpha = (0.025 + limb * 0.35) * min(v.density, 1.0) * v.fade;
        emission = vec3(0.38, 0.8, 1.0) * (0.025 + limb * 0.9) * v.temperature * v.emission * v.fade;
        return vec4(v.tint * 0.25, alpha);
    }
    float start = max(enter, near), finish = min(leave, far);
    firstHit = start;
    vec3 surface = ro + rd * (enter >= near ? enter : leave);
    vec3 normal = normalize(surface / v.extent);
    float facing = abs(dot(normal, direction));
    float limb = pow(max(1.0 - facing, 0.0), 2.8);
    float filament = 0.0;
    for (int k = 0; k < 12; k++) {
        vec3 local = ro + rd * mix(start, finish, (float(k) + 0.5) / 12.0);
        float spin = v.time * 0.45;
        local.xz = mat2(cos(spin), sin(spin), -sin(spin), cos(spin)) * local.xz;
        vec3 q = local * 0.8 + v.seed + vec3(0.0, v.time * 0.035, 0.0);
        vec4 noise = gemrender_effectNoise(q);
        float strands = smoothstep(0.48, 0.75, noise.r) * smoothstep(0.38, 0.68, noise.b);
        filament += strands * strands / 12.0;
    }
    vec3 color = vec3(0.055, 0.5, 1.0);
    if (v.shape == 7) color = gemrender_effectSpectrum(atan(surface.z, surface.x + 1e-6)
            / 6.2831853 + surface.y * 0.3 + v.time * 0.12);
    if (v.shape == 6) {
        vec3 q = surface * 1.3 + floor(v.time * 8.0) * vec3(0.17, 0.31, 0.43);
        float arcs = 1.0 - smoothstep(0.006, 0.026, abs(gemrender_effectNoise(q).g - 0.42));
        filament += arcs * 1.8;
    }
    vec3 body = color * (0.02 + filament * 1.25);
    vec3 rim = color * limb * 1.5 + vec3(0.7, 0.95, 1.0) * limb * limb * 0.8;
    float optical = v.density * (finish - start) / max(min(min(v.extent.x, v.extent.y), v.extent.z), 0.01);
    float alpha = 1.0 - exp(-optical * v.fade);
    // The chord shrinks to nothing at the silhouette, so weighting the rim by it would dim the very limb it brightens.
    emission = (body * alpha + rim * sqrt(alpha)) * v.temperature * v.emission;
    return vec4(v.tint * 0.45, alpha);
}

vec4 gemrender_effectMarch(in GemRenderVolume v, vec3 center, vec3 origin, vec3 direction,
        float near, float far, float jitter, out vec3 emission, out float firstHit) {
    if (v.shape == 3 || v.shape == 5 || v.shape == 6 || v.shape == 7) {
        return gemrender_energyMarch(v, center, origin, direction, near, far, emission, firstHit);
    }
    int steps = clamp(int(v.steps), 1, GEMRENDER_VOLUME_MAX_STEPS);
    float stride = (far - near) / float(steps);
    vec3 sun = normalize(flw_light0Direction);
    float phase = gemrender_volumePhase(dot(direction, sun), v.phase);
    float transmittance = 1.0;
    vec3 scattered = vec3(0.0);
    emission = vec3(0.0);
    firstHit = far;
    for (int k = 0; k < steps; k++) {
        float t = near + (float(k) + jitter) * stride;
        vec3 p = origin + direction * t - center;
        float heat;
        float density = gemrender_effectDensity(v, p, heat);
        if (density > 1e-3) {
            firstHit = min(firstHit, t);
            float opticalScale = v.shape == 10 ? 1.0 : min(1.0, 12.0 / max(min(min(v.extent.x, v.extent.y), v.extent.z), 0.01));
            float absorbed = 1.0 - exp(-density * v.density * opticalScale * v.fade * stride);
            float sunlight = gemrender_effectSun(v, p, sun);
            vec3 lit = v.tint * (v.ambient + sunlight * phase * v.sunStrength);
            // Hot gas lights adjacent soot; cold pockets retain their dark body instead of clipping to white.
            lit += vec3(0.35, 0.065, 0.008) * v.temperature * v.temperature
                    * exp(-dot(p / v.extent, p / v.extent) * 2.0);
            lit *= mix(1.0, 0.45, v.temperature);
            scattered += transmittance * absorbed * lit;
            emission += transmittance * absorbed * gemrender_effectEmission(v, p, heat);
            transmittance *= 1.0 - absorbed;
            if (transmittance < 0.01) break;
        }
    }
    float alpha = 1.0 - transmittance;
    return vec4(scattered / max(alpha, 1e-4), alpha);
}
