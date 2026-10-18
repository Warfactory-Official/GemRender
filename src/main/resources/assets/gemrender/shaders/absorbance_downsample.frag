uniform sampler2D _gr_depth;
uniform int _gr_factor;
uniform float _gr_znear;
uniform float _gr_zfar;

out float frag;

// Farthest of the block: media behind any covered surface survive the test; upsample picks the matching texel.
// frag = linear view depth, stands in for _gemrender_sceneDepth while drawing reduced.
void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * _gr_factor;
    ivec2 last = textureSize(_gr_depth, 0) - 1;
    float d = 0.;
    for (int y = 0; y < _gr_factor; y++) {
        for (int x = 0; x < _gr_factor; x++) {
            d = max(d, texelFetch(_gr_depth, min(base + ivec2(x, y), last), 0).r);
        }
    }
    gl_FragDepth = d;
    frag = linearize_depth(d, _gr_znear, _gr_zfar);
}
