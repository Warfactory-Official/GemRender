uniform sampler2D _gr_accumulate;
uniform sampler2D _gr_frontAccumulate;
uniform sampler2D _gr_depthRange;
uniform sampler2DArray _gr_coefficients;
uniform sampler2D _gr_waterDepth;
uniform sampler2D _gr_cloudDepth;
uniform float _gr_cloudPhase;
uniform float _gr_znear;
uniform float _gr_zfar;

out vec4 frag;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);

    if (_gr_cloudPhase >= 0.) {

        float here = texelFetch(_gr_cloudDepth, px, 0).r < 1. ? 1. : 0.;
        if (here != _gr_cloudPhase) {
            discard;
        }
    }

    vec4 whole = texelFetch(_gr_accumulate, px, 0);
    vec4 front = texelFetch(_gr_frontAccumulate, px, 0);

    if (front.a < 1e-5) {
        discard;
    }

    float alpha;
    if (max(whole.a - front.a, 0.) < 1e-5) {

        alpha = 1. - total_transmittance(_gr_coefficients);
    } else {
        vec2 range = texelFetch(_gr_depthRange, px, 0).rg;
        float waterLinear = linearize_depth(texelFetch(_gr_waterDepth, px, 0).r, _gr_znear, _gr_zfar);
        float waterNorm = clamp((waterLinear + range.x) / (range.x + range.y), 0., 1.);
        alpha = 1. - transmittance(_gr_coefficients, waterNorm);
    }

    frag = vec4(front.rgb / front.a, alpha);

    float minDepth = -texelFetch(_gr_depthRange, px, 0).r;
    gl_FragDepth = delinearize_depth(minDepth, _gr_znear, _gr_zfar);
}
