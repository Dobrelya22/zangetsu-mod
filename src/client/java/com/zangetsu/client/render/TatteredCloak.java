package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Рваный плащ финальной формы: чёрный снаружи, алый изнутри, с настоящей толщиной и глубокими складками, поверх него — короткая накидка на плечах.
 * Это настоящая ткань ({@link FormPhysics.Cloth}): отстаёт от рывка, вьётся за бегущим, запахивается при развороте и ложится на землю,
 * когда игрок опускается. Внизу полотно расходится на рваные полосы, кончики которых тлеют. Здесь только форма и отрисовка.
 */
final class TatteredCloak {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/entity/cloak.png");
    private static final int COLS = 20;
    /** Первые две строки — кокетка от воротника к плечам, пришита к телу. */
    private static final int YOKE = 2;
    private static final float WRAP = 104.0F * MathHelper.RADIANS_PER_DEGREE;
    private static final float THICKNESS = 0.32F;
    /** Лёгкий наклон назад в покое: ткань не лежит на ногах. */
    private static final float REST_TILT = 7.0F * MathHelper.RADIANS_PER_DEGREE;
    private static final int OUTER_RGB = 0x34313B, INNER_RGB = 0x9A141C, HEM_RGB = 0x17161C, TRIM_RGB = 0x7A1016;

    /**
     * Слой ткани: сколько строк висит, длина, откуда висит, полуоси эллипса на кокетке, у плеч и внизу, с какой доли рвётся на полосы и какая у
     * полос длина. {@code trim} — насколько кончики уходят в алую кайму, {@code soft} — как крепко ткань помнит свою форму вверху и внизу.
     */
    private record Layer(String key, int rows, float length, float top, float yokeA, float yokeB, float shoulderA, float shoulderB, float bottomA, float bottomB,
            float shredFrom, float[] shreds, float trim, float softTop, float softBottom) {
        int total() {
            return YOKE + this.rows;
        }
    }

    private static final Layer CLOAK = new Layer("cloak", 20, 25.0F, 1.3F, 7.6F, 3.5F, 8.7F, 3.95F, 9.8F, 6.1F, 0.6F, shreds(0.8F, 91), 0.0F, 0.16F, 0.012F);
    private static final Layer MANTLE = new Layer("mantle", 8, 8.6F, 0.7F, 8.2F, 4.1F, 9.5F, 4.6F, 10.6F, 5.7F, 0.42F, shreds(0.7F, 47), 1.0F, 0.3F, 0.05F);

    private static float[] shreds(float shortest, int seed) {
        float[] lengths = new float[COLS];
        for (int c = 0; c < COLS; c++) {
            lengths[c] = shortest + (1.0F - shortest) * Flames.hash(c, seed);
        }
        return lengths;
    }

    private TatteredCloak() {
    }

    /** Доля длины плаща у строки сетки: кокетка считается началом. */
    private static float rowFraction(Layer layer, int j) {
        return j <= YOKE ? 0.0F : (float) (j - YOKE) / layer.rows();
    }

    /** Складка: от −1 (дно) до 1 (гребень); складки расходятся книзу. */
    private static float fold(float theta, float r) {
        return (0.72F * MathHelper.sin(theta * 7.0F + 1.2F) + 0.28F * MathHelper.sin(theta * 13.0F + 0.4F)) * Math.min(1.0F, r * 3.0F + 0.15F);
    }

    private static float theta(int c) {
        return ((float) c / COLS * 2.0F - 1.0F) * WRAP;
    }

    /** Покой слоя в координатах туловища (блоки): от него считается длина связей и память о форме. */
    private static float[][][] rest(Layer layer) {
        float[][][] rest = new float[COLS + 1][layer.total() + 1][];
        float z = 0.0F, y = layer.top();
        float dy = layer.length() / layer.rows();
        for (int j = 0; j <= layer.total(); j++) {
            float r = rowFraction(layer, j);
            float ease = r * r * (3.0F - 2.0F * r);
            float a, b, rowY, rowZ;
            if (j < YOKE) {
                float k = (float) j / YOKE;
                a = MathHelper.lerp(k, 4.4F, layer.yokeA());
                b = MathHelper.lerp(k, 2.75F, layer.yokeB());
                rowY = MathHelper.lerp(k, -0.5F, layer.top() - 0.8F);
                rowZ = 0.0F;
            } else {
                if (j > YOKE) {
                    float angle = REST_TILT * (0.2F + 0.8F * r);
                    z += MathHelper.sin(angle) * dy;
                    y += MathHelper.cos(angle) * dy;
                }
                a = MathHelper.lerp(ease, layer.shoulderA(), layer.bottomA());
                b = MathHelper.lerp(ease, layer.shoulderB(), layer.bottomB());
                rowY = y;
                rowZ = z;
            }
            for (int c = 0; c <= COLS; c++) {
                float theta = theta(c);
                rest[c][j] = new float[]{a * MathHelper.sin(theta) / 16.0F, rowY / 16.0F, (b * MathHelper.cos(theta) + rowZ) / 16.0F};
            }
        }
        return rest;
    }

    private static FormPhysics.Cloth cloth(FormPhysics physics, Layer layer) {
        float[] soft = new float[layer.total() + 1];
        for (int j = 0; j < soft.length; j++) {
            soft[j] = MathHelper.lerp((float) j / layer.total(), layer.softTop(), layer.softBottom());
        }
        return physics.cloth(layer.key(), rest(layer), YOKE + 1, soft, YOKE + layer.shredFrom() * layer.rows());
    }

    /**
     * Нарисовать плащ. {@code entry} — координаты туловища. {@code stepped} — можно считать физику (модель рисуется в мире, а не в инвентаре);
     * {@code bodyFromSim} — как перевести физику в координаты туловища.
     */
    static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, FormPhysics physics, float now, float grow, float speed, float heat,
            boolean stepped, Matrix4f simFromBody, Matrix4f bodyFromSim, float[][] capsules, float ground, Flames.Frame frame) {
        FormPhysics.Cloth cloakCloth = cloth(physics, CLOAK), mantleCloth = cloth(physics, MANTLE);
        if (stepped) {
            cloakCloth.step(simFromBody, now, capsules, ground, grow, speed, heat);
            mantleCloth.step(simFromBody, now, capsules, ground, grow, speed * 0.6F, heat * 0.6F);
        }
        MatrixStack.Entry entry = matrices.peek();
        VertexConsumer cloth = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        Vector3f[][] cloak = cloakCloth.local(bodyFromSim);
        Vector3f[][] cloakNormals = normals(cloak, CLOAK);
        pleat(cloak, cloakNormals, CLOAK);
        layer(cloth, entry, CLOAK, cloak, cloakNormals, light);
        Vector3f[][] mantle = mantleCloth.local(bodyFromSim);
        Vector3f[][] mantleNormals = normals(mantle, MANTLE);
        pleat(mantle, mantleNormals, MANTLE);
        layer(cloth, entry, MANTLE, mantle, mantleNormals, light);
        collar(cloth, entry, light);

        // Маленькие языки на кончиках длинных полос и тлеющая ткань.
        VertexConsumer blend = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(Flames.WHITE));
        for (int c = 2; c < COLS; c += 4) {
            float[] tip = point(cloak, CLOAK, c, CLOAK.shreds()[c], 0.5F);
            float size = grow * (0.8F + 0.5F * Flames.hash(c, 5));
            Flames.flame(blend, frame, tip[0] / 16.0F, tip[1] / 16.0F, tip[2] / 16.0F, size * 3.4F / 16.0F, size * 0.6F / 16.0F, c * 0.37F, 0.85F);
        }
        VertexConsumer glow = consumers.getBuffer(RenderLayer.getEyes(Flames.WHITE));
        for (int c = 0; c < COLS; c++) {
            for (int j = YOKE; j < CLOAK.total(); j++) {
                ember(glow, entry, cloak, cloakNormals, c, j, now, heat);
            }
        }
    }

    /** Складки поверх посчитанной ткани: гребни выступают, дно уходит внутрь, а вся поверхность движется вместе с физикой. */
    private static void pleat(Vector3f[][] p, Vector3f[][] n, Layer layer) {
        for (int c = 0; c <= COLS; c++) {
            for (int j = 0; j <= layer.total(); j++) {
                float amount = 0.55F * fold(theta(c), rowFraction(layer, j));
                p[c][j].add(n[c][j].x * amount, n[c][j].y * amount, n[c][j].z * amount);
            }
        }
    }

    private static void layer(VertexConsumer cloth, MatrixStack.Entry entry, Layer layer, Vector3f[][] p, Vector3f[][] n, int light) {
        for (int c = 0; c < COLS; c++) {
            for (int j = 0; j < layer.total(); j++) {
                strip(cloth, entry, layer, p, n, c, j, light, false);
                strip(cloth, entry, layer, p, n, c, j, light, true);
            }
        }
        // Боковые края полотна: у плаща есть толщина.
        for (int j = 0; j < layer.total(); j++) {
            hem(cloth, entry, layer, p, n, 0, j, light);
            hem(cloth, entry, layer, p, n, COLS, j, light);
        }
    }

    /** Нормали сетки наружу: сетка идёт слева направо и сверху вниз, так что направление у них одно и не зависит от того, как ткань завернулась. */
    private static Vector3f[][] normals(Vector3f[][] p, Layer layer) {
        Vector3f[][] n = new Vector3f[COLS + 1][layer.total() + 1];
        for (int c = 0; c <= COLS; c++) {
            for (int j = 0; j <= layer.total(); j++) {
                Vector3f across = new Vector3f(p[Math.min(COLS, c + 1)][j]).sub(p[Math.max(0, c - 1)][j]);
                Vector3f along = new Vector3f(p[c][Math.min(layer.total(), j + 1)]).sub(p[c][Math.max(0, j - 1)]);
                Vector3f normal = across.cross(along);
                if (normal.lengthSquared() < 1.0E-6F) {
                    normal.set(MathHelper.sin(theta(c)), 0.0F, MathHelper.cos(theta(c)));
                }
                n[c][j] = normal.normalize();
            }
        }
        return n;
    }

    /** Точка на полосе {@code c}: {@code r} — доля длины, {@code k} — поперёк полосы (0..1). */
    private static float[] point(Vector3f[][] p, Layer layer, int c, float r, float k) {
        float row = r * layer.rows() + YOKE;
        int j = Math.min(layer.total() - 1, (int) row);
        float m = row - j;
        Vector3f a = new Vector3f(p[c][j]).lerp(p[c + 1][j], k), b = new Vector3f(p[c][j + 1]).lerp(p[c + 1][j + 1], k);
        Vector3f v = a.lerp(b, m);
        return new float[]{v.x, v.y, v.z};
    }

    /** Насколько полоса сужается к концу: 0 — полная ширина, у самого кончика — почти до точки. */
    private static float inset(Layer layer, float r, float limit) {
        float s = MathHelper.clamp((r - layer.shredFrom()) / (limit - layer.shredFrom()), 0.0F, 1.0F);
        return s * s * 0.47F;
    }

    /** Цвет ткани с запечённой тенью: дно складки темнее, гребень светлее; у накидки кончики уходят в алую кайму. */
    private static int shade(Layer layer, int base, int c, float r, float limit) {
        float k = 0.72F + 0.42F * fold(theta(c), r);
        float trim = layer.trim() * MathHelper.clamp((r - limit * 0.72F) / (limit * 0.28F), 0.0F, 1.0F);
        int red = (int) MathHelper.lerp(trim, ((base >> 16) & 0xFF) * k, (TRIM_RGB >> 16) & 0xFF);
        int green = (int) MathHelper.lerp(trim, ((base >> 8) & 0xFF) * k, (TRIM_RGB >> 8) & 0xFF);
        int blue = (int) MathHelper.lerp(trim, (base & 0xFF) * k, TRIM_RGB & 0xFF);
        return (Math.min(255, red) << 16) | (Math.min(255, green) << 8) | Math.min(255, blue);
    }

    private static void strip(VertexConsumer out, MatrixStack.Entry entry, Layer layer, Vector3f[][] p, Vector3f[][] n, int c, int j, int light, boolean inner) {
        float limit = layer.shreds()[c];
        float r0 = rowFraction(layer, j), r1 = rowFraction(layer, j + 1);
        if (r0 >= limit) {
            return;
        }
        float end = Math.min(r1, limit);
        float k0 = inset(layer, r0, limit), k1 = inset(layer, end, limit);
        float f = r1 > r0 ? (end - r0) / (r1 - r0) : 1.0F;
        Vector3f bottomLeft = new Vector3f(p[c][j]).lerp(p[c][j + 1], f), bottomRight = new Vector3f(p[c + 1][j]).lerp(p[c + 1][j + 1], f);
        Vector3f a = new Vector3f(p[c][j]).lerp(p[c + 1][j], k0), b = new Vector3f(p[c + 1][j]).lerp(p[c][j], k0);
        Vector3f d = new Vector3f(bottomLeft).lerp(bottomRight, k1), e = new Vector3f(bottomRight).lerp(bottomLeft, k1);
        float u0 = (float) c / COLS * 3.0F, u1 = (float) (c + 1) / COLS * 3.0F, v0 = j * 0.16F, v1 = (j + f) * 0.16F;
        // Подкладка: внутри, на толщину ткани ближе к телу.
        float off = inner ? -THICKNESS : 0.0F, sign = inner ? -1.0F : 1.0F;
        int base = inner ? INNER_RGB : OUTER_RGB;
        vertex(out, entry, a, n[c][j], off, sign, u0, v0, shade(layer, base, c, r0, limit), light);
        vertex(out, entry, b, n[c + 1][j], off, sign, u1, v0, shade(layer, base, c + 1, r0, limit), light);
        vertex(out, entry, e, n[c + 1][j + 1], off, sign, u1, v1, shade(layer, base, c + 1, end, limit), light);
        vertex(out, entry, d, n[c][j + 1], off, sign, u0, v1, shade(layer, base, c, end, limit), light);
    }

    private static void hem(VertexConsumer out, MatrixStack.Entry entry, Layer layer, Vector3f[][] p, Vector3f[][] n, int c, int j, int light) {
        float v0 = j * 0.16F, v1 = (j + 1) * 0.16F;
        if (rowFraction(layer, j) >= layer.shreds()[Math.min(COLS - 1, c)]) {
            return;
        }
        Vector3f a = p[c][j], b = p[c][j + 1];
        vertex(out, entry, a, n[c][j], 0.0F, 1.0F, 0.0F, v0, HEM_RGB, light);
        vertex(out, entry, a, n[c][j], -THICKNESS, 1.0F, 0.1F, v0, HEM_RGB, light);
        vertex(out, entry, b, n[c][j + 1], -THICKNESS, 1.0F, 0.1F, v1, HEM_RGB, light);
        vertex(out, entry, b, n[c][j + 1], 0.0F, 1.0F, 0.0F, v1, HEM_RGB, light);
    }

    private static void vertex(VertexConsumer out, MatrixStack.Entry entry, Vector3f p, Vector3f n, float offset, float sign, float u, float v, int rgb, int light) {
        out.vertex(entry.getPositionMatrix(), (p.x + n.x * offset) / 16.0F, (p.y + n.y * offset) / 16.0F, (p.z + n.z * offset) / 16.0F)
                .color(0xFF000000 | rgb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, n.x * sign, n.y * sign, n.z * sign);
    }

    /** Стоячий воротник: кольцо с толщиной, расширяется кверху. Снаружи чёрный, внутри алый. */
    private static void collar(VertexConsumer out, MatrixStack.Entry entry, int light) {
        int steps = 12;
        float wrap = 118.0F * MathHelper.RADIANS_PER_DEGREE;
        for (int i = 0; i < steps; i++) {
            float t0 = ((float) i / steps * 2.0F - 1.0F) * wrap, t1 = ((float) (i + 1) / steps * 2.0F - 1.0F) * wrap;
            // Спереди воротник ниже, сзади поднимается выше.
            float h0 = -0.7F - 1.1F * (0.5F + 0.5F * MathHelper.cos(t0)), h1 = -0.7F - 1.1F * (0.5F + 0.5F * MathHelper.cos(t1));
            Vector3f a0 = new Vector3f(4.5F * MathHelper.sin(t0), 0.1F, 2.8F * MathHelper.cos(t0)), a1 = new Vector3f(4.5F * MathHelper.sin(t1), 0.1F, 2.8F * MathHelper.cos(t1));
            Vector3f b0 = new Vector3f(5.2F * MathHelper.sin(t0), h0, 4.6F * MathHelper.cos(t0)), b1 = new Vector3f(5.2F * MathHelper.sin(t1), h1, 4.6F * MathHelper.cos(t1));
            Vector3f n0 = new Vector3f(MathHelper.sin(t0), -0.2F, MathHelper.cos(t0)).normalize(), n1 = new Vector3f(MathHelper.sin(t1), -0.2F, MathHelper.cos(t1)).normalize();
            float u0 = (float) i / steps, u1 = (float) (i + 1) / steps;
            vertex(out, entry, a0, n0, 0.0F, 1.0F, u0, 0.0F, OUTER_RGB, light);
            vertex(out, entry, a1, n1, 0.0F, 1.0F, u1, 0.0F, OUTER_RGB, light);
            vertex(out, entry, b1, n1, 0.0F, 1.0F, u1, 0.3F, OUTER_RGB, light);
            vertex(out, entry, b0, n0, 0.0F, 1.0F, u0, 0.3F, OUTER_RGB, light);
            vertex(out, entry, a0, n0, -THICKNESS, -1.0F, u0, 0.0F, INNER_RGB, light);
            vertex(out, entry, a1, n1, -THICKNESS, -1.0F, u1, 0.0F, INNER_RGB, light);
            vertex(out, entry, b1, n1, -THICKNESS, -1.0F, u1, 0.3F, INNER_RGB, light);
            vertex(out, entry, b0, n0, -THICKNESS, -1.0F, u0, 0.3F, INNER_RGB, light);
        }
    }

    /** Тлеющая ткань у самых кончиков полос: дрожит, как уголь, а от жара разгорается сильнее. */
    private static void ember(VertexConsumer glow, MatrixStack.Entry entry, Vector3f[][] p, Vector3f[][] n, int c, int j, float time, float heat) {
        float limit = CLOAK.shreds()[c];
        float r0 = rowFraction(CLOAK, j), r1 = rowFraction(CLOAK, j + 1);
        float reach = 0.16F + 0.08F * Math.min(1.0F, heat);
        float from = limit - reach;
        if (r1 <= from || r0 >= limit) {
            return;
        }
        float end = Math.min(r1, limit);
        float f = (end - r0) / (r1 - r0);
        float k0 = inset(CLOAK, r0, limit), k1 = inset(CLOAK, end, limit);
        Vector3f bottomLeft = new Vector3f(p[c][j]).lerp(p[c][j + 1], f), bottomRight = new Vector3f(p[c + 1][j]).lerp(p[c + 1][j + 1], f);
        Vector3f a = new Vector3f(p[c][j]).lerp(p[c + 1][j], k0), b = new Vector3f(p[c + 1][j]).lerp(p[c][j], k0);
        Vector3f d = new Vector3f(bottomLeft).lerp(bottomRight, k1), e = new Vector3f(bottomRight).lerp(bottomLeft, k1);
        float flicker = 0.65F + 0.35F * MathHelper.sin(time * 0.5F + c * 2.3F + j);
        float top = MathHelper.clamp((r0 - from) / reach, 0.0F, 1.0F) * flicker, bottom = MathHelper.clamp((end - from) / reach, 0.0F, 1.0F) * flicker;
        Vector3f normal = n[c][j];
        for (float offset : new float[]{0.04F, -THICKNESS - 0.04F}) {
            float sign = offset > 0.0F ? 1.0F : -1.0F;
            glowVertex(glow, entry, a, normal, offset, sign, top);
            glowVertex(glow, entry, b, normal, offset, sign, top);
            glowVertex(glow, entry, e, normal, offset, sign, bottom);
            glowVertex(glow, entry, d, normal, offset, sign, bottom);
            glowVertex(glow, entry, d, normal, offset, sign, bottom);
            glowVertex(glow, entry, e, normal, offset, sign, bottom);
            glowVertex(glow, entry, b, normal, offset, sign, top);
            glowVertex(glow, entry, a, normal, offset, sign, top);
        }
    }

    private static void glowVertex(VertexConsumer out, MatrixStack.Entry entry, Vector3f p, Vector3f n, float offset, float sign, float k) {
        int red = (int) (235.0F * k), green = (int) (58.0F * k * k), blue = (int) (14.0F * k * k);
        out.vertex(entry.getPositionMatrix(), (p.x + n.x * offset) / 16.0F, (p.y + n.y * offset) / 16.0F, (p.z + n.z * offset) / 16.0F)
                .color(red, green, blue, 255).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry, n.x * sign, n.y * sign, n.z * sign);
    }
}
