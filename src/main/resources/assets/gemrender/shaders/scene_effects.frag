uniform mat4 _gr_inverse;
uniform vec2 _gr_size;
uniform vec3 _gr_source;
uniform vec3 _gr_tint;
uniform vec3 _gr_amount;
uniform float _gr_time;
uniform vec2 _gr_halo;
uniform int _gr_add;
out vec4 frag;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec2 uv = gl_FragCoord.xy / _gr_size;
    vec4 a = _gr_inverse * vec4(uv * 2.0 - 1.0, -1.0, 1.0);
    vec4 b = _gr_inverse * vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    vec3 ray = normalize(b.xyz / b.w - a.xyz / a.w);
    vec3 source = length(_gr_source) > 1e-5 ? normalize(_gr_source) : vec3(0.0, 1.0, 0.0);
    float facing = max(dot(ray, source), 0.0);
    float grade = _gr_amount.x;
    float grain = hash(gl_FragCoord.xy + floor(_gr_time * 30.0));
    if (_gr_add == 0) {
        vec3 exponent = vec3(grade);
        if (_gr_tint.r > _gr_tint.b) exponent.b *= 1.65;
        vec3 tint = pow(max(_gr_tint, vec3(0.015)), exponent);
        float vignette = 1.0 - grade * 0.2 * smoothstep(0.3, 0.8, length(uv - 0.5));
        frag = vec4(tint * vignette * (1.0 + (grain - 0.5) * grade * 0.06), 0.0);
    } else {
        vec3 axis = abs(source.y) < 0.95 ? vec3(0,1,0) : vec3(1,0,0);
        vec3 right = normalize(cross(source, axis));
        vec3 up = cross(right, source);
        float angle = atan(dot(ray, up), dot(ray, right));
        float rays = pow(0.5 + 0.5 * sin(angle * 37.0 + sin(angle * 19.0) + _gr_time * 0.3), 5.0);
        rays *= smoothstep(0.4, 0.95, facing) * smoothstep(-0.02, 0.15, ray.y);
        vec3 glow = _gr_tint * grade * (0.045 + pow(facing, 2.0) * 0.12 + pow(facing, 18.0) * 0.22 + rays * 0.25);
        float angular = max(_gr_halo.x, 1e-5);
        float radial2 = 2.0 * (1.0 - facing) / (angular * angular);
        float outer = exp(-radial2 * 0.22) * 0.28 + exp(-radial2 * 1.1) * 0.65;
        float cover = 1.0 - 0.88 * (1.0 - smoothstep(0.7, 1.3, radial2));
        float outside = 1.0 - smoothstep(0.65, 1.2, angular);
        glow += _gr_tint * _gr_halo.y * outer * cover * outside;
        glow += vec3(1.0, 0.97, 0.91) * _gr_amount.y * (0.55 + 0.45 * facing);
        float noise = _gr_amount.z;
        float specks = step(1.0 - noise * 0.004, hash(floor(gl_FragCoord.xy / 2.0) + floor(_gr_time * 60.0)));
        float streaks = step(1.0 - noise * 0.0004, hash(floor(gl_FragCoord.xy / vec2(24, 2)) + floor(_gr_time * 47.0)));
        frag = vec4(glow + vec3(specks + streaks * 0.5) * noise, 0.0);
    }
}
