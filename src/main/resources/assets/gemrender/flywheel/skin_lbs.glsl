// GemRender: linear blend skinning, as a standalone GLSL unit.
//
// The shader half of com.wf.gemrender.gltf.skin.BoneAttributeCodec, and the two must agree exactly. Read
// that class first: it explains why joint indices arrive in `light` and weights in `color`, and why a
// skinned mesh gives up per-vertex colour and light to make room.
//
// Kept in its own file, taking its inputs as parameters rather than reading Flywheel's `flw_*`
// globals, so that it compiles on its own. skinned.vert #includes it and passes the globals in; the
// tier-2 GPU tests #include the same text into a compute shader and check its output against a CPU
// reference. So the code that ships is the code that is tested, rather than a copy in a test file kept
// in sync by a comment asking future readers to keep it in sync.

const int GEMRENDER_INFLUENCES = 4;
const int GEMRENDER_FLOATS_PER_MATRIX = 16;

// The shared bone palette, as a texture buffer of raw floats. A texture buffer rather than an SSBO
// because Flywheel compiles ONE instance vertex shader and runs it on BOTH backends, and the
// instancing backend targets hardware with no SSBOs. See BoneBuffer.
//
// Unit 10 is the first one Flywheel's Samplers class does not claim, and the unit is set from the
// host by SamplerBindings rather than declared here. It used to be a `layout(binding = 10)`, which
// reads better and costs nothing on the default backends -- but it needs GLSL 420, and under a
// shaderpack this file is merged into the pack's own vertex shader, which iris-flw-compat compiles
// at GLSL 400. Every shaderpack anyone actually uses is below 420, so that one qualifier was the
// whole of "GemRender does not work with shaders": `unrecognized layout identifier 'binding'`, twice
// a frame, and nothing drawn. See gemrender-internal/docs/TESTING.md.
uniform samplerBuffer _gemrender_bones;

/// Unpacks the four joint indices smuggled through the light attribute.
///
/// The light attribute is a pair of UNSIGNED shorts that Flywheel's vertex layout hands over already
/// divided by 256. Multiplying back is exact, since 256 is a power of two and a 16-bit integer is
/// exact in a float, so the round trip loses nothing and needs no rounding.
///
/// Unsigned is the reason this field rather than the overlay, which is where the joints used to live:
/// the overlay is a pair of SIGNED shorts, so every packed value with the top bit set arrived negative
/// and had to be masked back, corrupting exactly the joint indices above 127. The other reason is
/// Iris, whose compat layer drops the overlay attribute entirely.
/// See gemrender-internal/docs/RESEARCH.md.
void gemrender_unpackJoints(vec2 packedJoints, out int joints[GEMRENDER_INFLUENCES]) {
    int lo = int(packedJoints.x * 256.0);
    int hi = int(packedJoints.y * 256.0);

    joints[0] = lo & 0xFF;
    joints[1] = (lo >> 8) & 0xFF;
    joints[2] = hi & 0xFF;
    joints[3] = (hi >> 8) & 0xFF;
}

/// Unpacks the four blend weights smuggled through the colour attribute.
void gemrender_unpackWeights(vec4 packedWeights, out float weights[GEMRENDER_INFLUENCES]) {
    weights[0] = packedWeights.r;
    weights[1] = packedWeights.g;
    weights[2] = packedWeights.b;
    weights[3] = packedWeights.a;
}

/// Fetches one bone matrix from the shared palette.
mat4 gemrender_boneMatrix(uint base, int joint) {
    int offset = (int(base) + joint) * GEMRENDER_FLOATS_PER_MATRIX;

    return mat4(
        texelFetch(_gemrender_bones, offset +  0).r, texelFetch(_gemrender_bones, offset +  1).r,
        texelFetch(_gemrender_bones, offset +  2).r, texelFetch(_gemrender_bones, offset +  3).r,
        texelFetch(_gemrender_bones, offset +  4).r, texelFetch(_gemrender_bones, offset +  5).r,
        texelFetch(_gemrender_bones, offset +  6).r, texelFetch(_gemrender_bones, offset +  7).r,
        texelFetch(_gemrender_bones, offset +  8).r, texelFetch(_gemrender_bones, offset +  9).r,
        texelFetch(_gemrender_bones, offset + 10).r, texelFetch(_gemrender_bones, offset + 11).r,
        texelFetch(_gemrender_bones, offset + 12).r, texelFetch(_gemrender_bones, offset + 13).r,
        texelFetch(_gemrender_bones, offset + 14).r, texelFetch(_gemrender_bones, offset + 15).r
    );
}

/// The blended skinning matrix for one vertex.
mat4 gemrender_skinMatrix(uint base, vec2 packedJoints, vec4 packedWeights) {
    int joints[GEMRENDER_INFLUENCES];
    float weights[GEMRENDER_INFLUENCES];
    gemrender_unpackJoints(packedJoints, joints);
    gemrender_unpackWeights(packedWeights, weights);

    mat4 skin = mat4(0.0);
    for (int influence = 0; influence < GEMRENDER_INFLUENCES; influence++) {
        // remaining slots are padding, so skipping them is worth the branch.
        if (weights[influence] > 0.0) {
            skin += weights[influence] * gemrender_boneMatrix(base, joints[influence]);
        }
    }
    return skin;
}
