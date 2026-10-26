package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Объёмный Зангецу — настоящая 3D-модель, а не выдавленная картинка. Широкий клинок-тесак со скосами:
 * толстый обух, плоская тёмная сердцевина, серебряная фаска к острой кромке и скошенное остриё.
 * Восьмигранная рукоять в оплётке, восьмигранное навершие и латунная муфта. На обеих сторонах клинка —
 * живой 3D-глаз: выпуклый обод, тёмный белок, жёлтая радужка с вертикальным зрачком, веки. Глаз
 * смотрит на ближайшего злого моба или на тебя, моргает, сердито щурится и горит в форме Пустого.
 *
 * <p>Меч строится в своих координатах (в пикселях модели): u — вдоль, от навершия к острию; v — поперёк,
 * от обуха (−) к лезвию (+); w — толщина. Середина рукояти стоит в центре предмета, клинок уходит
 * по диагонали вверх-вправо, как у ванильных мечей, лезвием вверх-влево.
 */
public class ZangetsuItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/entity/zangetsu_model.png");
    private static final Identifier EYE_TEXTURE = ZangetsuMod.id("textures/entity/zangetsu_eye.png");

    // ---- размеры (пиксели модели); те же числа — в tools/textures.cs (атлас)
    private static final float GRIP_U = 3.1F;
    private static final float POMMEL_END = 0.8F;
    private static final float HILT_END = 5.4F;
    private static final float BLADE_START = 6.2F;
    private static final float TIP_START = 18.2F;
    private static final float BLADE_END = 22.0F;
    private static final float SPINE = -2.3F;
    private static final float EDGE = 2.8F;
    private static final float BEVEL = 1.7F;
    private static final float SPINE_T = 0.62F;
    private static final float FLAT_T = 0.55F;
    private static final float EDGE_T = 0.05F;
    private static final float HILT_V = -0.5F;
    private static final int TIP_SEGMENTS = 10;
    private static final float FIRST_PERSON_LEAN = -24.0F;

    // ---- глаз
    private static final float EYE_U = 10.6F;
    private static final float EYE_V = -0.6F;
    private static final float EYE_SURFACE = 0.6F;
    private static final float EYE_W = 2.1F;
    private static final float EYE_H = 1.1F;
    private static final float IRIS = 0.95F;
    private static final int ROWS = 10;
    private static final float RIM = 0.34F;
    private static final float RIM_HEIGHT = 0.26F;

    // ---- оси меча в пространстве предмета
    private static final float S = 0.70710678F;
    private static final Vector3f AXIS_U = new Vector3f(S, S, 0.0F);
    private static final Vector3f AXIS_V = new Vector3f(-S, S, 0.0F);

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        int eye = stack.getOrDefault(ModComponents.EYE, Eye.CLOSED);
        matrices.push();
        if (mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND) {
            // От первого лица клинок наклонён к середине экрана, чтобы был виден целиком, а не столбом у края.
            float lean = mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND ? FIRST_PERSON_LEAN : -FIRST_PERSON_LEAN;
            matrices.translate(0.5F, 0.5F, 0.5F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lean));
            matrices.translate(-0.5F, -0.5F, -0.5F);
        }
        Mesh mesh = new Mesh(matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay);
        pommel(mesh);
        hilt(mesh);
        collar(mesh);
        blade(mesh, eye == Eye.BURNING);
        renderEye(eye, mode, matrices, consumers, light, overlay);
        matrices.pop();
    }

    /** Точка меча (u, v, w) → координаты предмета в блоках (середина рукояти — в центре предмета). */
    static Vector3f toModel(float u, float v, float w) {
        float du = u - GRIP_U;
        return new Vector3f(8.0F + (du - v) * S, 8.0F + (du + v) * S, 8.0F + w).div(16.0F);
    }

    // ---------------------------------------------------------------- рукоять

    private static float[][] octagon(float vc, float rv, float rw) {
        float[][] p = new float[8][2];
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45.0 * k);
            p[k][0] = vc + rv * (float) Math.cos(a);
            p[k][1] = rw * (float) Math.sin(a);
        }
        return p;
    }

    /** Восьмигранная призма вдоль u; текстура грани — полоса (tu0..tu1 вдоль, tv0..tv1 поперёк). */
    private static void prism(Mesh mesh, float u0, float u1, float[][] ring, float tu0, float tv0, float tu1, float tv1, boolean capStart) {
        for (int k = 0; k < 8; k++) {
            float[] a = ring[k], b = ring[(k + 1) % 8];
            mesh.quad(u0, a[0], a[1], tu0, tv0, u1, a[0], a[1], tu1, tv0, u1, b[0], b[1], tu1, tv1, u0, b[0], b[1], tu0, tv1,
                    0.0F, (a[0] + b[0]) / 2.0F - HILT_V, (a[1] + b[1]) / 2.0F);
        }
        if (capStart) {
            int[][] quads = {{0, 1, 2, 3}, {0, 3, 4, 7}, {4, 5, 6, 7}};
            for (int[] q : quads) {
                mesh.quad(u0, ring[q[0]][0], ring[q[0]][1], tu0, tv0, u0, ring[q[1]][0], ring[q[1]][1], tu0, tv1,
                        u0, ring[q[2]][0], ring[q[2]][1], tu1, tv1, u0, ring[q[3]][0], ring[q[3]][1], tu1, tv0, -1.0F, 0.0F, 0.0F);
            }
        }
    }

    private static void pommel(Mesh mesh) {
        prism(mesh, 0.0F, POMMEL_END, octagon(HILT_V, 1.0F, 0.86F), 64, 80, 96, 96, true);
        prism(mesh, POMMEL_END - 0.18F, POMMEL_END, octagon(HILT_V, 1.06F, 0.92F), 96, 80, 128, 96, false);
    }

    private static void hilt(Mesh mesh) {
        prism(mesh, POMMEL_END, HILT_END, octagon(HILT_V, 0.8F, 0.68F), 0, 80, 64, 96, false);
    }

    /** Латунная муфта у основания клинка. */
    private static void collar(Mesh mesh) {
        box(mesh, HILT_END, BLADE_START + 0.05F, -1.75F, 0.75F, -0.8F, 0.8F, 96, 80, 128, 96);
    }

    private static void box(Mesh m, float u0, float u1, float v0, float v1, float w0, float w1, float tu0, float tv0, float tu1, float tv1) {
        // ±w (широкие грани), ±v, ±u.
        m.quad(u0, v0, w1, tu0, tv0, u1, v0, w1, tu1, tv0, u1, v1, w1, tu1, tv1, u0, v1, w1, tu0, tv1, 0, 0, 1);
        m.quad(u0, v0, w0, tu0, tv0, u1, v0, w0, tu1, tv0, u1, v1, w0, tu1, tv1, u0, v1, w0, tu0, tv1, 0, 0, -1);
        m.quad(u0, v1, w0, tu0, tv0, u1, v1, w0, tu1, tv0, u1, v1, w1, tu1, tv1, u0, v1, w1, tu0, tv1, 0, 1, 0);
        m.quad(u0, v0, w0, tu0, tv0, u1, v0, w0, tu1, tv0, u1, v0, w1, tu1, tv1, u0, v0, w1, tu0, tv1, 0, -1, 0);
        m.quad(u0, v0, w0, tu0, tv0, u0, v1, w0, tu1, tv0, u0, v1, w1, tu1, tv1, u0, v0, w1, tu0, tv1, -1, 0, 0);
        m.quad(u1, v0, w0, tu0, tv0, u1, v1, w0, tu1, tv0, u1, v1, w1, tu1, tv1, u1, v0, w1, tu0, tv1, 1, 0, 0);
    }

    // ---------------------------------------------------------------- клинок

    /** Сечение клинка в точке u: {обух, начало скоса, кромка, толщина обуха, у скоса, у кромки}. */
    private static float[] section(float u) {
        float s = MathHelper.clamp((u - TIP_START) / (BLADE_END - TIP_START), 0.0F, 1.0F);
        float spine = SPINE + 0.35F * s * s;
        // Остриё тесака: кромка дугой поднимается к обуху.
        float edge = MathHelper.lerp((float) Math.pow(s, 1.35), EDGE, spine);
        float bevel = BEVEL * (1.0F - s) + 0.25F * s;
        float flat = Math.max(spine + 0.08F, edge - bevel);
        float k = 1.0F - 0.75F * s;
        return new float[]{spine, flat, edge, SPINE_T * k, FLAT_T * k, EDGE_T};
    }

    /** Координаты атласа для точки клинка (u, v): по длине → x, поперёк → y. */
    private static float bladeTu(float u) {
        return (u - BLADE_START) / (BLADE_END - BLADE_START) * 112.0F;
    }

    private static float bladeTv(float v, int row0) {
        return row0 + MathHelper.clamp((v - SPINE) / (EDGE - SPINE), 0.0F, 1.0F) * 40.0F;
    }

    private static void blade(Mesh m, boolean burning) {
        int row0 = burning ? 40 : 0;
        float[] us = new float[TIP_SEGMENTS + 2];
        us[0] = BLADE_START;
        for (int i = 0; i <= TIP_SEGMENTS; i++) {
            us[i + 1] = TIP_START + (BLADE_END - TIP_START) * i / TIP_SEGMENTS;
        }
        for (int i = 0; i + 1 < us.length; i++) {
            float ua = us[i], ub = us[i + 1];
            float[] a = section(ua), b = section(ub);
            float tua = bladeTu(ua), tub = bladeTu(ub);
            for (int side = -1; side <= 1; side += 2) {
                // Плоская сердцевина: от обуха до начала скоса.
                m.quad(ua, a[0], side * a[3], tua, bladeTv(a[0], row0), ub, b[0], side * b[3], tub, bladeTv(b[0], row0),
                        ub, b[1], side * b[4], tub, bladeTv(b[1], row0), ua, a[1], side * a[4], tua, bladeTv(a[1], row0), 0, 0, side);
                // Скос к кромке.
                m.quad(ua, a[1], side * a[4], tua, bladeTv(a[1], row0), ub, b[1], side * b[4], tub, bladeTv(b[1], row0),
                        ub, b[2], side * b[5], tub, bladeTv(b[2], row0), ua, a[2], side * a[5], tua, bladeTv(a[2], row0), 0, 0.35F, side);
            }
            // Кромка и обух.
            m.quad(ua, a[2], a[5], tua, row0 + 39.5F, ub, b[2], b[5], tub, row0 + 39.5F, ub, b[2], -b[5], tub, row0 + 39.9F,
                    ua, a[2], -a[5], tua, row0 + 39.9F, 0, 1, 0);
            m.quad(ua, a[0], a[3], tua, row0 + 0.2F, ub, b[0], b[3], tub, row0 + 0.2F, ub, b[0], -b[3], tub, row0 + 1.4F,
                    ua, a[0], -a[3], tua, row0 + 1.4F, 0, -1, 0);
        }
        // Торцы: у основания (под муфтой) и у самого острия.
        for (float u : new float[]{BLADE_START, BLADE_END}) {
            float[] c = section(u);
            float dir = u == BLADE_START ? -1.0F : 1.0F;
            float tu = bladeTu(u);
            m.quad(u, c[0], c[3], tu, row0 + 1, u, c[1], c[4], tu, row0 + 20, u, c[1], -c[4], tu + 1, row0 + 20, u, c[0], -c[3], tu + 1, row0 + 1, dir, 0, 0);
            m.quad(u, c[1], c[4], tu, row0 + 20, u, c[2], c[5], tu, row0 + 39, u, c[2], -c[5], tu + 1, row0 + 39, u, c[1], -c[4], tu + 1, row0 + 20, dir, 0, 0);
        }
    }

    // ---------------------------------------------------------------- глаз

    private void renderEye(int state, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        Matrix4f model = matrices.peek().getPositionMatrix();
        float[] gaze = EyeTracker.gaze(mode, model, toModel(EYE_U, EYE_V, EYE_SURFACE), AXIS_U, AXIS_V,
                EYE_W - IRIS - 0.15F, EYE_H - IRIS * 0.55F);
        float openness = switch (state) {
            case Eye.OPEN, Eye.BURNING -> 1.0F;
            case Eye.ANGRY -> 0.55F;
            default -> 0.0F;
        };
        openness = EyeTracker.blink(mode, openness);
        float pupil = state == Eye.BURNING ? 0.17F : (state == Eye.ANGRY ? 0.15F : 0.3F);
        float irisU = state == Eye.BURNING ? 8.0F : (state == Eye.ANGRY ? 16.0F : 0.0F);
        // Буфер берём заново перед каждым слоем: смена слоя закрывает предыдущий.
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(EYE_TEXTURE));
        for (int side = -1; side <= 1; side += 2) {
            eyeSide(new Face(matrices.peek(), vertices, side, light, overlay), gaze, openness, state == Eye.ANGRY, pupil, irisU);
        }
        if (state == Eye.OPEN || state == Eye.BURNING) {
            // Радужка светится в темноте.
            VertexConsumer glowing = consumers.getBuffer(RenderLayer.getEyes(EYE_TEXTURE));
            for (int side = -1; side <= 1; side += 2) {
                Face glow = new Face(matrices.peek(), glowing, side, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay);
                glow.quad(gaze[0] - IRIS, gaze[1] - IRIS, gaze[0] + IRIS, gaze[1] + IRIS, 0.06F,
                        irisU / 32.0F, 8.0F / 32.0F, (irisU + 8.0F) / 32.0F, 16.0F / 32.0F, state == Eye.BURNING ? 0xFFFFFFFF : 0xFF9A7A3A);
            }
        }
    }

    private static float halfLength(float b) {
        float t = MathHelper.clamp(b / EYE_H, -1.0F, 1.0F);
        return EYE_W * (float) Math.pow(1.0F - t * t, 0.8F);
    }

    /** Одна сторона глаза (a — вдоль клинка, b — поперёк): белок, радужка, зрачок, блик, обод и веки. */
    private static void eyeSide(Face f, float[] gaze, float openness, boolean angry, float pupil, float irisU) {
        float rowH = 2.0F * EYE_H / ROWS;
        float ia = gaze[0], ib = gaze[1];
        for (int r = 0; r < ROWS; r++) {
            float b0 = -EYE_H + r * rowH, b1 = b0 + rowH, bc = (b0 + b1) / 2.0F;
            float hl = halfLength(bc) + 0.05F;
            f.quad(-hl, b0, hl, b1, 0.02F, (EYE_W - hl) / (2 * EYE_W) * 0.5F, (EYE_H - b1) / (2 * EYE_H) * 0.25F,
                    (EYE_W + hl) / (2 * EYE_W) * 0.5F, (EYE_H - b0) / (2 * EYE_H) * 0.25F, 0xFFFFFFFF);
        }
        f.quad(ia - IRIS, ib - IRIS, ia + IRIS, ib + IRIS, 0.05F, irisU / 32.0F, 8.0F / 32.0F, (irisU + 8.0F) / 32.0F, 16.0F / 32.0F, 0xFFFFFFFF);
        // Вертикальный зрачок — поперёк клинка.
        f.quad(ia - pupil / 2.0F, ib - IRIS * 0.78F, ia + pupil / 2.0F, ib + IRIS * 0.78F, 0.07F,
                24.5F / 32.0F, 16.5F / 32.0F, 27.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);
        f.quad(ia - 0.55F, ib + 0.28F, ia - 0.27F, ib + 0.56F, 0.08F, 28.5F / 32.0F, 16.5F / 32.0F, 31.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);

        // Веки: верхнее (к обуху, −b) и нижнее (к лезвию, +b) сходятся; у сердитого верхнее скошено.
        float meet = 0.1F;
        float upper = MathHelper.lerp(openness, meet, -EYE_H);
        float lower = MathHelper.lerp(openness, meet, EYE_H);
        float slope = angry ? 0.3F : 0.0F;
        for (int r = 0; r < ROWS; r++) {
            float b0 = -EYE_H + r * rowH, b1 = b0 + rowH, bc = (b0 + b1) / 2.0F;
            float hl = halfLength(bc);
            f.box(-hl - RIM, b0, -hl, b1, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
            f.box(hl, b0, hl + RIM, b1, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
            float visibleFrom = -hl, visibleTo = hl;
            if (bc > lower) {
                visibleFrom = visibleTo;
            } else if (slope == 0.0F) {
                if (bc < upper) {
                    visibleFrom = visibleTo;
                }
            } else {
                // Скошенное верхнее веко: линия upper − slope·a/W; видно там, где строка ниже линии.
                float cut = (upper - bc) / slope * EYE_W;
                visibleFrom = Math.max(visibleFrom, cut);
            }
            if (visibleFrom >= visibleTo) {
                lid(f, -hl, b0, hl, b1, Math.abs(bc - upper) < rowH || Math.abs(bc - lower) < rowH);
            } else if (visibleFrom > -hl) {
                lid(f, -hl, b0, visibleFrom, b1, true);
            }
        }
        float tip = halfLength(EYE_H - rowH / 2.0F) + RIM;
        f.box(-tip, EYE_H, tip, EYE_H + RIM * 0.7F, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
        f.box(-tip, -EYE_H - RIM * 0.7F, tip, -EYE_H, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
    }

    private static void lid(Face f, float a0, float b0, float a1, float b1, boolean lashes) {
        f.box(a0, b0, a1, b1, 0.2F, 0.0F, 16.0F, 16.0F, lashes ? 17.0F : 20.0F);
        if (lashes) {
            f.quad(a0, b0 + (b1 - b0) * 0.35F, a1, b0 + (b1 - b0) * 0.65F, 0.205F, 24.5F / 32.0F, 16.5F / 32.0F, 27.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);
        }
    }

    // ---------------------------------------------------------------- вывод вершин

    /** Сетка меча: четырёхугольники в координатах меча, UV в пикселях атласа 128×128, нормаль по подсказке. */
    private record Mesh(MatrixStack.Entry entry, VertexConsumer vertices, int light, int overlay) {
        void quad(float u0, float v0, float w0, float tu0, float tv0, float u1, float v1, float w1, float tu1, float tv1,
                float u2, float v2, float w2, float tu2, float tv2, float u3, float v3, float w3, float tu3, float tv3,
                float hu, float hv, float hw) {
            Vector3f p0 = toModel(u0, v0, w0), p1 = toModel(u1, v1, w1), p2 = toModel(u2, v2, w2), p3 = toModel(u3, v3, w3);
            Vector3f normal = new Vector3f(p2).sub(p0).cross(new Vector3f(p3).sub(p1));
            Vector3f hint = new Vector3f(AXIS_U).mul(hu).add(new Vector3f(AXIS_V).mul(hv)).add(0.0F, 0.0F, hw);
            if (normal.lengthSquared() < 1.0E-12F) {
                normal.set(hint);
            }
            if (normal.dot(hint) < 0.0F) {
                normal.negate();
            }
            normal.normalize();
            this.vertex(p0, tu0, tv0, normal);
            this.vertex(p1, tu1, tv1, normal);
            this.vertex(p2, tu2, tv2, normal);
            this.vertex(p3, tu3, tv3, normal);
        }

        private void vertex(Vector3f p, float tu, float tv, Vector3f n) {
            this.vertices.vertex(this.entry.getPositionMatrix(), p.x, p.y, p.z).color(-1).texture(tu / 128.0F, tv / 128.0F)
                    .overlay(this.overlay).light(this.light).normal(this.entry, n.x, n.y, n.z);
        }
    }

    /** Рисование на одной стороне клинка в координатах глаза: a — вдоль клинка, b — поперёк, lift — над поверхностью. */
    private record Face(MatrixStack.Entry entry, VertexConsumer vertices, int side, int light, int overlay) {
        private Vector3f at(float a, float b, float lift) {
            return toModel(EYE_U + a, EYE_V + b, this.side * (EYE_SURFACE + lift));
        }

        void quad(float a0, float b0, float a1, float b1, float lift, float u0, float v0, float u1, float v1, int color) {
            Vector3f n = new Vector3f(0.0F, 0.0F, this.side);
            this.vertex(this.at(a0, b0, lift), u0, v1, color, n);
            this.vertex(this.at(a1, b0, lift), u1, v1, color, n);
            this.vertex(this.at(a1, b1, lift), u1, v0, color, n);
            this.vertex(this.at(a0, b1, lift), u0, v0, color, n);
        }

        /** Выпуклый брусок высотой {@code lift} над поверхностью: верх и четыре бока. UV — в пикселях текстуры 32×32. */
        void box(float a0, float b0, float a1, float b1, float lift, float tu0, float tv0, float tu1, float tv1) {
            float u0 = tu0 / 32.0F, v0 = tv0 / 32.0F, u1 = tu1 / 32.0F, v1 = tv1 / 32.0F;
            this.quad(a0, b0, a1, b1, lift, u0, v0, u1, v1, 0xFFFFFFFF);
            int shade = 0xFFB0B0B0;
            this.side(a0, b0, a1, b0, lift, u0, v0, u1, v1, shade, new Vector3f(AXIS_V).negate());
            this.side(a1, b1, a0, b1, lift, u0, v0, u1, v1, shade, new Vector3f(AXIS_V));
            this.side(a0, b1, a0, b0, lift, u0, v0, u1, v1, shade, new Vector3f(AXIS_U).negate());
            this.side(a1, b0, a1, b1, lift, u0, v0, u1, v1, shade, new Vector3f(AXIS_U));
        }

        private void side(float aa, float ba, float ab, float bb, float lift, float u0, float v0, float u1, float v1, int color, Vector3f n) {
            this.vertex(this.at(aa, ba, 0.0F), u0, v1, color, n);
            this.vertex(this.at(ab, bb, 0.0F), u1, v1, color, n);
            this.vertex(this.at(ab, bb, lift), u1, v0, color, n);
            this.vertex(this.at(aa, ba, lift), u0, v0, color, n);
        }

        private void vertex(Vector3f p, float u, float v, int color, Vector3f n) {
            this.vertices.vertex(this.entry.getPositionMatrix(), p.x, p.y, p.z).color(color).texture(u, v).overlay(this.overlay)
                    .light(this.light).normal(this.entry, n.x, n.y, n.z);
        }
    }
}
