uniform sampler2D _gr_accumulate;

out vec4 frag;

void main() {
    vec4 acc = texelFetch(_gr_accumulate, ivec2(gl_FragCoord.xy), 0);

    if (acc.a < 1e-5) {
        discard;
    }

    frag = vec4(acc.rgb / acc.a, 1. - exp(-acc.a));
}
