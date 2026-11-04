package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Eye;
import com.zangetsu.logic.SwordBreak;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import java.util.Random;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Сцена, где Яхве ломает меч: сам Зангецу, повисший в воздухе, и длинная рука с настоящими пальцами. Рука из гладких объёмных суставов: ладонь ложится за клинок, пальцы заворачивают через обух на сторону
 * игрока, большой палец через лезвие. Клинок покрывается светящимися трещинами и разлетается осколками, которые будто застывают в воздухе.
 *
 * <p>Всё считается в осях меча: x — поперёк клинка (лезвие в минусе, обух в плюсе), y — вверх вдоль клинка, z — к игроку.
 */
final class YhwachHand {
    private static final Identifier WHITE = ZangetsuMod.id("textures/misc/white.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    /** Пикселей модели в блоке при масштабе меча: те же 1.5, что и у воткнутого. */
    private static final float SWORD_SCALE = 1.5F, PX = SWORD_SCALE / 16.0F;
    /** Где рука хватает клинок (u модели, в пикселях). */
    private static final float GRIP_U = 13.0F, MODEL_GRIP = 3.1F;
    private static final float GRIP_Y = (GRIP_U - MODEL_GRIP) * PX;
    /** Размер кисти: она чуть больше человеческой, но клинок обхватывает как надо. */
    private static final float H = 1.2F;
    private static final float[] KNUCKLE_Y = {-0.135F, -0.045F, 0.045F, 0.135F};
    private static final float[] FINGER = {0.86F, 1.0F, 1.05F, 0.95F};

    private static final ItemStack SWORD = new ItemStack(ModItems.ZANGETSU);

    private static final float[][] CRACKS = buildCracks();
    private static final float[][] SHARDS = buildShards();

    private YhwachHand() {
    }

    static {
        SWORD.set(ModComponents.EYE, Eye.OPEN);
    }

    private static float ease(float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float span(float a, float b, float x) {
        return ease((x - a) / (b - a));
    }

    // ---------------------------------------------------------------- трещины и осколки

    /** Ломаные от места хвата по всему клинку: (u, v) через одну, в порядке роста. */
    private static float[][] buildCracks() {
        Random random = new Random(404L);
        float[][] all = new float[26][];
        for (int i = 0; i < all.length; i++) {
            boolean branch = i >= 12;
            float angle = branch ? random.nextFloat() * MathHelper.TAU : MathHelper.TAU * (i + random.nextFloat() * 0.5F) / 12.0F;
            float u = GRIP_U + (branch ? (random.nextFloat() - 0.5F) * 10.0F : (random.nextFloat() - 0.5F) * 0.6F);
            float v = branch ? (random.nextFloat() - 0.5F) * 3.6F : 0.2F + (random.nextFloat() - 0.5F) * 0.6F;
            int n = branch ? 4 : 8;
            float[] line = new float[n * 2];
            for (int k = 0; k < n; k++) {
                line[k * 2] = u;
                line[k * 2 + 1] = v;
                float step = 0.5F + random.nextFloat() * 1.1F;
                angle += (random.nextFloat() - 0.5F) * 1.3F;
                u = MathHelper.clamp(u + MathHelper.cos(angle) * step, 6.5F, 20.8F);
                v = MathHelper.clamp(v + MathHelper.sin(angle) * step * 0.55F, -2.2F, 2.6F);
            }
            all[i] = line;
        }
        return all;
    }

    /** Осколок: u, v, размер, направление разлёта (x, y, z), ось и скорость вращения. */
    private static float[][] buildShards() {
        Random random = new Random(909L);
        float[][] all = new float[46][];
        for (int i = 0; i < all.length; i++) {
            float u = 6.5F + random.nextFloat() * 14.5F, v = -2.2F + random.nextFloat() * 4.8F;
            float x = -v * PX, y = (u - MODEL_GRIP) * PX - GRIP_Y;
            float l = Math.max(0.05F, MathHelper.sqrt(x * x + y * y));
            float out = 0.4F + random.nextFloat() * 1.3F;
            all[i] = new float[]{u, v, 0.05F + random.nextFloat() * 0.13F, x / l * out, y / l * out, (random.nextFloat() - 0.2F) * 1.2F, random.nextFloat() * 6.28F, random.nextFloat() * 6.28F, 2.0F + random.nextFloat() * 5.0F};
        }
        return all;
    }

    // ---------------------------------------------------------------- кадр

    static void render(ZangetsuSpiritEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d camera, ItemRenderer items) {
        float t = entity.age + tickDelta;
        Vector3f anchor = entity.breakAnchor();
        float yaw = entity.victimYaw();
        matrices.push();
        matrices.translate(anchor.x, anchor.y, anchor.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        // Камера в осях меча: для контуров.
        float rad = (180.0F - yaw) * MathHelper.RADIANS_PER_DEGREE, cos = MathHelper.cos(rad), sin = MathHelper.sin(rad);
        Vec3d rel = camera.subtract(entity.getLerpedPos(tickDelta)).subtract(anchor.x, anchor.y, anchor.z);
        float camX = (float) (rel.x * cos - rel.z * sin), camY = (float) rel.y, camZ = (float) (rel.x * sin + rel.z * cos);
        // Фигура в тех же осях: оттуда идёт рука.
        float rootX = -(anchor.x * cos - anchor.z * sin), rootY = -anchor.y - 1.1F, rootZ = -(anchor.x * sin + anchor.z * cos) - 1.3F;

        // Рука и сжатие.
        float reach = span(SwordBreak.ARM_FROM, SwordBreak.GRIP, t) * (1.0F - span(SwordBreak.RELEASE, SwordBreak.FIGURE_OUT + 6.0F, t));
        float curl = span(SwordBreak.GRIP - 9.0F, SwordBreak.GRIP + 7.0F, t) * (1.0F - span(SwordBreak.RELEASE, SwordBreak.RELEASE + 12.0F, t) * 0.75F)
                + span(SwordBreak.CRUSH, SwordBreak.SNAP, t) * 0.12F + span(SwordBreak.SNAP, SwordBreak.SNAP + 3.0F, t) * (1.0F - span(SwordBreak.RELEASE, SwordBreak.RELEASE + 10.0F, t)) * 0.45F;
        float crushing = span(SwordBreak.GRIP + 4.0F, SwordBreak.SNAP, t) * (1.0F - span(SwordBreak.SNAP, SwordBreak.SNAP + 2.0F, t));
        float tremble = crushing * 0.012F;
        float shakeX = MathHelper.sin(t * 4.3F) * tremble, shakeY = MathHelper.cos(t * 5.1F) * tremble;

        boolean broken = t >= SwordBreak.SNAP;
        // Меч всплывает, чуть покачивается, а в руке только дрожит.
        float rise = span(SwordBreak.SWORD_FROM, SwordBreak.SWORD_FROM + 24.0F, t);
        if (rise > 0.01F) {
            float idle = 1.0F - span(SwordBreak.GRIP - 6.0F, SwordBreak.GRIP, t);
            float swordY = -0.5F * (1.0F - rise) + MathHelper.sin(t * 0.09F) * 0.03F * idle + shakeY;
            matrices.push();
            matrices.translate(shakeX, swordY, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.sin(t * 0.06F) * 7.0F * idle));
            if (!broken) {
                drawSword(entity, matrices, consumers, items, rise, false, t);
                cracks(consumers, matrices, span(SwordBreak.GRIP + 1.0F, SwordBreak.SNAP, t));
            } else {
                // Рукоять падает и исчезает.
                float since = t - SwordBreak.SNAP;
                matrices.translate(0.0F, -0.0035F * since * since, 0.0F);
                float gone = 1.0F - span(30.0F, 55.0F, since);
                if (gone > 0.01F) {
                    drawSword(entity, matrices, consumers, items, gone, true, t);
                }
            }
            matrices.pop();
        }
        if (broken) {
            shards(consumers, matrices, t - SwordBreak.SNAP, camX, camY, camZ);
            shockwave(consumers, matrices, t - SwordBreak.SNAP);
        }
        if (reach > 0.005F) {
            arm(consumers, matrices, reach, curl, shakeX, GRIP_Y + shakeY, rootX, rootY, rootZ, camX, camY, camZ);
        }
        matrices.pop();
    }

    private static void drawSword(ZangetsuSpiritEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, ItemRenderer items, float size, boolean hiltOnly, float t) {
        matrices.push();
        // Картинка меча лежит по диагонали: остриё вверх.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45.0F));
        float s = SWORD_SCALE * size;
        matrices.scale(s, s, s);
        net.minecraft.entity.Entity previous = EyeTracker.push(entity);
        ZangetsuItemRenderer.hiltOnly = hiltOnly;
        items.renderItem(SWORD, ModelTransformationMode.NONE, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.getWorld(), entity.getId());
        ZangetsuItemRenderer.hiltOnly = false;
        EyeTracker.pop(previous);
        matrices.pop();
    }

    /** Светящиеся трещины с обеих сторон клинка: из точки хвата по всей его длине. */
    private static void cracks(VertexConsumerProvider consumers, MatrixStack matrices, float progress) {
        if (progress <= 0.01F) {
            return;
        }
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        for (int c = 0; c < CRACKS.length; c++) {
            float[] line = CRACKS[c];
            float delay = c < 12 ? 0.0F : 0.25F + (c - 12) * 0.035F;
            float shown = MathHelper.clamp((progress - delay) / (1.0F - delay), 0.0F, 1.0F) * (line.length / 2 - 1);
            for (int k = 0; k < line.length / 2 - 1; k++) {
                float part = MathHelper.clamp(shown - k, 0.0F, 1.0F);
                if (part <= 0.0F) {
                    break;
                }
                float u0 = line[k * 2], v0 = line[k * 2 + 1];
                float u1 = MathHelper.lerp(part, u0, line[k * 2 + 2]), v1 = MathHelper.lerp(part, v0, line[k * 2 + 3]);
                for (int side = -1; side <= 1; side += 2) {
                    // Широкая красная кайма и тонкая белая сердцевина.
                    crackSegment(out, m, u0, v0, u1, v1, side, 0.16F, 255, 40, 30, 90);
                    crackSegment(out, m, u0, v0, u1, v1, side, 0.05F, 255, 240, 225, 255);
                }
            }
        }
    }

    private static void crackSegment(VertexConsumer out, Matrix4f m, float u0, float v0, float u1, float v1, int side, float width, int r, int g, int b, int a) {
        float du = u1 - u0, dv = v1 - v0;
        float l = Math.max(1.0E-3F, MathHelper.sqrt(du * du + dv * dv));
        float nu = -dv / l * width, nv = du / l * width;
        float w = side * 0.66F * PX;
        // Клинок здесь повёрнут: u вверх, v к минусу x.
        put(out, m, -(v0 + nv) * PX, (u0 + nu - MODEL_GRIP) * PX, w, r, g, b, a);
        put(out, m, -(v0 - nv) * PX, (u0 - nu - MODEL_GRIP) * PX, w, r, g, b, a);
        put(out, m, -(v1 - nv) * PX, (u1 - nu - MODEL_GRIP) * PX, w, r, g, b, a);
        put(out, m, -(v1 + nv) * PX, (u1 + nu - MODEL_GRIP) * PX, w, r, g, b, a);
    }

    private static void put(VertexConsumer out, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        out.vertex(m, x, y, z).color(r, g, b, a).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(0.0F, 1.0F, 0.0F);
    }

    /** Осколки: сначала рвутся наружу, потом будто застывают, вращаясь и поблёскивая, и медленно оседают. */
    private static void shards(VertexConsumerProvider consumers, MatrixStack matrices, float since, float camX, float camY, float camZ) {
        float fade = 1.0F - span(58.0F, 84.0F, since);
        if (fade <= 0.01F) {
            return;
        }
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        float e = 1.0F - (float) Math.exp(-since / 12.0F);
        for (float[] s : SHARDS) {
            float x0 = -s[1] * PX, y0 = (s[0] - MODEL_GRIP) * PX;
            float x = x0 + s[3] * e * 1.5F, y = y0 + s[4] * e * 1.5F - 0.0009F * since * since, z = s[5] * e * 1.5F;
            float size = s[2] * fade;
            float angle = s[6] + s[8] * e + since * 0.012F * s[8];
            float c = MathHelper.cos(angle), n = MathHelper.sin(angle), tilt = s[7] + since * 0.008F * s[8], ct = MathHelper.cos(tilt), st = MathHelper.sin(tilt);
            // Плоский зубчатый кусок, повёрнутый как угодно.
            float ax = c * size, ay = n * size * ct, az = n * size * st;
            float bx = -n * size * 0.7F, by = c * size * 0.7F * ct, bz = c * size * 0.7F * st;
            float gleam = 0.4F + 0.6F * Math.abs(ct * MathHelper.cos(angle * 0.5F + since * 0.05F));
            int edge = (int) (150.0F + 95.0F * gleam), dark = (int) (24.0F + 46.0F * gleam);
            int tone = s[1] > 1.0F ? edge : dark;
            put(out, m, x + ax, y + ay, z + az, tone, tone, tone + 6, 255);
            put(out, m, x + bx, y + by, z + bz, dark, dark, dark + 4, 255);
            put(out, m, x - ax * 0.8F + bx * 0.2F, y - ay * 0.8F + by * 0.2F, z - az * 0.8F + bz * 0.2F, tone, tone, tone + 6, 255);
            put(out, m, x - bx, y - by, z - bz, dark, dark, dark + 4, 255);
        }
    }

    /** Кольцо удара расходится от руки. */
    private static void shockwave(VertexConsumerProvider consumers, MatrixStack matrices, float since) {
        if (since > 16.0F) {
            return;
        }
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        float k = since / 16.0F, radius = 0.4F + ease(k) * 5.5F, width = 0.5F * (1.0F - k) + 0.05F;
        int alpha = (int) (210.0F * (1.0F - k));
        int sides = 40;
        for (int i = 0; i < sides; i++) {
            float a0 = MathHelper.TAU * i / sides, a1 = MathHelper.TAU * (i + 1) / sides;
            float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
            put(out, m, c0 * radius, GRIP_Y + s0 * radius, 0.0F, 255, 230, 225, alpha);
            put(out, m, c1 * radius, GRIP_Y + s1 * radius, 0.0F, 255, 230, 225, alpha);
            put(out, m, c1 * (radius + width), GRIP_Y + s1 * (radius + width), 0.0F, 255, 60, 50, 0);
            put(out, m, c0 * (radius + width), GRIP_Y + s0 * (radius + width), 0.0F, 255, 60, 50, 0);
        }
    }

    // ---------------------------------------------------------------- рука

    private static void arm(VertexConsumerProvider consumers, MatrixStack matrices, float reach, float curl, float shakeX, float gripY, float rootX, float rootY, float rootZ, float camX, float camY, float camZ) {
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        Matrix4f m = matrices.peek().getPositionMatrix();
        Solid solid = new Solid(out, m, camX, camY, camZ);

        // Кисть в своих координатах ещё не дошла: сдвинута назад, к фигуре.
        float left = 1.0F - reach;
        float ox = shakeX + rootX * left * 0.92F, oy = gripY + (rootY - gripY) * left * 0.92F, oz = rootZ * left * 0.92F;

        // Ладонь за клинком, запястье со стороны лезвия.
        float wristX = ox - 0.56F * H, wristY = oy - 0.03F * H, wristZ = oz - 0.2F * H;
        solid.color(SKIN);
        for (int i = 0; i < 4; i++) {
            float kx = ox + 0.10F * H, ky = oy + KNUCKLE_Y[i] * H, kz = oz - 0.115F * H;
            // Пястные кости сливаются в узкую сухую ладонь.
            solid.tube(wristX + 0.04F * H, wristY + KNUCKLE_Y[i] * H * 0.45F, wristZ, 0.058F * H, kx, ky, kz, 0.05F * H);
            solid.ball(kx, ky, kz, 0.052F * H);
            finger(solid, kx, ky, kz, FINGER[i], curl);
        }
        // Основание ладони.
        solid.tube(wristX, wristY - 0.1F * H, wristZ, 0.06F * H, wristX, wristY + 0.1F * H, wristZ, 0.06F * H);
        thumb(solid, ox, oy, oz, curl);

        // Предплечье: длинный рукав идёт дугой к самой фигуре.
        float midX = (wristX + rootX) * 0.5F, midY = (wristY + rootY) * 0.5F + 0.9F, midZ = (wristZ + rootZ) * 0.5F;
        solid.color(SLEEVE);
        int steps = 18;
        float[][] spine = new float[steps + 1][];
        for (int i = 0; i <= steps; i++) {
            float u = (float) i / steps, v = 1.0F - u;
            float x = v * v * wristX + 2.0F * v * u * midX + u * u * rootX;
            float y = v * v * wristY + 2.0F * v * u * midY + u * u * rootY;
            float z = v * v * wristZ + 2.0F * v * u * midZ + u * u * rootZ;
            spine[i] = new float[]{x, y, z, (0.13F + 0.2F * u * u) * H};
        }
        solid.sweep(spine);
        // Белый манжет у запястья, как воротник.
        solid.color(CUFF);
        solid.tube(wristX + 0.015F, wristY, wristZ, 0.105F * H, wristX - 0.1F * H, wristY, wristZ, 0.155F * H);
    }

    /** Палец заворачивается вокруг клинка: три фаланги, каждая сгибается сильнее, пока не ляжет на сторону игрока. */
    private static void finger(Solid solid, float x, float y, float z, float length, float curl) {
        float a1 = MathHelper.lerp(curl, -0.35F, 0.25F), a2 = a1 + MathHelper.lerp(curl, 0.3F, 1.45F), a3 = a2 + MathHelper.lerp(curl, 0.2F, 1.35F);
        float l1 = 0.205F * H * length, l2 = 0.15F * H * length, l3 = 0.125F * H * length;
        float x1 = x + MathHelper.cos(a1) * l1, z1 = z + MathHelper.sin(a1) * l1;
        float x2 = x1 + MathHelper.cos(a2) * l2, z2 = z1 + MathHelper.sin(a2) * l2;
        float x3 = x2 + MathHelper.cos(a3) * l3, z3 = z2 + MathHelper.sin(a3) * l3;
        solid.tube(x, y, z, 0.05F * H, x1, y, z1, 0.044F * H);
        solid.ball(x1, y, z1, 0.046F * H);
        solid.tube(x1, y, z1, 0.044F * H, x2, y, z2, 0.039F * H);
        solid.ball(x2, y, z2, 0.04F * H);
        solid.tube(x2, y, z2, 0.039F * H, x3, y, z3, 0.03F * H);
        solid.ball(x3, y, z3, 0.031F * H);
        // Тёмный ноготь на кончике.
        solid.color(NAIL);
        float nx = x3 - MathHelper.sin(a3) * 0.012F * H * -1.0F, nz = z3 + MathHelper.cos(a3) * 0.012F * H;
        solid.ball(nx, y, nz, 0.021F * H);
        solid.color(SKIN);
    }

    /** Большой палец: с другой стороны, через лезвие. */
    private static void thumb(Solid solid, float ox, float oy, float oz, float curl) {
        float x0 = ox - 0.44F * H, y0 = oy - 0.17F * H, z0 = oz - 0.14F * H;
        float x1 = x0 + MathHelper.lerp(curl, -0.02F, 0.06F) * H, y1 = y0 + 0.02F * H, z1 = z0 + MathHelper.lerp(curl, 0.04F, 0.11F) * H;
        float x2 = x1 + MathHelper.lerp(curl, 0.0F, 0.05F) * H, y2 = y1 + 0.04F * H, z2 = z1 + MathHelper.lerp(curl, 0.06F, 0.13F) * H;
        float x3 = x2 + MathHelper.lerp(curl, 0.04F, 0.15F) * H, y3 = y2 + 0.05F * H, z3 = z2 + MathHelper.lerp(curl, 0.04F, 0.015F) * H;
        solid.tube(x0, y0, z0, 0.062F * H, x1, y1, z1, 0.054F * H);
        solid.ball(x1, y1, z1, 0.054F * H);
        solid.tube(x1, y1, z1, 0.054F * H, x2, y2, z2, 0.046F * H);
        solid.ball(x2, y2, z2, 0.046F * H);
        solid.tube(x2, y2, z2, 0.046F * H, x3, y3, z3, 0.034F * H);
        solid.ball(x3, y3, z3, 0.035F * H);
    }

    private static final int SKIN = 0, SLEEVE = 1, CUFF = 2, NAIL = 3;

    /** Гладкие объёмные детали со своим светом: как у запечённого лица, с тёмным контуром там, где поверхность уходит от камеры. */
    private static final class Solid {
        private static final int SIDES = 10;
        private final VertexConsumer out;
        private final Matrix4f m;
        private final float camX, camY, camZ;
        private int material;

        Solid(VertexConsumer out, Matrix4f m, float camX, float camY, float camZ) {
            this.out = out;
            this.m = m;
            this.camX = camX;
            this.camY = camY;
            this.camZ = camZ;
        }

        void color(int material) {
            this.material = material;
        }

        private void vertex(float x, float y, float z, float nx, float ny, float nz) {
            float vx = this.camX - x, vy = this.camY - y, vz = this.camZ - z;
            float vl = Math.max(1.0E-3F, MathHelper.sqrt(vx * vx + vy * vy + vz * vz));
            float facing = Math.abs(nx * vx + ny * vy + nz * vz) / vl;
            float light = Math.max(0.0F, nx * 0.46F + ny * 0.62F + nz * 0.635F);
            float ink = MathHelper.clamp((facing - 0.08F) / 0.3F, 0.0F, 1.0F);
            float k1 = MathHelper.clamp((light - 0.08F) / 0.34F, 0.0F, 1.0F), k2 = MathHelper.clamp((light - 0.42F) / 0.5F, 0.0F, 1.0F);
            float r, g, b;
            switch (this.material) {
                case SKIN -> {
                    r = 86 + 100 * k1 + 54 * k2;
                    g = 64 + 80 * k1 + 60 * k2;
                    b = 104 + 26 * k1 + 50 * k2;
                }
                case SLEEVE -> {
                    r = 7 + 19 * k1 + 48 * k2;
                    g = 5 + 8 * k1 + 21 * k2;
                    b = 8 + 9 * k1 + 23 * k2;
                }
                case CUFF -> {
                    r = 104 + 110 * k1 + 38 * k2;
                    g = 104 + 112 * k1 + 36 * k2;
                    b = 170 + 68 * k1 + 17 * k2;
                }
                default -> {
                    r = 20;
                    g = 14;
                    b = 20;
                }
            }
            float dim = 0.25F + 0.75F * ink;
            put(this.out, this.m, x, y, z, (int) (r * dim), (int) (g * dim), (int) (b * dim), 255);
        }

        /** Сужающаяся труба между двумя точками. */
        void tube(float ax, float ay, float az, float ar, float bx, float by, float bz, float br) {
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float l = Math.max(1.0E-4F, MathHelper.sqrt(dx * dx + dy * dy + dz * dz));
            dx /= l;
            dy /= l;
            dz /= l;
            // Две оси сечения.
            float sx = dy, sy = -dx, sz = 0.0F;
            if (sx * sx + sy * sy < 1.0E-3F) {
                sx = 1.0F;
                sy = 0.0F;
            }
            float sl = MathHelper.sqrt(sx * sx + sy * sy + sz * sz);
            sx /= sl;
            sy /= sl;
            sz /= sl;
            float tx = dy * sz - dz * sy, ty = dz * sx - dx * sz, tz = dx * sy - dy * sx;
            for (int i = 0; i < SIDES; i++) {
                float a0 = MathHelper.TAU * i / SIDES, a1 = MathHelper.TAU * (i + 1) / SIDES;
                float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
                float n0x = sx * c0 + tx * s0, n0y = sy * c0 + ty * s0, n0z = sz * c0 + tz * s0;
                float n1x = sx * c1 + tx * s1, n1y = sy * c1 + ty * s1, n1z = sz * c1 + tz * s1;
                this.vertex(ax + n0x * ar, ay + n0y * ar, az + n0z * ar, n0x, n0y, n0z);
                this.vertex(ax + n1x * ar, ay + n1y * ar, az + n1z * ar, n1x, n1y, n1z);
                this.vertex(bx + n1x * br, by + n1y * br, bz + n1z * br, n1x, n1y, n1z);
                this.vertex(bx + n0x * br, by + n0y * br, bz + n0z * br, n0x, n0y, n0z);
            }
        }

        /** Гладкий чулок вдоль линии: сечения не проворачиваются относительно друг друга, поэтому швов нет. */
        void sweep(float[][] spine) {
            int sides = 14;
            float sx = 0.0F, sy = 0.0F, sz = 1.0F;
            float[] previous = null, previousNormals = null;
            for (int i = 0; i < spine.length; i++) {
                float[] from = spine[Math.max(0, i - 1)], to = spine[Math.min(spine.length - 1, i + 1)];
                float dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
                float l = Math.max(1.0E-4F, MathHelper.sqrt(dx * dx + dy * dy + dz * dz));
                dx /= l;
                dy /= l;
                dz /= l;
                // Прошлая ось, спроецированная на новое сечение.
                float dot = sx * dx + sy * dy + sz * dz;
                sx -= dx * dot;
                sy -= dy * dot;
                sz -= dz * dot;
                float sl = MathHelper.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1.0E-3F) {
                    sx = dy;
                    sy = -dx;
                    sz = 0.0F;
                    sl = Math.max(1.0E-3F, MathHelper.sqrt(sx * sx + sy * sy));
                }
                sx /= sl;
                sy /= sl;
                sz /= sl;
                float tx = dy * sz - dz * sy, ty = dz * sx - dx * sz, tz = dx * sy - dy * sx;
                float[] ring = new float[(sides + 1) * 3], normals = new float[(sides + 1) * 3];
                for (int j = 0; j <= sides; j++) {
                    float a = MathHelper.TAU * j / sides, c = MathHelper.cos(a), n = MathHelper.sin(a);
                    float nx = sx * c + tx * n, ny = sy * c + ty * n, nz = sz * c + tz * n;
                    normals[j * 3] = nx;
                    normals[j * 3 + 1] = ny;
                    normals[j * 3 + 2] = nz;
                    ring[j * 3] = spine[i][0] + nx * spine[i][3];
                    ring[j * 3 + 1] = spine[i][1] + ny * spine[i][3];
                    ring[j * 3 + 2] = spine[i][2] + nz * spine[i][3];
                }
                if (previous != null) {
                    for (int j = 0; j < sides; j++) {
                        this.vertex(previous[j * 3], previous[j * 3 + 1], previous[j * 3 + 2], previousNormals[j * 3], previousNormals[j * 3 + 1], previousNormals[j * 3 + 2]);
                        this.vertex(previous[j * 3 + 3], previous[j * 3 + 4], previous[j * 3 + 5], previousNormals[j * 3 + 3], previousNormals[j * 3 + 4], previousNormals[j * 3 + 5]);
                        this.vertex(ring[j * 3 + 3], ring[j * 3 + 4], ring[j * 3 + 5], normals[j * 3 + 3], normals[j * 3 + 4], normals[j * 3 + 5]);
                        this.vertex(ring[j * 3], ring[j * 3 + 1], ring[j * 3 + 2], normals[j * 3], normals[j * 3 + 1], normals[j * 3 + 2]);
                    }
                }
                previous = ring;
                previousNormals = normals;
            }
        }

        /** Шар сустава: закрывает стык двух труб. */
        void ball(float x, float y, float z, float r) {
            int rings = 5, sides = 8;
            for (int i = 0; i < rings; i++) {
                float t0 = MathHelper.PI * i / rings, t1 = MathHelper.PI * (i + 1) / rings;
                float st0 = MathHelper.sin(t0), ct0 = MathHelper.cos(t0), st1 = MathHelper.sin(t1), ct1 = MathHelper.cos(t1);
                for (int j = 0; j < sides; j++) {
                    float f0 = MathHelper.TAU * j / sides, f1 = MathHelper.TAU * (j + 1) / sides;
                    float c0 = MathHelper.cos(f0), s0 = MathHelper.sin(f0), c1 = MathHelper.cos(f1), s1 = MathHelper.sin(f1);
                    this.vertex(x + st0 * c0 * r, y + ct0 * r, z + st0 * s0 * r, st0 * c0, ct0, st0 * s0);
                    this.vertex(x + st0 * c1 * r, y + ct0 * r, z + st0 * s1 * r, st0 * c1, ct0, st0 * s1);
                    this.vertex(x + st1 * c1 * r, y + ct1 * r, z + st1 * s1 * r, st1 * c1, ct1, st1 * s1);
                    this.vertex(x + st1 * c0 * r, y + ct1 * r, z + st1 * s0 * r, st1 * c0, ct1, st1 * s0);
                }
            }
        }
    }
}
