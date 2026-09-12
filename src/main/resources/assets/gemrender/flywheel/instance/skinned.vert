#include "gemrender:skin_lbs.glsl"
#include "gemrender:morph.glsl"

void flw_instanceVertex(in FlwInstance i) {

    int morphSet = flw_vertexOverlay.x;

    vec3 position = flw_vertexPos.xyz;
    vec3 normal = flw_vertexNormal;

    gemrender_applyMorph(_gemrender_bones, i.morphBase, morphSet, flw_vertexId, position, normal);

    mat4 skin = gemrender_skinMatrix(i.boneBase, flw_vertexLight, flw_vertexColor);

    flw_vertexPos = i.pose * (skin * vec4(position, 1.0));

    flw_vertexNormal = mat3(i.pose) * (mat3(skin) * normal);

    flw_vertexColor = i.color;
    flw_vertexOverlay = ivec2(0, 10);

    flw_vertexTexCoord += i.uvOffset;

    flw_vertexLight = vec2(i.light) / 256.0;
}
