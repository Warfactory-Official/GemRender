// Spliced by LightShaders after the #version line; GEMRENDER_LIGHT_* defines prepended there.

#define GEMRENDER_LIGHTS

// _grl_cameraFrac.w = Lights.IrView ordinal; _grl_lights[i*5+4].y = 1 => IR light, .z = falloff softening radius m (floor 1).
// _grl_night.x = night-sky irradiance on fully sky-lit surfaces, IR/NV channel only (Lights.nightSky).
// _grl_night.y = far light count; _grl_far = their light indices (LOD terrain, no section grid there).
layout(std140) uniform GemRenderLights {
    vec4 _grl_cameraFrac;
    ivec4 _grl_cameraSection;
    vec4 _grl_night;
    ivec4 _grl_far[2];
    vec4 _grl_lights[GEMRENDER_LIGHT_MAX * 5];
};

uniform sampler2DArray _grl_cookies;
uniform usampler3D _grl_grid;
uniform sampler2D _grl_shadows;

vec3 _grl_dpx;
vec3 _grl_dpy;
// IR irradiance of the last gemrender_lights call; already in its result when IrView.MERGED.
vec3 _grl_irSum;

// Uniform control flow only: derivatives.
void gemrender_lightPrepare(vec3 _grl_p) {
    _grl_dpx = dFdx(_grl_p);
    _grl_dpy = dFdy(_grl_p);
}

// Octahedral: unit direction <-> [0,1]^2.
vec2 gemrender_octEncode(vec3 _grl_v) {
    _grl_v /= abs(_grl_v.x) + abs(_grl_v.y) + abs(_grl_v.z);
    vec2 _grl_e = _grl_v.z >= 0.0 ? _grl_v.xy : (1.0 - abs(_grl_v.yx)) * vec2(_grl_v.x >= 0.0 ? 1.0 : -1.0, _grl_v.y >= 0.0 ? 1.0 : -1.0);
    return _grl_e * 0.5 + 0.5;
}

vec3 gemrender_octDecode(vec2 _grl_f) {
    _grl_f = _grl_f * 2.0 - 1.0;
    vec3 _grl_v = vec3(_grl_f, 1.0 - abs(_grl_f.x) - abs(_grl_f.y));
    float _grl_t = max(-_grl_v.z, 0.0);
    _grl_v.xy += vec2(_grl_v.x >= 0.0 ? -_grl_t : _grl_t, _grl_v.y >= 0.0 ? -_grl_t : _grl_t);
    return normalize(_grl_v);
}

// Spot tile uv; w = unit light -> point, axial = dot(w, axis) > 0.
vec2 gemrender_spotUv(vec3 _grl_w, float _grl_axial, vec3 _grl_axis, vec3 _grl_up, float _grl_tanOuter) {
    return vec2(dot(_grl_w, cross(_grl_axis, _grl_up)), dot(_grl_w, _grl_up)) / (_grl_axial * _grl_tanOuter) * 0.5 + 0.5;
}

// Lit fraction, 2x2 PCF of traced first-hit distances vs |q - light|.
float _grl_shadow(float _grl_tile, vec2 _grl_uv, float _grl_dq) {
    vec2 _grl_texel = _grl_uv * float(GEMRENDER_LIGHT_SHADOW_RES) - 0.5;
    ivec2 _grl_base = ivec2(floor(_grl_texel));
    vec2 _grl_f = _grl_texel - vec2(_grl_base);
    int _grl_t = int(_grl_tile);
    ivec2 _grl_origin = ivec2(_grl_t % GEMRENDER_LIGHT_SHADOW_TILES, _grl_t / GEMRENDER_LIGHT_SHADOW_TILES) * GEMRENDER_LIGHT_SHADOW_RES;
    ivec2 _grl_hi = ivec2(GEMRENDER_LIGHT_SHADOW_RES - 1);
    vec4 _grl_hit = vec4(texelFetch(_grl_shadows, _grl_origin + clamp(_grl_base, ivec2(0), _grl_hi), 0).r,
            texelFetch(_grl_shadows, _grl_origin + clamp(_grl_base + ivec2(1, 0), ivec2(0), _grl_hi), 0).r,
            texelFetch(_grl_shadows, _grl_origin + clamp(_grl_base + ivec2(0, 1), ivec2(0), _grl_hi), 0).r,
            texelFetch(_grl_shadows, _grl_origin + clamp(_grl_base + ivec2(1, 1), ivec2(0), _grl_hi), 0).r);
    vec4 _grl_lit = step(vec4(_grl_dq), _grl_hit);
    return mix(mix(_grl_lit.x, _grl_lit.y, _grl_f.x), mix(_grl_lit.z, _grl_lit.w, _grl_f.x), _grl_f.y);
}

// Wide cones spread the same RES^2 tile over a large area: the texels get coarse, the lit/shadow edge steps and swims
// with the beam. Five taps around the centre (1.3 texel out) turn that into a soft penumbra instead.
float _grl_shadowWide(float _grl_tile, vec2 _grl_uv, float _grl_dq) {
    const float _grl_r = 1.3 / float(GEMRENDER_LIGHT_SHADOW_RES);
    float _grl_s = _grl_shadow(_grl_tile, _grl_uv, _grl_dq) * 2.0;
    _grl_s += _grl_shadow(_grl_tile, _grl_uv + vec2(_grl_r, _grl_r), _grl_dq);
    _grl_s += _grl_shadow(_grl_tile, _grl_uv + vec2(-_grl_r, _grl_r), _grl_dq);
    _grl_s += _grl_shadow(_grl_tile, _grl_uv + vec2(_grl_r, -_grl_r), _grl_dq);
    _grl_s += _grl_shadow(_grl_tile, _grl_uv + vec2(-_grl_r, -_grl_r), _grl_dq);
    return _grl_s * (1.0 / 6.0);
}

vec3 _grl_one(int _grl_i, vec3 _grl_p, vec3 _grl_n, float _grl_footprint) {
    vec4 _grl_a = _grl_lights[_grl_i * 5];
    vec3 _grl_l = _grl_a.xyz - _grl_p;
    float _grl_d2 = dot(_grl_l, _grl_l);
    float _grl_r2 = _grl_a.w * _grl_a.w;
    if (_grl_d2 >= _grl_r2) {
        return vec3(0.0);
    }

    vec4 _grl_b = _grl_lights[_grl_i * 5 + 1];
    vec4 _grl_c = _grl_lights[_grl_i * 5 + 2];
    float _grl_tile = _grl_lights[_grl_i * 5 + 4].x;
    float _grl_dist = sqrt(_grl_d2);
    _grl_l /= _grl_dist;

    float _grl_x = _grl_d2 / _grl_r2;
    float _grl_window = 1.0 - _grl_x * _grl_x;
    float _grl_soft = _grl_lights[_grl_i * 5 + 4].z;
    vec3 _grl_e = _grl_b.rgb * (_grl_window * _grl_window / (_grl_d2 + max(_grl_soft * _grl_soft, 1.0)));
    bool _grl_faced = dot(_grl_n, _grl_n) > 0.25;
    if (_grl_faced) {
        float _grl_facing = dot(_grl_n, _grl_l);
        if (_grl_facing <= 0.0) {
            return vec3(0.0);
        }
        _grl_e *= _grl_facing;
    }
    bool _grl_spot = _grl_c.w > -1.5;
    float _grl_tanOuter = _grl_spot ? sqrt(1.0 - _grl_c.w * _grl_c.w) / _grl_c.w : 0.0;
    // Shadow sample point: half a block into the air the face looks onto, then toward the light by ~3 shadow
    // texels (grazing receivers: a neighbour texel's ray hits the floor first). Sum <= 1 block: never through
    // a 1-block wall.
    // Ternary only as a whole initializer: Veil's GLSL re-printer drops parens around a ternary operand.
    float _grl_spread = _grl_spot ? 2.0 * _grl_tanOuter : 4.7;
    vec3 _grl_off = _grl_faced ? _grl_n : _grl_l;
    float _grl_shadowTexel = _grl_dist * _grl_spread / float(GEMRENDER_LIGHT_SHADOW_RES);
    vec3 _grl_q = _grl_p + _grl_off * 0.5 + _grl_l * min(3.0 * _grl_shadowTexel, 0.5);
    vec3 _grl_wq = _grl_q - _grl_a.xyz;
    float _grl_dq = length(_grl_wq);
    _grl_wq /= _grl_dq;

    if (_grl_spot) {
        vec4 _grl_d = _grl_lights[_grl_i * 5 + 3];
        float _grl_axial = -dot(_grl_l, _grl_c.xyz);
        float _grl_cone = clamp((_grl_axial - _grl_c.w) * _grl_d.w, 0.0, 1.0);
        if (_grl_cone <= 0.0) {
            return vec3(0.0);
        }
        _grl_e *= _grl_cone * _grl_cone;

        if (_grl_tile >= 0.0) {
            float _grl_axialQ = dot(_grl_wq, _grl_c.xyz);
            vec2 _grl_suv = gemrender_spotUv(_grl_wq, _grl_axialQ, _grl_c.xyz, _grl_d.xyz, _grl_tanOuter);
            float _grl_lit = 0.0;
            if (_grl_axialQ > 0.0) {
                if (_grl_tanOuter > 1.2) {
                    _grl_lit = _grl_shadowWide(_grl_tile, _grl_suv, _grl_dq);
                } else {
                    _grl_lit = _grl_shadow(_grl_tile, _grl_suv, _grl_dq);
                }
            }
            _grl_e *= _grl_lit;
        }
        if (_grl_b.w >= 0.0) {
            vec2 _grl_uv = gemrender_spotUv(-_grl_l, _grl_axial, _grl_c.xyz, _grl_d.xyz, _grl_tanOuter);
            float _grl_texel = 2.0 * _grl_dist * _grl_axial * _grl_tanOuter / float(GEMRENDER_LIGHT_COOKIE_SIZE);
            float _grl_lod = log2(max(_grl_footprint / _grl_texel, 1.0));
            _grl_e *= textureLod(_grl_cookies, vec3(_grl_uv, _grl_b.w), _grl_lod).rgb;
        }
    } else if (_grl_tile >= 0.0) {
        _grl_e *= _grl_shadow(_grl_tile, gemrender_octEncode(_grl_wq), _grl_dq);
    }
    return _grl_e;
}

// p camera-relative; n world-oriented, zero = no facing. Returns irradiance multiplier for albedo.
vec3 gemrender_lights(vec3 _grl_p, vec3 _grl_n) {
    _grl_irSum = vec3(0.0);
    if (_grl_cameraSection.w == 0) {
        return vec3(0.0);
    }

    ivec3 _grl_cell = (_grl_cameraSection.xyz + ivec3(floor((_grl_p + _grl_cameraFrac.xyz) * (1.0 / 16.0))))
            & (GEMRENDER_LIGHT_GRID - 1);
    uvec2 _grl_mask = texelFetch(_grl_grid, _grl_cell, 0).xy;
    float _grl_footprint = max(length(_grl_dpx), length(_grl_dpy));

    vec3 _grl_sum = vec3(0.0);
    for (int _grl_word = 0; _grl_word < 2; _grl_word++) {
        uint _grl_m = _grl_word == 0 ? _grl_mask.x : _grl_mask.y;
        while (_grl_m != 0u) {
            uint _grl_bit = _grl_m & (~_grl_m + 1u);
            _grl_m ^= _grl_bit;
            int _grl_i = _grl_word * 32 + int(round(log2(float(_grl_bit))));
            vec3 _grl_e = _grl_one(_grl_i, _grl_p, _grl_n, _grl_footprint);
            if (_grl_lights[_grl_i * 5 + 4].y > 0.5) {
                _grl_irSum += _grl_e;
            } else {
                _grl_sum += _grl_e;
            }
        }
    }
    return _grl_sum + _grl_irSum * step(1.5, _grl_cameraFrac.w);
}

// gemrender_lights for surfaces past the section grid: far list only.
vec3 gemrender_farLights(vec3 _grl_p, vec3 _grl_n) {
    _grl_irSum = vec3(0.0);
    int _grl_count = int(_grl_night.y);
    float _grl_footprint = max(length(_grl_dpx), length(_grl_dpy));
    vec3 _grl_sum = vec3(0.0);
    for (int _grl_k = 0; _grl_k < _grl_count; _grl_k++) {
        int _grl_i = _grl_far[_grl_k >> 2][_grl_k & 3];
        vec3 _grl_e = _grl_one(_grl_i, _grl_p, _grl_n, _grl_footprint);
        if (_grl_lights[_grl_i * 5 + 4].y > 0.5) {
            _grl_irSum += _grl_e;
        } else {
            _grl_sum += _grl_e;
        }
    }
    return _grl_sum + _grl_irSum * step(1.5, _grl_cameraFrac.w);
}

// Moon + starlight; sky = lightmap sky level 0..1. SPLIT: IR channel; MERGED: colour (gemrender_mergedNight).
vec3 gemrender_nightSky(float _grl_skyLevel) {
    return vec3(_grl_night.x * _grl_skyLevel * _grl_skyLevel);
}

vec3 gemrender_mergedNight(float _grl_skyLevel) {
    return gemrender_nightSky(_grl_skyLevel) * step(1.5, _grl_cameraFrac.w);
}

// Zero stays zero: no facing.
vec3 gemrender_lightNormal(vec3 _grl_n) {
    float _grl_l2 = dot(_grl_n, _grl_n);
    return _grl_l2 > 1.0e-8 ? _grl_n * inversesqrt(_grl_l2) : vec3(0.0);
}

// Faceted normal from derivatives, oriented to the camera; for vertex formats without a normal.
vec3 gemrender_lightFaceNormal(vec3 _grl_p) {
    vec3 _grl_n = normalize(cross(_grl_dpx, _grl_dpy));
    return dot(_grl_n, _grl_p) > 0.0 ? -_grl_n : _grl_n;
}
