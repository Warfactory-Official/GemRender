// Locations: ResidentMesh guest VAO. Appended after the pack's Iris-patched vertex stage.
layout(location = 0) in vec3 _gr_position;
layout(location = 1) in vec3 _gr_normal;
layout(location = 2) in vec2 _gr_uv;
layout(location = 3) in vec4 _gr_weights;
layout(location = 4) in vec2 _gr_joints;
layout(location = 5) in float _gr_morphSet;
layout(location = 6) in mat4 _gr_pose;
layout(location = 10) in uint _gr_boneBase;
layout(location = 11) in uint _gr_morphBase;
layout(location = 12) in vec2 _gr_light;
layout(location = 13) in vec4 _gr_color;
layout(location = 14) in vec4 _gr_overlayUv;
// (octTangent.x + (w < 0 ? 4 : 0), octTangent.y, midUv)
layout(location = 15) in vec4 _gr_extras;

vec4 _gr_gPosition;
vec4 _gr_gNormal;
vec4 _gr_gTangent;
vec4 _gr_gUv;
vec4 _gr_gMidUv;
ivec4 _gr_gOverlay;
vec4 _gr_gLight;

vec3 _gr_octDecode(vec2 e) {
    vec3 v = vec3(e, 1.0 - abs(e.x) - abs(e.y));
    if (v.z < 0.0) {
        v.xy = (1.0 - abs(v.yx)) * vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
    }
    return normalize(v);
}

void _gr_guestVertex() {
    vec3 position = _gr_position;
    vec3 normal = _gr_normal;
    gemrender_applyMorph(_gemrender_bones, _gr_morphBase, int(_gr_morphSet), uint(gl_VertexID), position, normal);

    mat4 skin = gemrender_skinMatrix(_gr_boneBase, _gr_joints, _gr_weights);
    mat3 linear = mat3(_gr_pose) * mat3(skin);

    bool mirrored = _gr_extras.x > 2.0;
    vec3 tangent = _gr_octDecode(vec2(mirrored ? _gr_extras.x - 4.0 : _gr_extras.x, _gr_extras.y));

    _gr_gPosition = _gr_pose * (skin * vec4(position, 1.0));
    _gr_gNormal = vec4(normalize(linear * normal), 0.0);
    _gr_gTangent = vec4(normalize(linear * tangent), mirrored ? -1.0 : 1.0);
    _gr_gUv = vec4(_gr_uv + _gr_overlayUv.zw, 0.0, 1.0);
    _gr_gMidUv = vec4(_gr_extras.zw + _gr_overlayUv.zw, 0.0, 1.0);
    _gr_gOverlay = ivec4(ivec2(_gr_overlayUv.xy * 16.0), 0, 0);
    // Light < 0: emissive => full bright. Else ((l & 0xFFFF) + 8) / 256 per channel.
    _gr_gLight = vec4(_gr_light.x < 0.0 ? vec2(240.0) : _gr_light * 256.0 - 8.0, 0.0, 1.0);
}
