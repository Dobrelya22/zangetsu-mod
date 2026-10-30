package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Объёмный Зангецу — настоящая 3D-модель, а не выдавленная картинка. Широкий клинок-тесак со скосами:
 * толстый обух, плоская тёмная сердцевина со шлифовкой, долом и гравировкой «斬月», туманный хамон и
 * серебряная фаска к острой кромке. Рукоять — кожа ската, поверх неё крест-накрест выпуклые ленты
 * шёлкового шнура; латунная муфта с фланцем, воронёное навершие с кольцом и обломком цепи, которая
 * висит по силе тяжести. У горящего клинка трещины светятся в темноте. На обеих сторонах клинка — живой
 * 3D-глаз: выпуклый обод, тёмный белок, жёлтая радужка с вертикальным зрачком, веки и влажная роговица.
 * Глаз смотрит на ближайшего злого моба или на тебя, моргает, сердито щурится и горит в форме Пустого.
 *
 * <p>Меч строится в своих координатах (в пикселях модели): u — вдоль, от навершия к острию; v — поперёк,
 * от обуха (−) к лезвию (+); w — толщина. Середина рукояти стоит в центре предмета, клинок уходит
 * по диагонали вверх-вправо, как у ванильных мечей, лезвием вверх-влево.
 */
public class ZangetsuItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/entity/zangetsu_model.png");
    private static final Identifier EYE_TEXTURE = ZangetsuMod.id("textures/entity/zangetsu_eye.png");
    /** Раскалённые трещины горящего клинка — тот же атлас, рисуется светом поверх. */
    private static final Identifier GLOW_TEXTURE = ZangetsuMod.id("textures/entity/zangetsu_glow.png");
    /** Атлас 512×512 (раскладка — в tools/textures.cs): клинок 448×160, горящий клинок под ним, справа рукоять и металл. */
    private static final float ATLAS = 512.0F;
    private static final float BLADE_TW = 448.0F;
    private static final float BLADE_TH = 160.0F;

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
    /** Кожа ската под шнуром (полуоси сечения) и шаг витка шнура. */
    private static final float CORE_V = 0.72F;
    private static final float CORE_W = 0.6F;
    private static final float CORD_PITCH = 1.15F;
    private static final int TIP_SEGMENTS = 10;
    // ---- от первого лица (в пространстве руки: x — вправо, y — вверх, z — к камере)
    private static final Vector3f FP_BLADE = new Vector3f(-0.5F, 0.75F, -0.45F);
    private static final Vector3f FP_FACE = new Vector3f(0.35F, 0.15F, 0.92F);
    private static final Vector3f FP_GRIP = new Vector3f(0.11F, -0.1F, -0.02F);
    private static final float FP_SCALE = 0.78F;

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
            firstPerson(matrices, mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND);
        }
        boolean burning = eye == Eye.BURNING;
        Mesh mesh = new Mesh(matrices.peek(), consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, overlay);
        pommel(mesh);
        if (mode != ModelTransformationMode.GUI) {
            chain(mesh, gravity(matrices, mode), animationTime());
        }
        hilt(mesh);
        collar(mesh);
        blade(mesh, burning);
        if (burning) {
            // Трещины горят и в темноте.
            blade(new Mesh(matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(GLOW_TEXTURE)), LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    overlay), true);
        }
        renderEye(eye, mode, matrices, consumers, light, overlay);
        matrices.pop();
    }

    /**
     * От первого лица меч ставится не стандартными поворотами предмета, а прямо по осям экрана: рукоять
     * в кулаке, клинок уходит вверх и к середине экрана, плоскость с глазом повёрнута к камере. Левая рука —
     * зеркально. (В модели предмета для этих режимов стоят нулевые повороты.)
     */
    private static void firstPerson(MatrixStack matrices, boolean right) {
        float side = right ? 1.0F : -1.0F;
        Vector3f blade = new Vector3f(FP_BLADE.x * side, FP_BLADE.y, FP_BLADE.z).normalize();
        Vector3f face = new Vector3f(FP_FACE.x * side, FP_FACE.y, FP_FACE.z);
        face.sub(new Vector3f(blade).mul(face.dot(blade))).normalize();
        Vector3f edge = new Vector3f(face).cross(blade);
        // Оси меча (вдоль, к лезвию, толщина) → оси руки.
        Matrix3f hand = new Matrix3f(blade, edge, face);
        Matrix3f item = new Matrix3f(AXIS_U, AXIS_V, new Vector3f(0.0F, 0.0F, 1.0F));
        Quaternionf rotation = new Quaternionf().setFromNormalized(hand.mul(item.transpose()));
        matrices.translate(0.5F, 0.5F, 0.5F);
        matrices.translate(FP_GRIP.x * side, FP_GRIP.y, FP_GRIP.z);
        matrices.multiply(rotation);
        matrices.scale(FP_SCALE, FP_SCALE, FP_SCALE);
        matrices.translate(-0.5F, -0.5F, -0.5F);
    }

    /** «Вниз» по силе тяжести в координатах меча (u, v, w) — для цепи на навершии. */
    private static Vector3f gravity(MatrixStack matrices, ModelTransformationMode mode) {
        Vector3f down = new Vector3f(0.0F, -1.0F, 0.0F);
        if (mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND) {
            // Руки рисуются в пространстве камеры.
            down.rotate(new Quaternionf(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation()).conjugate());
        }
        Vector3f item = new Matrix3f(matrices.peek().getPositionMatrix()).invert().transform(down);
        Vector3f d = new Vector3f(item.dot(AXIS_U), item.dot(AXIS_V), item.z);
        return d.lengthSquared() < 1.0E-8F ? new Vector3f(-1.0F, 0.0F, 0.0F) : d.normalize();
    }

    private static float animationTime() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.world == null ? 0.0F : client.world.getTime() + client.getRenderTickCounter().getTickDelta(false);
    }

    /** Точка меча (u, v, w) → координаты предмета в блоках (середина рукояти — в центре предмета). */
    static Vector3f toModel(float u, float v, float w) {
        float du = u - GRIP_U;
        return new Vector3f(8.0F + (du - v) * S, 8.0F + (du + v) * S, 8.0F + w).div(16.0F);
    }

    // ---------------------------------------------------------------- рукоять

    /** Труба вдоль u: эллипс (rv0, rw0) в u0 переходит в (rv1, rw1) в u1; текстура — полоса атласа вокруг. */
    private static void tube(Mesh m, float u0, float u1, float rv0, float rw0, float rv1, float rw1, int sides,
            float tu0, float tv0, float tu1, float tv1, boolean capStart) {
        for (int k = 0; k < sides; k++) {
            float a = MathHelper.TAU * k / sides, b = MathHelper.TAU * (k + 1) / sides;
            float ca = MathHelper.cos(a), sa = MathHelper.sin(a), cb = MathHelper.cos(b), sb = MathHelper.sin(b);
            float ta = MathHelper.lerp((float) k / sides, tu0, tu1), tb = MathHelper.lerp((float) (k + 1) / sides, tu0, tu1);
            m.quad(u0, HILT_V + rv0 * ca, rw0 * sa, ta, tv0, u1, HILT_V + rv1 * ca, rw1 * sa, ta, tv1,
                    u1, HILT_V + rv1 * cb, rw1 * sb, tb, tv1, u0, HILT_V + rv0 * cb, rw0 * sb, tb, tv0,
                    0.0F, (ca + cb) * 0.5F, (sa + sb) * 0.5F);
        }
        if (capStart) {
            for (int k = 0; k < sides; k++) {
                float a = MathHelper.TAU * k / sides, b = MathHelper.TAU * (k + 1) / sides;
                m.quad(u0, HILT_V, 0.0F, tu0, tv0, u0, HILT_V + rv0 * MathHelper.cos(a), rw0 * MathHelper.sin(a), tu0, tv1,
                        u0, HILT_V + rv0 * MathHelper.cos(b), rw0 * MathHelper.sin(b), tu1, tv1, u0, HILT_V, 0.0F, tu1, tv0, -1.0F, 0.0F, 0.0F);
            }
        }
    }

    /** Навершие: воронёный колпак со скруглённым торцом, латунный поясок и кольцо для цепи. */
    private static void pommel(Mesh m) {
        tube(m, -0.3F, 0.0F, 0.66F, 0.55F, 0.98F, 0.84F, 12, 448, 96, 512, 104, true);
        tube(m, 0.0F, POMMEL_END - 0.16F, 0.98F, 0.84F, 0.98F, 0.84F, 12, 448, 104, 512, 128, false);
        tube(m, POMMEL_END - 0.16F, POMMEL_END, 1.07F, 0.93F, 1.07F, 0.93F, 12, 448, 128, 512, 136, false);
        torus(m, new Vector3f(-0.62F, HILT_V, 0.0F), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), 0.36F, 0.36F, 0.1F, 448, 96, 512, 128);
    }

    /** Рукоять: кожа ската и поверх неё шнур крест-накрест — настоящие выпуклые ленты, между ними ромбы кожи. */
    private static void hilt(Mesh m) {
        tube(m, POMMEL_END, HILT_END, CORE_V, CORE_W, CORE_V, CORE_W, 16, 448, 0, 512, 64, false);
        for (int strand = 0; strand < 2; strand++) {
            ribbon(m, 1, strand * MathHelper.PI, 0.14F);
            ribbon(m, -1, strand * MathHelper.PI + MathHelper.HALF_PI, 0.1F);
        }
    }

    /** Лента шнура: виток по рукояти (dir — в какую сторону), выпуклая посередине. */
    private static void ribbon(Mesh m, int dir, float phase, float lift) {
        int segments = 48;
        float half = 0.42F, length = HILT_END - POMMEL_END;
        for (int i = 0; i < segments; i++) {
            float u0 = POMMEL_END + length * i / segments, u1 = POMMEL_END + length * (i + 1) / segments;
            float c0 = phase + dir * MathHelper.TAU * (u0 - POMMEL_END) / CORD_PITCH;
            float c1 = phase + dir * MathHelper.TAU * (u1 - POMMEL_END) / CORD_PITCH;
            float[] a0 = cordPoint(c0 - half, lift), m0 = cordPoint(c0, lift + 0.07F), b0 = cordPoint(c0 + half, lift);
            float[] a1 = cordPoint(c1 - half, lift), m1 = cordPoint(c1, lift + 0.07F), b1 = cordPoint(c1 + half, lift);
            float t0 = 448 + (i % 8) * 8.0F, t1 = t0 + 8.0F;
            float hv = MathHelper.cos(c0), hw = MathHelper.sin(c0);
            m.quad(u0, a0[0], a0[1], t0, 64, u1, a1[0], a1[1], t1, 64, u1, m1[0], m1[1], t1, 80, u0, m0[0], m0[1], t0, 80, 0.0F, hv, hw);
            m.quad(u0, m0[0], m0[1], t0, 80, u1, m1[0], m1[1], t1, 80, u1, b1[0], b1[1], t1, 96, u0, b0[0], b0[1], t0, 96, 0.0F, hv, hw);
        }
    }

    private static float[] cordPoint(float angle, float lift) {
        return new float[]{HILT_V + (CORE_V + lift) * MathHelper.cos(angle), (CORE_W + lift) * MathHelper.sin(angle)};
    }

    /** Латунная муфта у основания клинка и фланец перед ней. */
    private static void collar(Mesh mesh) {
        box(mesh, HILT_END - 0.1F, BLADE_START + 0.05F, -1.75F, 0.75F, -0.8F, 0.8F, 448, 136, 512, 160);
        box(mesh, HILT_END - 0.3F, HILT_END + 0.12F, -2.05F, 1.05F, -0.96F, 0.96F, 448, 128, 512, 152);
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

    /**
     * Тор в координатах меча: центр, две оси плоскости кольца (по ним полуоси a и b), толщина r.
     * Звенья цепи и кольцо навершия.
     */
    private static void torus(Mesh m, Vector3f center, Vector3f axisA, Vector3f axisB, float a, float b, float r,
            float tu0, float tv0, float tu1, float tv1) {
        Vector3f normal = new Vector3f(axisA).cross(axisB).normalize();
        int around = 10, tube = 5;
        for (int i = 0; i < around; i++) {
            for (int j = 0; j < tube; j++) {
                Vector3f p00 = torusPoint(center, axisA, axisB, normal, a, b, r, i, j, around, tube);
                Vector3f p10 = torusPoint(center, axisA, axisB, normal, a, b, r, i + 1, j, around, tube);
                Vector3f p11 = torusPoint(center, axisA, axisB, normal, a, b, r, i + 1, j + 1, around, tube);
                Vector3f p01 = torusPoint(center, axisA, axisB, normal, a, b, r, i, j + 1, around, tube);
                float ta = MathHelper.lerp((float) i / around, tu0, tu1), tb = MathHelper.lerp((float) (i + 1) / around, tu0, tu1);
                float va = MathHelper.lerp((float) j / tube, tv0, tv1), vb = MathHelper.lerp((float) (j + 1) / tube, tv0, tv1);
                Vector3f hint = new Vector3f(p00).add(p11).mul(0.5F).sub(torusPoint(center, axisA, axisB, normal, a, b, 0.0F, i, j, around, tube));
                m.quad(p00.x, p00.y, p00.z, ta, va, p10.x, p10.y, p10.z, tb, va, p11.x, p11.y, p11.z, tb, vb, p01.x, p01.y, p01.z, ta, vb,
                        hint.x, hint.y, hint.z);
            }
        }
    }

    private static Vector3f torusPoint(Vector3f c, Vector3f axisA, Vector3f axisB, Vector3f normal, float a, float b, float r,
            int i, int j, int around, int tube) {
        float alpha = MathHelper.TAU * i / around, beta = MathHelper.TAU * j / tube;
        float ca = MathHelper.cos(alpha), sa = MathHelper.sin(alpha);
        Vector3f radial = new Vector3f(axisA).mul(ca).add(new Vector3f(axisB).mul(sa)).normalize();
        return new Vector3f(c).add(new Vector3f(axisA).mul(a * ca)).add(new Vector3f(axisB).mul(b * sa))
                .add(radial.mul(r * MathHelper.cos(beta))).add(new Vector3f(normal).mul(r * MathHelper.sin(beta)));
    }

    /**
     * Обломок цепи на навершии — три звена висят по силе тяжести ({@code down} — «вниз» в координатах меча)
     * и чуть покачиваются; вдоль рукояти не ложатся, а отходят к обуху.
     */
    private static void chain(Mesh m, Vector3f down, float time) {
        Vector3f d = new Vector3f(down);
        float along = d.x;
        if (along > 0.0F) {
            d.x -= along * 0.85F;
            d.y -= along * 0.6F;
        }
        d.normalize();
        Vector3f side = new Vector3f(d).cross(0.0F, 0.0F, 1.0F);
        if (side.lengthSquared() < 1.0E-4F) {
            side.set(0.0F, 1.0F, 0.0F);
        }
        side.normalize();
        Vector3f other = new Vector3f(d).cross(side).normalize();
        Vector3f at = new Vector3f(-0.62F - 0.36F, HILT_V, 0.0F);
        for (int k = 0; k < 3; k++) {
            float sway = MathHelper.sin(time * 0.11F + k * 0.9F) * 0.05F * (k + 1);
            Vector3f axis = new Vector3f(d).add(new Vector3f(side).mul(sway)).normalize();
            Vector3f across = (k % 2 == 0 ? new Vector3f(side) : new Vector3f(other));
            across.sub(new Vector3f(axis).mul(across.dot(axis))).normalize();
            Vector3f center = new Vector3f(at).add(new Vector3f(axis).mul(0.42F));
            torus(m, center, axis, across, 0.46F, 0.27F, 0.085F, 448, 160, 512, 192);
            at = new Vector3f(center).add(new Vector3f(axis).mul(0.46F));
        }
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
        return (u - BLADE_START) / (BLADE_END - BLADE_START) * BLADE_TW;
    }

    private static float bladeTv(float v, int row0) {
        return row0 + MathHelper.clamp((v - SPINE) / (EDGE - SPINE), 0.0F, 1.0F) * BLADE_TH;
    }

    private static void blade(Mesh m, boolean burning) {
        int row0 = burning ? (int) BLADE_TH : 0;
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
            m.quad(ua, a[2], a[5], tua, row0 + 158.0F, ub, b[2], b[5], tub, row0 + 158.0F, ub, b[2], -b[5], tub, row0 + 159.6F,
                    ua, a[2], -a[5], tua, row0 + 159.6F, 0, 1, 0);
            m.quad(ua, a[0], a[3], tua, row0 + 0.8F, ub, b[0], b[3], tub, row0 + 0.8F, ub, b[0], -b[3], tub, row0 + 5.6F,
                    ua, a[0], -a[3], tua, row0 + 5.6F, 0, -1, 0);
        }
        // Торцы: у основания (под муфтой) и у самого острия.
        for (float u : new float[]{BLADE_START, BLADE_END}) {
            float[] c = section(u);
            float dir = u == BLADE_START ? -1.0F : 1.0F;
            float tu = bladeTu(u);
            m.quad(u, c[0], c[3], tu, row0 + 4, u, c[1], c[4], tu, row0 + 80, u, c[1], -c[4], tu + 4, row0 + 80, u, c[0], -c[3], tu + 4, row0 + 4, dir, 0, 0);
            m.quad(u, c[1], c[4], tu, row0 + 80, u, c[2], c[5], tu, row0 + 156, u, c[2], -c[5], tu + 4, row0 + 156, u, c[1], -c[4], tu + 4, row0 + 80, dir, 0, 0);
        }
    }

    // ---------------------------------------------------------------- глаз

    private void renderEye(int state, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        Matrix4f model = matrices.peek().getPositionMatrix();
        float[] gaze = EyeTracker.gaze(mode, model, toModel(EYE_U, EYE_V, EYE_SURFACE), AXIS_U, AXIS_V,
                EYE_W - IRIS - 0.15F, EYE_H - IRIS * 0.75F);
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
                clipped(glow, gaze[0] - IRIS, gaze[1] - IRIS, gaze[0] + IRIS, gaze[1] + IRIS, 0.06F,
                        irisU / 32.0F, 8.0F / 32.0F, (irisU + 8.0F) / 32.0F, 16.0F / 32.0F, state == Eye.BURNING ? 0xFFFFFFFF : 0xFF9A7A3A);
            }
        }
        if (openness > 0.85F) {
            // Влажная роговица: прозрачный купол с бликом поверх открытого глаза.
            VertexConsumer gloss = consumers.getBuffer(RenderLayer.getEntityTranslucent(EYE_TEXTURE));
            for (int side = -1; side <= 1; side += 2) {
                cornea(new Face(matrices.peek(), gloss, side, light, overlay), openness);
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
        clipped(f, ia - IRIS, ib - IRIS, ia + IRIS, ib + IRIS, 0.05F, irisU / 32.0F, 8.0F / 32.0F, (irisU + 8.0F) / 32.0F, 16.0F / 32.0F, 0xFFFFFFFF);
        // Вертикальный зрачок — поперёк клинка. Блик даёт роговица.
        clipped(f, ia - pupil / 2.0F, ib - IRIS * 0.78F, ia + pupil / 2.0F, ib + IRIS * 0.78F, 0.07F,
                24.5F / 32.0F, 16.5F / 32.0F, 27.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);

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

    /**
     * Прямоугольник на глазу, обрезанный по миндалевидному краю (сеткой 8×8): радужка и зрачок при взгляде вбок не вылезают
     * за обод.
     */
    private static void clipped(Face f, float a0, float b0, float a1, float b1, float lift, float u0, float v0, float u1, float v1, int color) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float ca0 = MathHelper.lerp((float) i / n, a0, a1), ca1 = MathHelper.lerp((float) (i + 1) / n, a0, a1);
                float cb0 = MathHelper.lerp((float) j / n, b0, b1), cb1 = MathHelper.lerp((float) (j + 1) / n, b0, b1);
                float ca = (ca0 + ca1) / 2.0F, cb = (cb0 + cb1) / 2.0F;
                if (Math.abs(cb) > EYE_H || Math.abs(ca) > halfLength(cb) - 0.06F) {
                    continue;
                }
                // В Face.quad низ прямоугольника (b0) берёт v1 — поэтому v считаем от v1.
                f.quad(ca0, cb0, ca1, cb1, lift, MathHelper.lerp((float) i / n, u0, u1), MathHelper.lerp((float) (j + 1) / n, v1, v0),
                        MathHelper.lerp((float) (i + 1) / n, u0, u1), MathHelper.lerp((float) j / n, v1, v0), color);
            }
        }
    }

    /** Купол роговицы над всем глазом (сетка 6×6, ячейки за краем эллипса пропускаем). */
    private static void cornea(Face f, float openness) {
        int n = 6;
        float ra = EYE_W * 0.86F, rb = EYE_H * 0.9F * openness;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float x0 = -1 + 2.0F * i / n, x1 = -1 + 2.0F * (i + 1) / n, y0 = -1 + 2.0F * j / n, y1 = -1 + 2.0F * (j + 1) / n;
                float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
                if (cx * cx + cy * cy > 1.0F) {
                    continue;
                }
                f.domeVertex(x0, y0, ra, rb);
                f.domeVertex(x1, y0, ra, rb);
                f.domeVertex(x1, y1, ra, rb);
                f.domeVertex(x0, y1, ra, rb);
            }
        }
    }

    private static void lid(Face f, float a0, float b0, float a1, float b1, boolean lashes) {
        f.box(a0, b0, a1, b1, 0.2F, 0.0F, 16.0F, 16.0F, lashes ? 17.0F : 20.0F);
        if (lashes) {
            f.quad(a0, b0 + (b1 - b0) * 0.35F, a1, b0 + (b1 - b0) * 0.65F, 0.205F, 24.5F / 32.0F, 16.5F / 32.0F, 27.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);
        }
    }

    // ---------------------------------------------------------------- вывод вершин

    /** Сетка меча: четырёхугольники в координатах меча, UV в пикселях атласа 512×512, нормаль по подсказке. */
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
            this.vertices.vertex(this.entry.getPositionMatrix(), p.x, p.y, p.z).color(-1).texture(tu / ATLAS, tv / ATLAS)
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

        /** Вершина купола роговицы: (x, y) — в долях полуосей глаза, высота купола — до 0,22 над белком. */
        void domeVertex(float x, float y, float ra, float rb) {
            float h = 0.1F + 0.2F * Math.max(0.0F, 1.0F - x * x - y * y);
            float tu = (24.0F + 4.0F + x * 4.0F) / 32.0F, tv = (8.0F + 4.0F - y * 4.0F) / 32.0F;
            this.vertex(this.at(x * ra, y * rb, h), tu, tv, 0xFFFFFFFF, new Vector3f(0.0F, 0.0F, this.side));
        }

        private void vertex(Vector3f p, float u, float v, int color, Vector3f n) {
            this.vertices.vertex(this.entry.getPositionMatrix(), p.x, p.y, p.z).color(color).texture(u, v).overlay(this.overlay)
                    .light(this.light).normal(this.entry, n.x, n.y, n.z);
        }
    }
}
