// GemRender: glTF morph targets
//
// The shader half of com.wf.gemrender.gltf.morph.MorphTargets and GltfMorphLayout, and the three must
// agree exactly. Kept in its own file for the same reason skin_lbs.glsl is: it compiles on its own, so
// the tier-2 GPU tests compile the SHIPPED file rather than a copy of it.
//
// A morph target is a per-vertex displacement from the base mesh, and a weight says how much of it to
// apply:
//
//     position = base + sum over t of weight[t] * delta[t][vertex]
//
// That cannot be written as a matrix, so unlike node animation and skinning it does not collapse into
// the skinning path. It runs BEFORE skinning, because a morph displaces a vertex in its own bind space
// and the skin carries it to where the bones are; the other order applies an unposed displacement to a
// posed vertex, which is right at rest and increasingly wrong as the model animates.
//
// Deltas are static, so they sit in their own buffer uploaded once at import (MorphBuffer, unit 11).
// Weights animate, so they ride in the per-frame bone buffer alongside the palette, at the instance's
// morphBase. That split is what makes morphing cost the same as skinning instead of the same as
// rebuilding the mesh. The per-instance block, all indices in floats:
//
//     morphBase +  0            set 0 header
//     morphBase +  4            set 1 header
//                               ...
//     morphBase + 4n            set 0's weights, then set 1's, ...
//
// and a header is { dataBase, targetCount, weightOffset, floatsPerDelta }, where weightOffset is
// relative to morphBase: it has to be, because the block's own base is not known until the block has
// been appended.

const int GEMRENDER_MORPH_HEADER = 4;

// The shared morph delta buffer. Unit 11: Flywheel's Samplers claims 0 to 9 and the bone palette took
// 10. Set from the host by SamplerBindings, for the reason skin_lbs.glsl gives.
uniform samplerBuffer _gemrender_morphs;

/// Applies one vertex's morph, in place.
///
/// `morphSet` is 1-based, and 0 means this vertex does not morph, which is every vertex of almost
/// every model, so the early out is the path that matters. It arrives smuggled through the vertex
/// overlay attribute; see GltfMesh for why that field was free.
///
/// `bones` is the same texture buffer skin_lbs.glsl reads its palette from. The headers and weights
/// live there rather than here because they change every frame and the deltas do not.
void gemrender_applyMorph(in samplerBuffer bones, uint morphBase, int morphSet,
        uint vertexId, inout vec3 position, inout vec3 normal) {
    if (morphSet <= 0) {
        return;
    }

    int header = int(morphBase) + (morphSet - 1) * GEMRENDER_MORPH_HEADER;

    int dataBase       = int(texelFetch(bones, header).r);
    int targetCount    = int(texelFetch(bones, header + 1).r);
    int weightBase     = int(morphBase) + int(texelFetch(bones, header + 2).r);
    int floatsPerDelta = int(texelFetch(bones, header + 3).r);

    // One vertex's targets are stored adjacently, so this walk is sequential in memory.
    int vertexOffset = dataBase + int(vertexId) * targetCount * floatsPerDelta;

    for (int t = 0; t < targetCount; t++) {
        float weight = texelFetch(bones, weightBase + t).r;

        // Most weights are zero most of the time, since a clip usually drives one or two targets of a
        // set at once, so skipping is worth the branch.
        if (weight == 0.0) {
            continue;
        }

        int delta = vertexOffset + t * floatsPerDelta;
        position += weight * vec3(
            texelFetch(_gemrender_morphs, delta).r,
            texelFetch(_gemrender_morphs, delta + 1).r,
            texelFetch(_gemrender_morphs, delta + 2).r);

        // Position-only targets store three floats rather than six. Reading the normal deltas anyway
        // would not merely waste three fetches, it would read the NEXT target's position.
        if (floatsPerDelta > 3) {
            normal += weight * vec3(
                texelFetch(_gemrender_morphs, delta + 3).r,
                texelFetch(_gemrender_morphs, delta + 4).r,
                texelFetch(_gemrender_morphs, delta + 5).r);
        }
    }
}
