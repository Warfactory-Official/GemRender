uniform sampler2D _gr_accumulate;
uniform sampler2D _gr_frontAccumulate;

out vec4 frag;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    vec4 whole = texelFetch(_gr_accumulate, px, 0);
    vec4 front = texelFetch(_gr_frontAccumulate, px, 0);
    vec4 behind = max(whole - front, vec4(0.));

    if (behind.a < 1e-5) {
        discard;
    }

    frag = vec4(behind.rgb / behind.a, 1. - exp(-behind.a));
}
