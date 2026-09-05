uniform sampler2D _gr_depth;
uniform float _gr_znear;
uniform float _gr_zfar;

out float frag;

void main() {
    float d = texelFetch(_gr_depth, ivec2(gl_FragCoord.xy), 0).r;
    float n = 2.0 * d - 1.0;
    frag = 2.0 * _gr_znear * _gr_zfar / (_gr_zfar + _gr_znear - n * (_gr_zfar - _gr_znear));
}
