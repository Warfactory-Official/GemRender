// GemRender: glTF vertex animation inside a Flywheel instance vertex shader.
#include "gemrender:skin_lbs.glsl"
#include "gemrender:morph.glsl"

void flw_instanceVertex(in FlwInstance i) {
    // The three smuggled attributes are read here and overwritten below; nothing else reads them.
    // color -> blend weights, light -> joint indices, overlay -> morph set index. Which of light and
    // overlay carries which is not arbitrary: every mesh needs joints and only a morphed one needs a
    // set index, and light survives shader-pack compatibility layers that drop the overlay.
    int morphSet = flw_vertexOverlay.x;

    vec3 position = flw_vertexPos.xyz;
    vec3 normal = flw_vertexNormal;

    // Morph FIRST, then skin. A morph target displaces a vertex in its own bind space and the skin is
    // what carries it to where the bones are; the other order applies an unposed displacement to a
    // posed vertex, which is exactly right at rest and increasingly wrong as the model animates.
    gemrender_applyMorph(_gemrender_bones, i.morphBase, morphSet, flw_vertexId, position, normal);

    mat4 skin = gemrender_skinMatrix(i.boneBase, flw_vertexLight, flw_vertexColor);

    flw_vertexPos = i.pose * (skin * vec4(position, 1.0));

    // The inverse-transpose is the correct normal matrix under non-uniform scale, but it is expensive
    // and skinning matrices are overwhelmingly rigid in practice. Using the upper-left 3x3 directly is
    // what MCglTF does and what every real-time skinning implementation does.
    flw_vertexNormal = mat3(i.pose) * (mat3(skin) * normal);

    // Restore the three fields the encodings borrowed. A GemRender mesh has no per-vertex colour, overlay
    // or light of its own: colour and light come from the instance, and overlay is simply unused.
    flw_vertexColor = i.color;
    flw_vertexOverlay = ivec2(0, 10);

    // Assigned, not max()'d with the vertex value, because the vertex value is a morph set index now
    // and folding it in would light the model by which part of it was morphing. This is also what
    // makes per-instance light take effect at all: GemRender meshes used to write FULL_BRIGHT into every
    // vertex, and the max() meant the instance could only ever brighten.
    flw_vertexLight = vec2(i.light) / 256.0;
}
