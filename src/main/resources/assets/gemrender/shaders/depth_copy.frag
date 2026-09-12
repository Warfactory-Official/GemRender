uniform sampler2D _gr_depth;
uniform sampler2D _gr_depth2;

uniform float _gr_twoSources;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(_gr_depth, px, 0).r;
    if (_gr_twoSources > 0.5) {
        depth = min(depth, texelFetch(_gr_depth2, px, 0).r);
    }
    gl_FragDepth = depth;
}
