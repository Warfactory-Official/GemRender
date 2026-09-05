uniform sampler2D _gr_frontAccumulate;

out vec4 frag;

void main() {
    vec4 front = texelFetch(_gr_frontAccumulate, ivec2(gl_FragCoord.xy), 0);

    if (front.a < 1e-5) {
        discard;
    }

    frag = vec4(front.rgb / front.a, 1. - exp(-front.a));
}
