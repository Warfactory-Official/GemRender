const int GEMRENDER_MORPH_HEADER = 4;

uniform samplerBuffer _gemrender_morphs;

void gemrender_applyMorph(in samplerBuffer bones, uint morphBase, int morphSet,
        uint vertexId, inout vec3 position, inout vec3 normal) {
    if (morphSet <= 0) {
        return;
    }

    int header = int(morphBase) + (morphSet - 1) * GEMRENDER_MORPH_HEADER;

    int dataBase = int(texelFetch(bones, header).r);
    int targetCount = int(texelFetch(bones, header + 1).r);
    int weightBase = int(morphBase) + int(texelFetch(bones, header + 2).r);
    int floatsPerDelta = int(texelFetch(bones, header + 3).r);

    int vertexOffset = dataBase + int(vertexId) * targetCount * floatsPerDelta;

    for (int t = 0; t < targetCount; t++) {
        float weight = texelFetch(bones, weightBase + t).r;

        if (weight == 0.0) {
            continue;
        }

        int delta = vertexOffset + t * floatsPerDelta;
        position += weight * vec3(
                texelFetch(_gemrender_morphs, delta).r,
                texelFetch(_gemrender_morphs, delta + 1).r,
                texelFetch(_gemrender_morphs, delta + 2).r);

        if (floatsPerDelta > 3) {
            normal += weight * vec3(
                    texelFetch(_gemrender_morphs, delta + 3).r,
                    texelFetch(_gemrender_morphs, delta + 4).r,
                    texelFetch(_gemrender_morphs, delta + 5).r);
        }
    }
}
