uniform sampler2D _gr_accumulate;
uniform sampler2D _gr_frontAccumulate;
uniform sampler2D _gr_depthRange;
uniform sampler2DArray _gr_coefficients;
uniform sampler2D _gr_waterDepth;
uniform float _gr_znear;
uniform float _gr_zfar;

out vec4 frag;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    vec4 whole = texelFetch(_gr_accumulate, px, 0);
    vec4 front = texelFetch(_gr_frontAccumulate, px, 0);
    vec4 behind = max(whole - front, vec4(0.));

    if (behind.a < 1e-5) {
        discard;
    }

    vec2 range = texelFetch(_gr_depthRange, px, 0).rg;
    float waterLinear = linearize_depth(texelFetch(_gr_waterDepth, px, 0).r, _gr_znear, _gr_zfar);
    float waterNorm = clamp((waterLinear + range.x) / (range.x + range.y), 0., 1.);

    float tWater = transmittance(_gr_coefficients, waterNorm);
    float tTotal = total_transmittance(_gr_coefficients);
    float tBehind = clamp(tTotal / max(tWater, 1e-5), 0., 1.);

    frag = vec4(behind.rgb / behind.a, 1. - tBehind);
}
