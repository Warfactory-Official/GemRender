// Wavelet OIT (Colorwheel via CrankShaft guest_oit.glsl): rank r => 2^(r+1) coefficients, RGBA16F array layers.
// fma expanded: GLSL 330 has none.

const float _GR_OIT_NOISE = 0.07;

uniform sampler2D _gr_oitDepthRange;
uniform sampler2D _gr_oitBlueNoise;

float _gr_oitTentedBlueNoise(float normalizedDepth) {
    float tentIn = abs(normalizedDepth * 2. - 1.);
    float tentIn2 = tentIn * tentIn;
    float tentIn4 = tentIn2 * tentIn2;
    float tent = 1. - (tentIn2 * tentIn4);
    return texelFetch(_gr_oitBlueNoise, ivec2(gl_FragCoord.xy) % 64, 0).r * tent;
}

void _gr_oitAddAbsorbance(inout vec4[4] coefficients, float signal, float depth, int rank) {
    int count = 1 << (rank + 1);

    depth *= float(count - 1) / float(count);

    int index = clamp(int(floor(depth * float(count))), 0, count - 1);
    index += count - 1;

    for (int i = 0; i < (rank + 1); ++i) {
        int power = rank - i;
        float powerF = float(power);

        int newIndex = (index - 1) >> 1;
        float k = float((newIndex + 1) & ((1 << power) - 1));

        float waveletSign = float(((index & 1) << 1) - 1);
        float waveletPhase = float((index + 1) & 1) * exp2(-powerF);
        float addend = ((-exp2(-powerF) * k + depth) * waveletSign + waveletPhase) * exp2(powerF * 0.5) * signal;
        coefficients[newIndex >> 2][newIndex & 3] = addend;

        index = newIndex;
    }

    coefficients[(count - 1) >> 2][(count - 1) & 3] = signal * -depth + signal;
}

void _gr_oitAddTransmittance(inout vec4[4] coefficients, float transmittance, float depth, int rank) {
    _gr_oitAddAbsorbance(coefficients, -log(max(transmittance, 0.00001)), depth, rank);
}

float _gr_oitCoefficient(sampler2DArray coefficients, int index) {
    return texelFetch(coefficients, ivec3(gl_FragCoord.xy, index >> 2), 0)[index & 3];
}

float _gr_oitTotalAbsorbance(sampler2DArray coefficients, int rank) {
    int count = 1 << (rank + 1);

    float scale = _gr_oitCoefficient(coefficients, count - 1);
    if (scale == 0.) {
        return 0.;
    }

    int indexB = 2 * count - 2;
    float b = scale;

    for (int i = 0; i < (rank + 1); ++i) {
        int power = rank - i;

        int newIndexB = (indexB - 1) >> 1;
        float waveletSignB = float(((indexB & 1) << 1) - 1);
        b -= exp2(float(power) * 0.5) * _gr_oitCoefficient(coefficients, newIndexB) * waveletSignB;
        indexB = newIndexB;
    }

    return b;
}

// Undoes this fragment's own absorbance event before sampling (self-occlusion).
float _gr_oitSignalCorrectedAbsorbance(sampler2DArray coefficients, float depth, float signal, int rank) {
    int count = 1 << (rank + 1);

    float scale = _gr_oitCoefficient(coefficients, count - 1);
    if (scale == 0.) {
        return 0.;
    }

    depth *= float(count - 1) / float(count);

    scale -= signal * -depth + signal;

    float coefficientDepth = depth * float(count);
    int indexB = clamp(int(floor(coefficientDepth)), 0, count - 1);
    bool sampleA = indexB >= 1;
    int indexA = sampleA ? (indexB - 1) : indexB;

    indexB += count - 1;
    indexA += count - 1;

    float b = scale;
    float a = sampleA ? scale : 0.;

    for (int i = 0; i < (rank + 1); ++i) {
        int power = rank - i;
        float powerF = float(power);

        int newIndexB = (indexB - 1) >> 1;
        float waveletSignB = float(((indexB & 1) << 1) - 1);
        float coeffB = _gr_oitCoefficient(coefficients, newIndexB);

        float waveletPhaseB = float((indexB + 1) & 1) * exp2(-powerF);
        float k = float((newIndexB + 1) & ((1 << power) - 1));
        coeffB -= ((-exp2(-powerF) * k + depth) * waveletSignB + waveletPhaseB) * exp2(powerF * 0.5) * signal;

        b -= exp2(powerF * 0.5) * coeffB * waveletSignB;
        indexB = newIndexB;

        if (sampleA) {
            int newIndexA = (indexA - 1) >> 1;
            float waveletSignA = float(((indexA & 1) << 1) - 1);
            // The signal never contributed to A's coefficient.
            float coeffA = (newIndexA == newIndexB) ? coeffB : _gr_oitCoefficient(coefficients, newIndexA);
            a -= exp2(powerF * 0.5) * coeffA * waveletSignA;
            indexA = newIndexA;
        }
    }

    float t = coefficientDepth >= float(count) ? 1. : fract(coefficientDepth);

    return mix(a, b, t);
}

float _gr_oitTotalTransmittance(sampler2DArray coefficients, int rank) {
    return clamp(exp(-_gr_oitTotalAbsorbance(coefficients, rank)), 0., 1.);
}

float _gr_oitSignalCorrectedTransmittance(sampler2DArray coefficients, float depth, float transmittance, int rank) {
    return clamp(exp(-_gr_oitSignalCorrectedAbsorbance(coefficients, depth,
            -log(max(transmittance, 0.00001)), rank)), 0., 1.);
}
