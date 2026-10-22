uniform sampler2D _gr_accumulate;
uniform sampler2D _gr_frontAccumulate;
uniform sampler2D _gr_emission;
uniform sampler2D _gr_frontEmission;
// Full-res share (Absorbance#resubmitFull), same layers.
uniform sampler2D _gr_fullAccumulate;
uniform sampler2D _gr_fullFrontAccumulate;
uniform sampler2D _gr_fullEmission;
uniform sampler2D _gr_fullFrontEmission;
uniform sampler2D _gr_depthRange;
uniform sampler2D _gr_cloudDepth;
uniform sampler2D _gr_lowDepth;
uniform sampler2D _gr_depth;
uniform int _gr_factor;
// 0 composite, 1 behind (all - front), 2 front
uniform int _gr_mode;
uniform float _gr_cloudPhase;
uniform float _gr_znear;
uniform float _gr_zfar;

out vec4 frag;

vec4 layer(vec4 all, vec4 front) {
    return _gr_mode == 0 ? all : _gr_mode == 1 ? max(all - front, vec4(0.)) : front;
}

vec4 accumulated(ivec2 t) {
    return layer(texelFetch(_gr_accumulate, t, 0), texelFetch(_gr_frontAccumulate, t, 0));
}

vec3 glowing(ivec2 t) {
    return layer(texelFetch(_gr_emission, t, 0), texelFetch(_gr_frontEmission, t, 0)).rgb;
}

// Accumulators sum tau => linear, filter before exp. Bilinear x depth match: texels across an edge lose.
void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    if (_gr_mode == 2 && _gr_cloudPhase >= 0.
            && (texelFetch(_gr_cloudDepth, px, 0).r < 1. ? 1. : 0.) != _gr_cloudPhase) {
        discard;
    }
    float z = linearize_depth(texelFetch(_gr_depth, px, 0).r, _gr_znear, _gr_zfar);
    vec2 at = gl_FragCoord.xy / float(_gr_factor) - 0.5;
    ivec2 i0 = ivec2(floor(at));
    vec2 f = at - vec2(i0);
    ivec2 last = textureSize(_gr_lowDepth, 0) - 1;

    vec4 acc = vec4(0.);
    vec3 glow = vec3(0.);
    float total = 0.;
    for (int k = 0; k < 4; k++) {
        ivec2 o = ivec2(k & 1, k >> 1);
        ivec2 t = clamp(i0 + o, ivec2(0), last);
        vec2 b = mix(1. - f, f, vec2(o));
        float match = 1. / (1e-2 + abs(texelFetch(_gr_lowDepth, t, 0).r - z) / max(z, 1e-3));
        float w = (b.x * b.y + 1e-4) * match;
        acc += accumulated(t) * w;
        glow += glowing(t) * w;
        total += w;
    }

    acc = acc / total + layer(texelFetch(_gr_fullAccumulate, px, 0), texelFetch(_gr_fullFrontAccumulate, px, 0));
    glow = glow / total + layer(texelFetch(_gr_fullEmission, px, 0), texelFetch(_gr_fullFrontEmission, px, 0)).rgb;
    frag = gemrender_absorbance(acc, glow);
    if (_gr_mode == 0) {
        gl_FragDepth = delinearize_depth(-texelFetch(_gr_depthRange, px, 0).r, _gr_znear, _gr_zfar);
    } else {
        gl_FragDepth = gl_FragCoord.z;
    }
}
