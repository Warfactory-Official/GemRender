// One spot light's shaft, over a scissored fullscreen triangle: view ray -> cone span clamped by scene depth -> march.
uniform int _gs_slot;
uniform mat3 _gs_viewInverse;
uniform vec2 _gs_tanHalf;
uniform vec2 _gs_size;
uniform float _gs_dayFade;
uniform sampler2D _gs_scene;

out vec4 fragColor;

void main() {
    vec2 ndc = gl_FragCoord.xy / _gs_size * 2.0 - 1.0;
    vec3 dv = normalize(vec3(ndc * _gs_tanHalf, -1.0));
    vec3 d = _gs_viewInverse * dv;
    float viewZ = texelFetch(_gs_scene, ivec2(gl_FragCoord.xy), 0).r;
    float tScene = viewZ / max(-dv.z, 1.0e-3);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec3 s = gemrender_shaft(_gs_slot, d, tScene, jitter) * _gs_dayFade;
    fragColor = vec4(1.0 - exp(-s), 1.0);
}
