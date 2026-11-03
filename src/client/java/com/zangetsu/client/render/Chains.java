package com.zangetsu.client.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Цепи и кандалы финальной формы. Каждое звено — настоящее кольцо-овал из проволоки, соседние звенья повёрнуты на четверть оборота и входят
 * друг в друга, как у настоящей цепи. Размеры — в пикселях модели, рисуется в тех координатах, что заданы матрицей.
 */
final class Chains {
    private static final int RING = 8, WIRE = 4;
    private static final float LINK_LENGTH = 1.7F, LINK_WIDTH = 1.08F, WIRE_RADIUS = 0.24F;
    /** Шаг звеньев: каждое следующее входит в предыдущее. */
    private static final float STEP = LINK_LENGTH - WIRE_RADIUS * 2.0F - 0.12F;
    private static final int IRON = 0x4A4A52, DARK_IRON = 0x2C2C32;

    private Chains() {
    }

    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, Vector3f p, Vector3f n, int rgb, int light) {
        out.vertex(entry.getPositionMatrix(), p.x / 16.0F, p.y / 16.0F, p.z / 16.0F).color(0xFF000000 | rgb).texture(0.5F, 0.5F)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, n.x, n.y, n.z);
    }

    /** Одно звено: центр, ось вдоль цепи и поперечная ось его плоскости (пиксели). */
    private static void link(VertexConsumer out, MatrixStack.Entry entry, Vector3f center, Vector3f axis, Vector3f across, int rgb, int light) {
        Vector3f flat = new Vector3f(axis).cross(across).normalize();
        Vector3f[][] p = new Vector3f[RING][WIRE];
        Vector3f[][] n = new Vector3f[RING][WIRE];
        for (int i = 0; i < RING; i++) {
            float a = MathHelper.TAU * i / RING;
            float ca = MathHelper.cos(a), sa = MathHelper.sin(a);
            Vector3f mid = new Vector3f(center).add(new Vector3f(axis).mul(sa * LINK_LENGTH * 0.5F)).add(new Vector3f(across).mul(ca * LINK_WIDTH * 0.5F));
            Vector3f outward = new Vector3f(axis).mul(sa).add(new Vector3f(across).mul(ca)).normalize();
            for (int j = 0; j < WIRE; j++) {
                float b = MathHelper.TAU * j / WIRE;
                n[i][j] = new Vector3f(outward).mul(MathHelper.cos(b)).add(new Vector3f(flat).mul(MathHelper.sin(b)));
                p[i][j] = new Vector3f(mid).add(new Vector3f(n[i][j]).mul(WIRE_RADIUS));
            }
        }
        for (int i = 0; i < RING; i++) {
            int i1 = (i + 1) % RING;
            for (int j = 0; j < WIRE; j++) {
                int j1 = (j + 1) % WIRE;
                vertex(out, entry, p[i][j], n[i][j], rgb, light);
                vertex(out, entry, p[i1][j], n[i1][j], rgb, light);
                vertex(out, entry, p[i1][j1], n[i1][j1], rgb, light);
                vertex(out, entry, p[i][j1], n[i][j1], rgb, light);
            }
        }
    }

    /** Натянутая цепь по ломаной: {@code bulge} — куда смотрит плоскость первого звена. */
    static void along(VertexConsumer out, MatrixStack.Entry entry, Vector3f[] points, Vector3f bulge, int light) {
        boolean turned = false;
        for (int i = 0; i + 1 < points.length; i++) {
            Vector3f from = points[i], to = points[i + 1];
            float length = from.distance(to);
            Vector3f axis = new Vector3f(to).sub(from).normalize();
            Vector3f across = new Vector3f(bulge).sub(new Vector3f(axis).mul(bulge.dot(axis))).normalize();
            Vector3f flat = new Vector3f(axis).cross(across).normalize();
            int count = Math.max(1, (int) (length / STEP));
            for (int k = 0; k < count; k++) {
                Vector3f center = new Vector3f(from).add(new Vector3f(axis).mul((k + 0.5F) * length / count));
                link(out, entry, center, axis, turned ? flat : across, turned ? DARK_IRON : IRON, light);
                turned = !turned;
            }
        }
    }

    /**
     * Свободно висящая цепь от точки (пиксели) по силе тяжести, с качанием и ветром. {@code down} и {@code wind} — в координатах матрицы,
     * ветер тем сильнее, чем дальше звено от крепления.
     */
    static void hanging(VertexConsumer out, MatrixStack.Entry entry, Vector3f start, Vector3f down, Vector3f wind, int count, float time, float seed, int light) {
        Vector3f side = new Vector3f(down).cross(Math.abs(down.x) < 0.8F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
        Vector3f other = new Vector3f(down).cross(side).normalize();
        Vector3f at = new Vector3f(start);
        boolean turned = false;
        for (int i = 0; i < count; i++) {
            float k = (i + 1.0F) / count;
            float swing = MathHelper.sin(time * 0.13F + seed - k * 1.6F) * 0.28F * k;
            float swing2 = MathHelper.cos(time * 0.09F + seed * 2.0F - k) * 0.16F * k;
            Vector3f axis = new Vector3f(down).add(new Vector3f(side).mul(swing)).add(new Vector3f(other).mul(swing2)).add(new Vector3f(wind).mul(k * 1.4F)).normalize();
            Vector3f across = new Vector3f(side).sub(new Vector3f(axis).mul(side.dot(axis))).normalize();
            Vector3f flat = new Vector3f(axis).cross(across).normalize();
            Vector3f center = new Vector3f(at).add(new Vector3f(axis).mul(STEP * 0.5F));
            link(out, entry, center, axis, turned ? flat : across, turned ? DARK_IRON : IRON, light);
            at.add(new Vector3f(axis).mul(STEP));
            turned = !turned;
        }
    }

    /** Кандалы на запястье или щиколотке: толстый железный обруч вокруг конечности с заклёпками. */
    static void cuff(VertexConsumer out, MatrixStack.Entry entry, float cx, float y, float half, float tall, int light) {
        float t = 0.55F;
        float x0 = cx - half, x1 = cx + half, y0 = y - tall, y1 = y + tall, z0 = -half, z1 = half;
        box(out, entry, x0 - t, y0, z0 - t, x1 + t, y1, z0, IRON, light);
        box(out, entry, x0 - t, y0, z1, x1 + t, y1, z1 + t, IRON, light);
        box(out, entry, x0 - t, y0, z0, x0, y1, z1, IRON, light);
        box(out, entry, x1, y0, z0, x1 + t, y1, z1, IRON, light);
        // Заклёпки и выпирающая скоба — чтобы обруч не был гладкой коробкой.
        float r = 0.32F;
        for (int i = -1; i <= 1; i += 2) {
            box(out, entry, cx + i * half * 0.6F - r, y - r, z0 - t - r, cx + i * half * 0.6F + r, y + r, z0 - t, DARK_IRON, light);
            box(out, entry, cx + i * half * 0.6F - r, y - r, z1 + t, cx + i * half * 0.6F + r, y + r, z1 + t + r, DARK_IRON, light);
        }
        box(out, entry, x0 - t - 0.5F, y - 0.6F, -0.6F, x0 - t, y + 0.6F, 0.6F, DARK_IRON, light);
    }

    /** Коробка из шести граней (пиксели). */
    private static void box(VertexConsumer out, MatrixStack.Entry entry, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        face(out, entry, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0.0F, 0.0F, -1.0F, rgb, light);
        face(out, entry, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0.0F, 0.0F, 1.0F, rgb, light);
        face(out, entry, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1.0F, 0.0F, 0.0F, rgb, light);
        face(out, entry, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1.0F, 0.0F, 0.0F, rgb, light);
        face(out, entry, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0.0F, -1.0F, 0.0F, rgb, light);
        face(out, entry, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0.0F, 1.0F, 0.0F, rgb, light);
    }

    private static void face(VertexConsumer out, MatrixStack.Entry entry, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz, float nx, float ny, float nz, int rgb, int light) {
        Vector3f n = new Vector3f(nx, ny, nz);
        vertex(out, entry, new Vector3f(ax, ay, az), n, rgb, light);
        vertex(out, entry, new Vector3f(bx, by, bz), n, rgb, light);
        vertex(out, entry, new Vector3f(cx, cy, cz), n, rgb, light);
        vertex(out, entry, new Vector3f(dx, dy, dz), n, rgb, light);
    }
}
