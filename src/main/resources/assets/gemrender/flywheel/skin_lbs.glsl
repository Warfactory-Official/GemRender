const int GEMRENDER_INFLUENCES = 4;
const int GEMRENDER_FLOATS_PER_MATRIX = 16;

uniform samplerBuffer _gemrender_bones;

void gemrender_unpackJoints(vec2 packedJoints, out int joints[GEMRENDER_INFLUENCES]) {
    int lo = int(packedJoints.x * 256.0);
    int hi = int(packedJoints.y * 256.0);

    joints[0] = lo & 0xFF;
    joints[1] = (lo >> 8) & 0xFF;
    joints[2] = hi & 0xFF;
    joints[3] = (hi >> 8) & 0xFF;
}

void gemrender_unpackWeights(vec4 packedWeights, out float weights[GEMRENDER_INFLUENCES]) {
    weights[0] = packedWeights.r;
    weights[1] = packedWeights.g;
    weights[2] = packedWeights.b;
    weights[3] = packedWeights.a;
}

mat4 gemrender_boneMatrix(uint base, int joint) {
    int offset = (int(base) + joint) * GEMRENDER_FLOATS_PER_MATRIX;

    return mat4(
            texelFetch(_gemrender_bones, offset + 0).r, texelFetch(_gemrender_bones, offset + 1).r,
            texelFetch(_gemrender_bones, offset + 2).r, texelFetch(_gemrender_bones, offset + 3).r,
            texelFetch(_gemrender_bones, offset + 4).r, texelFetch(_gemrender_bones, offset + 5).r,
            texelFetch(_gemrender_bones, offset + 6).r, texelFetch(_gemrender_bones, offset + 7).r,
            texelFetch(_gemrender_bones, offset + 8).r, texelFetch(_gemrender_bones, offset + 9).r,
            texelFetch(_gemrender_bones, offset + 10).r, texelFetch(_gemrender_bones, offset + 11).r,
            texelFetch(_gemrender_bones, offset + 12).r, texelFetch(_gemrender_bones, offset + 13).r,
            texelFetch(_gemrender_bones, offset + 14).r, texelFetch(_gemrender_bones, offset + 15).r
    );
}

mat4 gemrender_skinMatrix(uint base, vec2 packedJoints, vec4 packedWeights) {
    int joints[GEMRENDER_INFLUENCES];
    float weights[GEMRENDER_INFLUENCES];
    gemrender_unpackJoints(packedJoints, joints);
    gemrender_unpackWeights(packedWeights, weights);

    mat4 skin = mat4(0.0);
    for (int influence = 0; influence < GEMRENDER_INFLUENCES; influence++) {

        if (weights[influence] > 0.0) {
            skin += weights[influence] * gemrender_boneMatrix(base, joints[influence]);
        }
    }
    return skin;
}
