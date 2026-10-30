package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Маска Пустого — вылепленная 3D-оболочка, а не кубик: она облегает голову, у неё есть надбровья, глубокие
 * глазницы с горящими глазами, скулы, переносица и оскал с настоящими зубами, толщина кости по краю и два
 * длинных рога, которые уходят от висков в стороны и загибаются вперёд. Трещины рисует текстура; на поздних
 * стадиях рога обломаны, с рваным концом.
 *
 * <p>Всё строится в координатах головы (пиксели модели: лицо смотрит в −z, верх — −y, левая сторона маски — +x).
 * Оболочка — скруглённый куб (суперэллипсоид), разложенный по углам θ (вокруг головы) и φ (вверх-вниз); те же
 * углы — координаты текстуры (см. tools/textures.cs, HollowMaskHD).
 */
public final class HollowMaskRenderer {
    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final Identifier EYES = ZangetsuMod.id("textures/entity/hollow_mask_eyes.png");
    /** Углы, которые покрывает текстура (как в tools/textures.cs), и углы, которые покрывает сама оболочка. */
    private static final float TEX_T0 = -100.0F, TEX_T1 = 100.0F, TEX_P0 = 80.0F, TEX_P1 = -85.0F;
    private static final float T0 = -90.0F, T1 = 90.0F, P0 = 78.0F, P1 = -62.0F;
    /** Степень суперэллипсоида: 6 — скруглённый куб, углы головы не протыкают маску. */
    private static final float SQUARENESS = 6.0F;
    private static final float EYE_T = 22.0F, EYE_P = 2.0F, EYE_DT = 12.5F, EYE_DP = 7.5F;
    private static final int NT = 50, NP = 44;
    private static final Vector3f CENTER = new Vector3f(0.0F, -4.2F, 0.2F);
    private static final float RX = 5.05F, RY = 4.6F, RZ = 5.05F;
    private static final float THICKNESS = 0.45F;
    /** Готовая поверхность (пиксели) и нормали к ней; форма одна на всех — считаем один раз. */
    private static final Vector3f[][] SURFACE = new Vector3f[NT + 1][NP + 1];
    private static final Vector3f[][] NORMALS = new Vector3f[NT + 1][NP + 1];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ZangetsuMod.id("textures/entity/hollow_mask_" + i + ".png");
        }
        build();
    }

    private HollowMaskRenderer() {
    }

    /**
     * Нарисовать маску в координатах головы (матрица уже повёрнута как голова модели). {@code grow} — 0..1, маска
     * нарастает при превращении; {@code glow} — яркость глаз 0..255.
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int crack, float grow, int glow) {
        matrices.push();
        matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        float scale = MathHelper.lerp(grow, 0.82F, 1.0F);
        matrices.translate(CENTER.x, CENTER.y, CENTER.z);
        matrices.scale(scale, scale, scale);
        matrices.translate(-CENTER.x, -CENTER.y, -CENTER.z);
        int color = ((int) (MathHelper.clamp(grow, 0.0F, 1.0F) * 255.0F) << 24) | 0xFFFFFF;
        Emitter bone = new Emitter(matrices.peek(), consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURES[MathHelper.clamp(crack, 0, 3)])),
                light, color);
        face(bone);
        rims(bone);
        teeth(bone);
        horn(bone, 1, crack >= 3 ? 0.25F : (crack >= 2 ? 0.62F : 1.0F));
        horn(bone, -1, crack >= 3 ? 0.7F : 1.0F);
        if (glow > 0) {
            Emitter eyes = new Emitter(matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(EYES)), LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    (255 << 24) | (glow << 16) | (glow << 8) | glow);
            eyeCells(eyes);
        }
        matrices.pop();
    }

    // ---------------------------------------------------------------- форма

    private static float theta(int i) {
        return MathHelper.lerp((float) i / NT, T0, T1);
    }

    private static float phi(int j) {
        return MathHelper.lerp((float) j / NP, P0, P1);
    }

    /**
     * Точка скруглённого куба вокруг головы в направлении (θ, φ) от центра: луч из центра до поверхности. Так углы
     * текстуры совпадают с направлением взгляда, и глаза на маске не растягиваются.
     */
    private static Vector3f shell(float thetaDeg, float phiDeg) {
        float t = thetaDeg * MathHelper.RADIANS_PER_DEGREE, p = phiDeg * MathHelper.RADIANS_PER_DEGREE;
        Vector3f d = new Vector3f(MathHelper.sin(t) * MathHelper.cos(p), -MathHelper.sin(p), -MathHelper.cos(t) * MathHelper.cos(p));
        double sum = Math.pow(Math.abs(d.x / RX), SQUARENESS) + Math.pow(Math.abs(d.y / RY), SQUARENESS) + Math.pow(Math.abs(d.z / RZ), SQUARENESS);
        float r = (float) Math.pow(sum, -1.0 / SQUARENESS);
        return d.mul(r).add(CENTER);
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
    }

    private static float band(float x, float from, float to, float soft) {
        return MathHelper.clamp((x - from) / soft, 0.0F, 1.0F) * MathHelper.clamp((to - x) / soft, 0.0F, 1.0F);
    }

    /** Глазница: 0 — центр, 1 — край (наружный уголок опущен), как в текстуре. */
    static float eye(float theta, float phi) {
        float best = 99.0F;
        for (int side = -1; side <= 1; side += 2) {
            float dt = theta - side * EYE_T;
            float dp = phi - EYE_P + side * dt * 0.32F;
            best = Math.min(best, (float) Math.sqrt((dt / EYE_DT) * (dt / EYE_DT) + (dp / EYE_DP) * (dp / EYE_DP)));
        }
        return best;
    }

    static float mouth(float theta) {
        return -27.0F + 0.0035F * theta * theta;
    }

    /** Рельеф маски (пиксели вдоль нормали): надбровья, глазницы, скулы, переносица, оскал, подбородок. */
    private static float relief(float theta, float phi) {
        float at = Math.abs(theta);
        // Злые надбровья: галочкой, ниже к переносице.
        float d = 0.7F * gauss((phi - (9.0F + 0.2F * at)) / 5.0F) * band(at, -1.0F, 46.0F, 10.0F);
        float e = eye(theta, phi);
        if (e < 1.0F) {
            d -= 0.75F * (1.0F - e * e);
        } else {
            d += 0.16F * gauss((e - 1.12F) / 0.12F);
        }
        d += 0.38F * gauss((phi + 10.0F) / 6.0F) * gauss((at - 34.0F) / 10.0F);
        d += 0.45F * gauss(theta / 4.5F) * band(phi, -19.0F, -3.0F, 3.0F);
        float m = phi - mouth(theta);
        if (at < 38.0F) {
            d -= 0.4F * band(m, -3.2F, 3.2F, 1.2F) * band(at, -1.0F, 38.0F, 5.0F);
        }
        d += 0.28F * gauss((phi + 46.0F) / 8.0F) * band(at, -1.0F, 42.0F, 10.0F);
        d += 0.14F * gauss(theta / 6.0F) * band(phi, 26.0F, 72.0F, 8.0F);
        d += 0.42F * gauss((at - 62.0F) / 7.0F) * gauss((phi - 42.0F) / 7.0F);
        return d;
    }

    private static void build() {
        Vector3f[][] base = new Vector3f[NT + 1][NP + 1];
        for (int i = 0; i <= NT; i++) {
            for (int j = 0; j <= NP; j++) {
                base[i][j] = shell(theta(i), phi(j));
            }
        }
        for (int i = 0; i <= NT; i++) {
            for (int j = 0; j <= NP; j++) {
                Vector3f n = normal(base, i, j);
                SURFACE[i][j] = new Vector3f(base[i][j]).add(new Vector3f(n).mul(relief(theta(i), phi(j))));
            }
        }
        for (int i = 0; i <= NT; i++) {
            for (int j = 0; j <= NP; j++) {
                NORMALS[i][j] = normal(SURFACE, i, j);
            }
        }
    }

    /** Нормаль сетки наружу (от центра головы). */
    private static Vector3f normal(Vector3f[][] grid, int i, int j) {
        Vector3f dt = new Vector3f(grid[Math.min(NT, i + 1)][j]).sub(grid[Math.max(0, i - 1)][j]);
        Vector3f dp = new Vector3f(grid[i][Math.min(NP, j + 1)]).sub(grid[i][Math.max(0, j - 1)]);
        Vector3f n = dt.cross(dp);
        Vector3f out = new Vector3f(grid[i][j]).sub(CENTER);
        if (n.dot(out) < 0.0F) {
            n.negate();
        }
        return n.lengthSquared() < 1.0E-10F ? out.normalize() : n.normalize();
    }

    // ---------------------------------------------------------------- части

    private static float faceU(int i) {
        return (theta(i) - TEX_T0) / (TEX_T1 - TEX_T0);
    }

    private static float faceV(int j) {
        return (TEX_P0 - phi(j)) / (TEX_P0 - TEX_P1) * (160.0F / 256.0F);
    }

    private static void face(Emitter e) {
        for (int i = 0; i < NT; i++) {
            for (int j = 0; j < NP; j++) {
                cell(e, i, j);
            }
        }
    }

    private static void cell(Emitter e, int i, int j) {
        e.vertex(SURFACE[i][j], faceU(i), faceV(j), NORMALS[i][j]);
        e.vertex(SURFACE[i][j + 1], faceU(i), faceV(j + 1), NORMALS[i][j + 1]);
        e.vertex(SURFACE[i + 1][j + 1], faceU(i + 1), faceV(j + 1), NORMALS[i + 1][j + 1]);
        e.vertex(SURFACE[i + 1][j], faceU(i + 1), faceV(j), NORMALS[i + 1][j]);
    }

    /** Свечение глаз — только клетки вокруг глазниц (остальная текстура глаз прозрачна). */
    private static void eyeCells(Emitter e) {
        for (int i = 0; i < NT; i++) {
            for (int j = 0; j < NP; j++) {
                if (eye((theta(i) + theta(i + 1)) / 2.0F, (phi(j) + phi(j + 1)) / 2.0F) < 1.6F) {
                    cell(e, i, j);
                }
            }
        }
    }

    /** Толщина кости по краю оболочки: полоса от лицевой поверхности внутрь. */
    private static void rims(Emitter e) {
        float u0 = 64.0F / 256.0F, u1 = 128.0F / 256.0F, v0 = 224.0F / 256.0F, v1 = 1.0F;
        for (int i = 0; i < NT; i++) {
            rim(e, SURFACE[i][0], SURFACE[i + 1][0], NORMALS[i][0], NORMALS[i + 1][0], u0, v0, u1, v1);
            rim(e, SURFACE[i + 1][NP], SURFACE[i][NP], NORMALS[i + 1][NP], NORMALS[i][NP], u0, v0, u1, v1);
        }
        for (int j = 0; j < NP; j++) {
            rim(e, SURFACE[0][j + 1], SURFACE[0][j], NORMALS[0][j + 1], NORMALS[0][j], u0, v0, u1, v1);
            rim(e, SURFACE[NT][j], SURFACE[NT][j + 1], NORMALS[NT][j], NORMALS[NT][j + 1], u0, v0, u1, v1);
        }
    }

    private static void rim(Emitter e, Vector3f a, Vector3f b, Vector3f na, Vector3f nb, float u0, float v0, float u1, float v1) {
        Vector3f ia = new Vector3f(a).sub(new Vector3f(na).mul(THICKNESS)), ib = new Vector3f(b).sub(new Vector3f(nb).mul(THICKNESS));
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(na)).normalize();
        e.vertex(a, u0, v0, n);
        e.vertex(b, u1, v0, n);
        e.vertex(ib, u1, v1, n);
        e.vertex(ia, u0, v1, n);
    }

    /** Точка на маске по углам (с рельефом) и нормаль в ней — для зубов. */
    private static Vector3f[] onSurface(float theta, float phi) {
        float fi = (theta - T0) / (T1 - T0) * NT, fj = (P0 - phi) / (P0 - P1) * NP;
        int i = MathHelper.clamp((int) fi, 0, NT - 1), j = MathHelper.clamp((int) fj, 0, NP - 1);
        float a = fi - i, b = fj - j;
        Vector3f p = lerp2(SURFACE, i, j, a, b), n = lerp2(NORMALS, i, j, a, b).normalize();
        return new Vector3f[]{p, n};
    }

    private static Vector3f lerp2(Vector3f[][] g, int i, int j, float a, float b) {
        Vector3f top = new Vector3f(g[i][j]).lerp(g[i + 1][j], a), bottom = new Vector3f(g[i][j + 1]).lerp(g[i + 1][j + 1], a);
        return top.lerp(bottom, b);
    }

    /** Оскал: верхний ряд зубов свисает с губы вниз, нижний торчит вверх, клыки длиннее. */
    private static void teeth(Emitter e) {
        int count = 11;
        for (int k = 0; k < count; k++) {
            float theta = MathHelper.lerp((float) k / (count - 1), -33.0F, 33.0F);
            boolean fang = k == 2 || k == count - 3;
            float length = fang ? 8.5F : 5.5F;
            tooth(e, theta, mouth(theta) + 3.0F, -1, length);
            tooth(e, theta + 3.3F, mouth(theta + 3.3F) - 3.0F, 1, length * 0.8F);
        }
    }

    /** Зуб-клин: основание у губы (ширина ≈ 5° по θ), кончик — на {@code length} градусов вверх или вниз. */
    private static void tooth(Emitter e, float theta, float phi, int dir, float length) {
        if (Math.abs(theta) > 36.0F) {
            return;
        }
        float half = 2.4F;
        Vector3f[] l = onSurface(theta - half, phi), r = onSurface(theta + half, phi), tip = onSurface(theta, phi + dir * length);
        Vector3f n = new Vector3f(l[1]).add(r[1]).add(tip[1]).normalize();
        Vector3f lf = new Vector3f(l[0]).add(new Vector3f(n).mul(0.28F)), rf = new Vector3f(r[0]).add(new Vector3f(n).mul(0.28F));
        Vector3f tf = new Vector3f(tip[0]).add(new Vector3f(n).mul(0.12F));
        float u0 = (theta + 36.0F) / 72.0F * (64.0F / 256.0F), u1 = u0 + 14.0F / 256.0F, vRoot = 224.0F / 256.0F, vTip = 1.0F;
        // Передняя грань (треугольник) и две боковые.
        Vector3f front = new Vector3f(rf).sub(lf).cross(new Vector3f(tf).sub(lf));
        if (front.dot(n) < 0.0F) {
            front.negate();
        }
        front.normalize();
        e.vertex(lf, u0, vRoot, front);
        e.vertex(rf, u1, vRoot, front);
        e.vertex(tf, (u0 + u1) / 2.0F, vTip, front);
        e.vertex(tf, (u0 + u1) / 2.0F, vTip, front);
        side(e, l[0], lf, tf, tip[0], u0, vRoot, vTip);
        side(e, rf, r[0], tip[0], tf, u1, vRoot, vTip);
    }

    private static void side(Emitter e, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float u, float vRoot, float vTip) {
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a)).normalize();
        e.vertex(a, u, vRoot, n);
        e.vertex(b, u + 0.01F, vRoot, n);
        e.vertex(c, u + 0.01F, vTip, n);
        e.vertex(d, u, vTip, n);
    }

    /**
     * Рог: растёт из виска (θ ≈ ±62°, φ ≈ 42°), уходит в сторону и вверх и загибается вперёд, сужаясь к острию.
     * {@code keep} &lt; 1 — обломан: короче и с рваным торцом.
     */
    private static void horn(Emitter e, int side, float keep) {
        int segments = 20, around = 8;
        Vector3f[] root = onSurface(side * 62.0F, 42.0F);
        Vector3f base = new Vector3f(root[0]).sub(new Vector3f(root[1]).mul(0.3F));
        Vector3f[] rings = new Vector3f[(segments + 1) * around];
        Vector3f[] centers = new Vector3f[segments + 1];
        float[] radius = new float[segments + 1];
        for (int k = 0; k <= segments; k++) {
            float s = keep * k / segments;
            centers[k] = new Vector3f(base).add(side * (3.2F * s - 0.8F * s * s), -(1.6F * s + 3.0F * s * s), -(5.8F * s * s));
            radius[k] = 1.3F * (float) Math.pow(1.0F - s, 0.8F) + 0.06F;
        }
        // Рамка вдоль оси рога (перенос без закручивания).
        Vector3f up = new Vector3f(0.0F, -1.0F, 0.0F);
        Vector3f prevN = null;
        for (int k = 0; k <= segments; k++) {
            Vector3f t = new Vector3f(centers[Math.min(segments, k + 1)]).sub(centers[Math.max(0, k - 1)]).normalize();
            Vector3f n = prevN == null ? new Vector3f(up).cross(t).normalize() : new Vector3f(prevN).sub(new Vector3f(t).mul(prevN.dot(t))).normalize();
            if (n.lengthSquared() < 1.0E-6F) {
                n.set(1.0F, 0.0F, 0.0F);
            }
            prevN = n;
            Vector3f b = new Vector3f(t).cross(n).normalize();
            boolean broken = keep < 1.0F && k == segments;
            for (int a = 0; a < around; a++) {
                float ang = MathHelper.TAU * a / around;
                float r = radius[k] * (broken ? 0.55F + 0.45F * Math.abs(MathHelper.sin(a * 2.3F + side)) : 1.0F);
                Vector3f offset = new Vector3f(n).mul(MathHelper.cos(ang) * r).add(new Vector3f(b).mul(MathHelper.sin(ang) * r));
                Vector3f p = new Vector3f(centers[k]).add(offset);
                if (broken) {
                    p.add(new Vector3f(t).mul(0.35F * MathHelper.sin(a * 1.7F + 0.5F)));
                }
                rings[k * around + a] = p;
            }
        }
        float vOff = 160.0F / 256.0F, vSpan = 64.0F / 256.0F;
        for (int k = 0; k < segments; k++) {
            for (int a = 0; a < around; a++) {
                int a1 = (a + 1) % around;
                Vector3f p00 = rings[k * around + a], p01 = rings[k * around + a1], p10 = rings[(k + 1) * around + a], p11 = rings[(k + 1) * around + a1];
                Vector3f n = new Vector3f(p00).add(p01).add(p10).add(p11).mul(0.25F).sub(new Vector3f(centers[k]).lerp(centers[k + 1], 0.5F)).normalize();
                float u0 = keep * k / segments, u1 = keep * (k + 1) / segments;
                float v0 = vOff + vSpan * a / around, v1 = vOff + vSpan * (a + 1) / around;
                e.vertex(p00, u0, v0, n);
                e.vertex(p10, u1, v0, n);
                e.vertex(p11, u1, v1, n);
                e.vertex(p01, u0, v1, n);
            }
        }
        // Торец: у целого рога — точка, у обломанного — рваный скол (тёмная сердцевина кости).
        Vector3f tipCenter = centers[segments];
        Vector3f axis = new Vector3f(centers[segments]).sub(centers[segments - 1]).normalize();
        float tu = keep < 1.0F ? 128.0F / 256.0F : 0.99F, tv = keep < 1.0F ? 230.0F / 256.0F : vOff + 0.5F * vSpan;
        for (int a = 0; a < around; a++) {
            Vector3f p0 = rings[segments * around + a], p1 = rings[segments * around + (a + 1) % around];
            e.vertex(p0, tu, tv, axis);
            e.vertex(p1, tu, tv, axis);
            e.vertex(tipCenter, tu, tv, axis);
            e.vertex(tipCenter, tu, tv, axis);
        }
    }

    // ---------------------------------------------------------------- вывод вершин

    private record Emitter(MatrixStack.Entry entry, VertexConsumer vertices, int light, int color) {
        void vertex(Vector3f p, float u, float v, Vector3f n) {
            this.vertices.vertex(this.entry.getPositionMatrix(), p.x, p.y, p.z).color(this.color).texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV).light(this.light).normal(this.entry, n.x, n.y, n.z);
        }
    }
}
