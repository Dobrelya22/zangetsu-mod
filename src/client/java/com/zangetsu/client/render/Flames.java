package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Объёмный огонь. Каждый язык — настоящая труба из колец: к вершине сужается, изгибается и дрожит, всегда тянется вверх по миру (куда бы
 * ни была повёрнута рука) и сносится встречным ветром. Яркость вершины зависит от того, как поверхность смотрит на камеру: край языка гаснет,
 * и поэтому он выглядит мягким сгустком света, а не многогранником. Снаружи алый слой, поверх него раскалённая сердцевина светом.
 */
final class Flames {
    static final Identifier WHITE = ZangetsuMod.id("textures/misc/white.png");
    private static final int RINGS = 6, SIDES = 6;
    private static final int FULL_LIGHT = 0xF000F0;

    /** Внешний слой пламени: от основания к вершине. Чёрно-алый огонь финальной формы. */
    static final float[][] OUTER = {{1.0F, 0.22F, 0.05F}, {0.92F, 0.05F, 0.03F}, {0.5F, 0.0F, 0.02F}};
    /** Внутренний, раскалённый слой. */
    static final float[][] INNER = {{1.0F, 0.72F, 0.34F}, {1.0F, 0.26F, 0.05F}, {0.62F, 0.03F, 0.02F}};

    /** Где сейчас рисуем: матрица, куда в этих координатах «вверх» и куда сносит ветром, и время. */
    static final class Frame {
        final MatrixStack.Entry entry;
        final Matrix4f matrix;
        final Vector3f up, wind, a, b;
        final float time;

        /** {@code spaceUp} и {@code spaceWind} — вверх по миру и ветер в пространстве, где стоит матрица (мир без поворота камеры либо вид от первого лица). */
        Frame(MatrixStack.Entry entry, Vector3f spaceUp, Vector3f spaceWind, float time) {
            this.entry = entry;
            this.matrix = entry.getPositionMatrix();
            Matrix4f inverse = new Matrix4f(this.matrix).invert();
            this.up = inverse.transformDirection(new Vector3f(spaceUp)).normalize();
            this.wind = inverse.transformDirection(new Vector3f(spaceWind));
            this.a = new Vector3f(this.up).cross(Math.abs(this.up.x) < 0.8F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
            this.b = new Vector3f(this.up).cross(this.a).normalize();
            this.time = time;
        }
    }

    private Flames() {
    }

    /** Псевдошум от 0 до 1: плавный, без текстур. */
    private static float noise(float x) {
        return 0.5F + 0.25F * MathHelper.sin(x) + 0.15F * MathHelper.sin(x * 2.3F + 1.7F) + 0.1F * MathHelper.sin(x * 4.1F + 4.1F);
    }

    static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    /**
     * Внешний слой языка: непрозрачный алый огонь, он не выцветает на светлом снегу. Рисуется в слой «светящийся полупрозрачный»,
     * где ничего не отбрасывается и не пишется глубина.
     */
    static void flame(VertexConsumer out, Frame f, float x, float y, float z, float length, float radius, float seed, float power) {
        float[][][] rings = rings(f, x, y, z, length, radius, seed, OUTER, power);
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SIDES; j++) {
                int k = (j + 1) % SIDES;
                blended(out, rings[i][j]);
                blended(out, rings[i][k]);
                blended(out, rings[i + 1][k]);
                blended(out, rings[i + 1][j]);
            }
        }
    }

    private static void blended(VertexConsumer out, float[] p) {
        int alpha = Math.min(255, (int) (p[9] * 300.0F));
        int color = (alpha << 24) | (Math.min(255, (int) (p[3] * 255.0F)) << 16) | (Math.min(255, (int) (p[4] * 255.0F)) << 8) | Math.min(255, (int) (p[5] * 255.0F));
        // Нормаль вверх: этот слой всё равно затеняется по ней, а огню тень не нужна.
        out.vertex(p[0], p[1], p[2], color, 0.5F, 0.5F, OverlayTexture.DEFAULT_UV, FULL_LIGHT, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Раскалённый язык светом от точки (x, y, z) (в блоках матрицы). {@code seed} делает каждый язык своим, {@code power} гасит весь язык.
     * В слое света отбрасываются обратные грани, а для глубины нужны обе стороны — выводим обе.
     */
    static void tongue(VertexConsumer out, Frame f, float x, float y, float z, float length, float radius, float seed, float[][] palette, float power) {
        float[][][] rings = rings(f, x, y, z, length, radius, seed, palette, power);
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SIDES; j++) {
                int k = (j + 1) % SIDES;
                float[] p0 = rings[i][j], p1 = rings[i][k], p2 = rings[i + 1][k], p3 = rings[i + 1][j];
                emit(out, p0);
                emit(out, p1);
                emit(out, p2);
                emit(out, p3);
                emit(out, p3);
                emit(out, p2);
                emit(out, p1);
                emit(out, p0);
            }
        }
    }

    /** Кольца языка в координатах вида: {x, y, z, цвет×яркость (3), нормаль (3), яркость}. */
    private static float[][][] rings(Frame f, float x, float y, float z, float length, float radius, float seed, float[][] palette, float power) {
        float t = f.time;
        float height = length * (0.58F + 0.42F * noise(t * 0.21F + seed * 11.0F));
        float[][][] rings = new float[RINGS + 1][SIDES][];
        for (int i = 0; i <= RINGS; i++) {
            float s = (float) i / RINGS;
            float swayA = 0.24F * MathHelper.sin(t * 0.37F + seed * 7.0F + s * 3.4F) + 0.1F * MathHelper.sin(t * 0.71F + seed * 3.0F - s * 6.1F);
            float swayB = 0.24F * MathHelper.cos(t * 0.31F + seed * 5.0F + s * 2.8F) + 0.1F * MathHelper.cos(t * 0.63F + seed * 9.0F - s * 5.3F);
            float along = height * s;
            float cx = x + f.up.x * along + (f.a.x * swayA + f.b.x * swayB + f.wind.x * s) * along;
            float cy = y + f.up.y * along + (f.a.y * swayA + f.b.y * swayB + f.wind.y * s) * along;
            float cz = z + f.up.z * along + (f.a.z * swayA + f.b.z * swayB + f.wind.z * s) * along;
            // Широкое основание, язык сужается к острию; по нему бежит перетяжка.
            float r = radius * (float) Math.pow(1.0F - s, 0.8F) * Math.min(1.0F, 0.6F + s * 3.5F) * (1.0F + 0.16F * MathHelper.sin(s * 9.0F - t * 0.8F + seed * 9.0F));
            float k = s * 2.0F;
            int stop = Math.min(1, (int) k);
            float m = k - stop;
            float[] from = palette[stop], to = palette[stop + 1];
            float red = MathHelper.lerp(m, from[0], to[0]), green = MathHelper.lerp(m, from[1], to[1]), blue = MathHelper.lerp(m, from[2], to[2]);
            float fade = power * Math.min(1.0F, 0.3F + s * 4.0F) * (1.0F - s * s * s);
            for (int j = 0; j < SIDES; j++) {
                float angle = MathHelper.TAU * j / SIDES + seed;
                float ca = MathHelper.cos(angle), sa = MathHelper.sin(angle);
                float nx = f.a.x * ca + f.b.x * sa, ny = f.a.y * ca + f.b.y * sa, nz = f.a.z * ca + f.b.z * sa;
                Vector3f view = f.matrix.transformPosition(cx + nx * r, cy + ny * r, cz + nz * r, new Vector3f());
                Vector3f normal = f.matrix.transformDirection(nx, ny, nz, new Vector3f()).normalize();
                float facing = Math.abs(new Vector3f(view).normalize().dot(normal));
                float glow = fade * (0.12F + 0.88F * facing * facing);
                rings[i][j] = new float[]{view.x, view.y, view.z, red, green, blue, normal.x, normal.y, normal.z, glow};
            }
        }
        return rings;
    }

    private static void emit(VertexConsumer out, float[] p) {
        int color = 0xFF000000 | (Math.min(255, (int) (p[3] * p[9] * 255.0F)) << 16) | (Math.min(255, (int) (p[4] * p[9] * 255.0F)) << 8)
                | Math.min(255, (int) (p[5] * p[9] * 255.0F));
        out.vertex(p[0], p[1], p[2], color, 0.5F, 0.5F, OverlayTexture.DEFAULT_UV, FULL_LIGHT, p[6], p[7], p[8]);
    }

    /** Тёмное сердце пламени: непрозрачная чернота внутри языка. Рисуется в полупрозрачный слой до света. */
    static void heart(VertexConsumer out, Frame f, float x, float y, float z, float length, float radius, float seed) {
        float t = f.time;
        float height = length * (0.58F + 0.42F * noise(t * 0.21F + seed * 11.0F));
        Vector3f previous = null;
        float previousRadius = 0.0F;
        for (int i = 0; i <= 3; i++) {
            float s = i / 3.0F;
            float along = height * s;
            float swayA = 0.24F * MathHelper.sin(t * 0.37F + seed * 7.0F + s * 3.4F), swayB = 0.24F * MathHelper.cos(t * 0.31F + seed * 5.0F + s * 2.8F);
            Vector3f c = new Vector3f(x, y, z).add(new Vector3f(f.up).mul(along)).add(new Vector3f(f.a).mul(swayA * along))
                    .add(new Vector3f(f.b).mul(swayB * along)).add(new Vector3f(f.wind).mul(s * along));
            float r = radius * (1.0F - s) * Math.min(1.0F, 0.6F + s * 3.5F);
            if (previous != null) {
                for (int j = 0; j < 4; j++) {
                    float a0 = MathHelper.HALF_PI * j + seed, a1 = a0 + MathHelper.HALF_PI;
                    heartVertex(out, f, previous, a0, previousRadius);
                    heartVertex(out, f, previous, a1, previousRadius);
                    heartVertex(out, f, c, a1, r);
                    heartVertex(out, f, c, a0, r);
                }
            }
            previous = c;
            previousRadius = r;
        }
    }

    private static void heartVertex(VertexConsumer out, Frame f, Vector3f c, float angle, float r) {
        float ca = MathHelper.cos(angle), sa = MathHelper.sin(angle);
        float nx = f.a.x * ca + f.b.x * sa, ny = f.a.y * ca + f.b.y * sa, nz = f.a.z * ca + f.b.z * sa;
        Vector3f view = f.matrix.transformPosition(c.x + nx * r, c.y + ny * r, c.z + nz * r, new Vector3f());
        out.vertex(view.x, view.y, view.z, 0xE0060002, 0.5F, 0.5F, OverlayTexture.DEFAULT_UV, FULL_LIGHT, 0.0F, 1.0F, 0.0F);
    }

    /** Искра: крошечный раскалённый ромб. */
    static void spark(VertexConsumer out, Matrix4f matrix, float x, float y, float z, float size, int rgb) {
        int color = 0xFF000000 | rgb;
        float[][] points = {{0.0F, size, 0.0F}, {size, 0.0F, 0.0F}, {0.0F, 0.0F, size}, {-size, 0.0F, 0.0F}, {0.0F, 0.0F, -size}, {0.0F, -size, 0.0F}};
        int[][] faces = {{0, 1, 2}, {0, 2, 3}, {0, 3, 4}, {0, 4, 1}, {5, 2, 1}, {5, 3, 2}, {5, 4, 3}, {5, 1, 4}};
        for (int[] face : faces) {
            for (int k : new int[]{face[0], face[1], face[2], face[2]}) {
                Vector3f view = matrix.transformPosition(x + points[k][0], y + points[k][1], z + points[k][2], new Vector3f());
                out.vertex(view.x, view.y, view.z, color, 0.5F, 0.5F, OverlayTexture.DEFAULT_UV, FULL_LIGHT, 0.0F, 1.0F, 0.0F);
            }
        }
    }

    /** Искры, летящие вверх от точки: свои у каждого {@code seed}. */
    static void embers(VertexConsumer out, Frame f, float x, float y, float z, float spread, float rise, int count, float seed) {
        for (int i = 0; i < count; i++) {
            float life = f.time * (0.035F + 0.012F * hash(i, 3)) + i * 0.618F + seed;
            int generation = MathHelper.floor(life);
            float p = life - generation;
            float angle = MathHelper.TAU * hash(generation, i + 11);
            float out0 = spread * (0.6F + 0.8F * hash(generation, i + 23));
            float px = x + (f.a.x * MathHelper.cos(angle) + f.b.x * MathHelper.sin(angle)) * out0 + (f.up.x + f.wind.x * p) * rise * p;
            float py = y + (f.a.y * MathHelper.cos(angle) + f.b.y * MathHelper.sin(angle)) * out0 + (f.up.y + f.wind.y * p) * rise * p;
            float pz = z + (f.a.z * MathHelper.cos(angle) + f.b.z * MathHelper.sin(angle)) * out0 + (f.up.z + f.wind.z * p) * rise * p;
            int green = (int) (150.0F * (1.0F - p) * (1.0F - p));
            spark(out, f.matrix, px, py, pz, 0.018F * (1.0F - p * p), (255 << 16) | (green << 8) | (int) (30.0F * (1.0F - p)));
        }
    }
}
