#version 150

uniform sampler2D Sampler0;

in vec3 vColor;
in vec2 vUv;
in vec3 vNormal;
in vec3 vView;
in vec3 vLocal;

uniform vec4 ColorModulator;
uniform float Outline;
uniform float Glow;
uniform float Reveal;
uniform float UseTexture;
uniform float Threshold;
uniform float Softness;
uniform vec3 LightDir;
uniform vec4 OutlineColor;
uniform vec3 ShadowTint;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    // Чернильное проявление и растворение: край рваный, с нитями.
    if (Reveal < 0.999) {
        float n = noise(vLocal * 5.0) * 0.6 + noise(vLocal * 17.0) * 0.3 + noise(vLocal * 43.0) * 0.1;
        if (n > Reveal) {
            discard;
        }
    }
    vec3 base = vColor;
    if (UseTexture > 0.5) {
        vec4 painted = texture(Sampler0, vUv);
        // Рваные дыры плаща: прозрачность в текстуре.
        if (painted.a < 0.5) {
            discard;
        }
        base = painted.rgb;
    }
    if (Outline > 0.0) {
        fragColor = OutlineColor;
        return;
    }
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) {
        n = -n;
    }
    // Свет как в аниме: граница задаётся для каждого материала, для ткани мягкая, для кожи чёткая.
    float ramp = smoothstep(Threshold - Softness, Threshold + Softness, dot(n, LightDir));
    vec3 color = mix(base * ShadowTint, base, ramp);
    color = mix(color, base, Glow);
    fragColor = vec4(color, 1.0) * ColorModulator;
}
