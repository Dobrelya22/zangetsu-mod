#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Time;
uniform float Sway;
uniform float Outline;
uniform float ViewOutline;
uniform mat4 Bone;
uniform float Bend;

out vec3 vColor;
out vec2 vUv;
out vec3 vNormal;
out vec3 vView;
out vec3 vLocal;

// Ткань и волосы на ветру: волна бежит по ткани, сильнее там, где это задано в альфе вершины.
void main() {
    vec3 p = Position;
    vec3 surface = Normal;
    if (Bend > 0.5) {
        // Рукав гнётся в локте: у плеча вершина идёт за плечом, у запястья за предплечьем, между ними мягко.
        float w = smoothstep(0.36, 0.64, -p.y);
        p = mix(p, (Bone * vec4(p, 1.0)).xyz, w);
        surface = normalize(mix(surface, mat3(Bone) * surface, w));
    }
    float weight = Color.a * Sway;
    if (weight > 0.0) {
        float along = length(p.xz) * 2.2 - p.y * 1.4;
        p += surface * (sin(Time * 1.5 - along * 2.0 + p.x * 0.8) + 0.45 * sin(Time * 2.6 - along * 4.3 + p.z * 1.7)) * weight;
        p += vec3(0.7, 0.3, 0.25) * sin(Time * 0.9 + p.x * 0.7) * weight * 0.7;
    }
    vec4 view = ModelViewMat * vec4(p, 1.0);
    vec3 normal = normalize(mat3(ModelViewMat) * surface);
    if (Outline > 0.0) {
        float amount = Outline;
        if (ViewOutline > 0.5) {
            // Кайма плаща: раздувается только сторона, отвёрнутая от камеры.
            amount = Outline * (ViewOutline > 1.5 ? 1.0 : Color.r) * smoothstep(0.12, -0.02, dot(normal, normalize(-view.xyz)));
            if (ViewOutline > 1.5) {
                amount *= clamp(length(view.xyz) / 20.0, 0.6, 2.5);
            }
        }
        p += surface * amount;
        view = ModelViewMat * vec4(p, 1.0);
    }
    gl_Position = ProjMat * view;
    vNormal = normal;
    vView = -view.xyz;
    vColor = Color.rgb;
    vUv = UV0;
    vLocal = Position;
}
