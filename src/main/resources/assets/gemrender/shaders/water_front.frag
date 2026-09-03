// The in-front-of-water half of the split composite, drawn at AFTER_TRANSLUCENT_BLOCKS so it blends
// over the water instead of being depth-rejected against it. Compiled with flywheel's wavelet.glsl
// and depth.glsl prepended.
//
// When nothing at this pixel is behind the water the two accumulates are identical draws of identical
// fragments, the difference is exactly zero, and this reduces to the stock composite bit for bit:
// stock average, stock alpha, stock depth. That is the invariant that keeps a scene with no water in
// it rendering as it always did.
//
// Depth is written like stock (the stack's nearest fragment) but tested LEQUAL rather than ALWAYS:
// anything that wrote depth between Flywheel's stage and this one, a solid particle, a banner, the
// water itself where it stands in front, legitimately occludes. Fragments behind the nearest
// translucent terrain surface are never in this texture, so the terrain depth cannot reject anything
// that belongs here.

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

    if (front.a < 1e-5) {
        discard;
    }

    float alpha;
    if (max(whole.a - front.a, 0.) < 1e-5) {
        // Nothing behind the water here: the stock path, exactly.
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
