// Light shafts: in-scattered light along a view ray through one spot light's cone. Spliced after lights.glsl (GemRenderLights
// block, _grl_lights, _grl_shadow, gemrender_spotUv), so it reads the same light records and the same traced shadow tiles
// the surfaces do: a sample point is lit iff it is nearer the light than the tile's first hit along its ray.

uniform float _gs_density;  // scattering per metre of air
uniform float _gs_phaseG;   // Henyey-Greenstein anisotropy, > 0 = scatters toward the viewer looking at the lamp
uniform float _gs_dust;     // dust modulation depth, 0 = uniform air
uniform float _gs_time;     // seconds, drifts the dust
uniform int _gs_steps;      // march steps at most

const float GS_PI = 3.14159265359;
// Metres per step before the step count is capped: a short chord through the cone needs few samples.
const float GS_STEP_M = 1.0;

float gs_hg(float c, float g) {
    float gg = g * g;
    float d = max(1.0 + gg - 2.0 * g * c, 1.0e-3);
    return (1.0 - gg) / (4.0 * GS_PI * d * sqrt(d));
}

// Ray (origin 0, unit direction d) inside the forward cone of apex A, unit axis a, cosine half-angle c, within R of the
// apex and tMax along the ray: [t0, t1]; t1 <= t0 means it misses.
vec2 gs_span(vec3 A, vec3 a, float c, float R, vec3 d, float tMax) {
    const vec2 none = vec2(1.0, 0.0);
    vec3 w = -A;
    float sb = dot(d, w);
    float sd = sb * sb - (dot(w, w) - R * R);
    if (sd <= 0.0) return none;
    float sq = sqrt(sd);
    float lo = max(0.0, -sb - sq), hi = min(tMax, -sb + sq);
    float da = dot(d, a), wa = dot(w, a);
    // Forward nappe only: axial distance > 0.
    if (abs(da) > 1.0e-6) {
        float ta = -wa / da;
        if (da > 0.0) lo = max(lo, ta); else hi = min(hi, ta);
    } else if (wa <= 0.0) {
        return none;
    }
    if (hi <= lo) return none;
    // Inside the cone: f(t) = (axial)^2 - c^2 |P - A|^2 >= 0, a quadratic in t.
    float c2 = c * c;
    float qa = da * da - c2;
    float qb = 2.0 * (da * wa - c2 * dot(d, w));
    float qc = wa * wa - c2 * dot(w, w);
    if (abs(qa) < 1.0e-7) {
        if (abs(qb) < 1.0e-9) return qc >= 0.0 ? vec2(lo, hi) : none;
        float r = -qc / qb;
        return qb > 0.0 ? vec2(max(lo, r), hi) : vec2(lo, min(hi, r));
    }
    float disc = qb * qb - 4.0 * qa * qc;
    if (disc < 0.0) return qa > 0.0 ? vec2(lo, hi) : none;
    float sr = sqrt(disc);
    float r1 = (-qb - sr) / (2.0 * qa), r2 = (-qb + sr) / (2.0 * qa);
    if (r1 > r2) { float s = r1; r1 = r2; r2 = s; }
    if (qa < 0.0) return vec2(max(lo, r1), min(hi, r2));
    // qa > 0: inside is outside (r1, r2). The forward nappe is convex, so at most one piece survives.
    vec2 left = vec2(lo, min(hi, r1)), right = vec2(max(lo, r2), hi);
    return (left.y - left.x) > (right.y - right.x) ? left : right;
}

// In-scattered radiance (linear, before tone mapping) of light slot along the ray. tScene: distance along the ray to
// geometry (large for sky); jitter in [0,1) offsets the samples.
vec3 gemrender_shaft(int slot, vec3 d, float tScene, float jitter) {
    vec4 pa = _grl_lights[slot * 5];
    vec4 pb = _grl_lights[slot * 5 + 1];
    vec4 pc = _grl_lights[slot * 5 + 2];
    vec4 pd = _grl_lights[slot * 5 + 3];
    vec4 pe = _grl_lights[slot * 5 + 4];
    float cosO = pc.w;
    if (cosO < -1.5 || cosO <= 0.0) return vec3(0.0);
    vec3 A = pa.xyz, a = pc.xyz;
    float R = pa.w;
    vec2 span = gs_span(A, a, cosO, R, d, tScene);
    float len = span.y - span.x;
    if (len <= 0.0) return vec3(0.0);

    int n = clamp(int(ceil(len / GS_STEP_M)), 3, _gs_steps);
    float tanO = sqrt(1.0 - cosO * cosO) / cosO;
    float tile = pe.x;
    float soft2 = max(pe.z * pe.z, 1.0);
    float invR2 = 1.0 / (R * R);
    // World-anchored, 16 m periodic (the section the camera is in changes without the dust popping).
    vec3 wcam = _grl_cameraFrac.xyz;
    const float W = 6.28318530718 / 16.0;

    // Equi-angular samples about the ray's closest approach to the light: the 1 / (r^2 + soft^2) falloff becomes constant
    // per step, so the near field of a bright lamp is smooth with a handful of samples instead of banded.
    float tc = dot(A, d);
    float hh = sqrt(max(dot(A, A) - tc * tc, 0.0) + soft2);
    float th0 = atan(span.x - tc, hh);
    float dth = (atan(span.y - tc, hh) - th0) / float(n);

    float sum = 0.0;
    for (int i = 0; i < n; i++) {
        float tn = tan(th0 + (float(i) + jitter) * dth);
        float t = tc + hh * tn;
        float dt = hh * (1.0 + tn * tn) * dth;
        vec3 P = d * t;
        vec3 L = P - A;
        float r2 = dot(L, L);
        float r = sqrt(r2);
        vec3 Lh = L / r;
        float axial = dot(Lh, a);
        float cone = clamp((axial - cosO) * pd.w, 0.0, 1.0);
        if (cone <= 0.0) continue;
        float x = r2 * invR2;
        float window = 1.0 - x * x;
        float e = cone * cone * window * window / (r2 + soft2);
        if (tile >= 0.0) {
            e *= _grl_shadow(tile, gemrender_spotUv(Lh, axial, a, pd.xyz, tanO), r);
        }
        vec3 wp = P + wcam;
        float dust = 1.0 + _gs_dust * sin(W * dot(wp, vec3(3.0, 2.0, 5.0)) + _gs_time * 0.7)
                * sin(W * dot(wp, vec3(-2.0, 4.0, 1.0)) - _gs_time * 0.45);
        sum += e * gs_hg(dot(Lh, -d), _gs_phaseG) * dust * dt;
    }
    return pb.rgb * (sum * _gs_density);
}
