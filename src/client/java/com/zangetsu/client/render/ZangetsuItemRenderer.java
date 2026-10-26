package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Объёмный Зангецу. Клинок собран из слоёв разной толщины (рукоять и обух толще, кромка — тонкая
 * фаска), а на обеих сторонах клинка сидит 3D-глаз: выпуклый обод, тёмный белок, жёлтая радужка
 * с вертикальным зрачком и веки. Глаз смотрит на ближайшего злого моба или на тебя, моргает,
 * сердито щурится и горит в форме Пустого.
 */
public class ZangetsuItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    public static final Identifier HILT = ZangetsuMod.id("item/zangetsu_hilt");
    public static final Identifier SPINE = ZangetsuMod.id("item/zangetsu_spine");
    public static final Identifier CORE = ZangetsuMod.id("item/zangetsu_core");
    public static final Identifier CORE_BURNING = ZangetsuMod.id("item/zangetsu_core_burning");
    public static final Identifier EDGE = ZangetsuMod.id("item/zangetsu_edge");
    public static final Identifier[] MODELS = {HILT, SPINE, CORE, CORE_BURNING, EDGE};

    private static final Identifier EYE_TEXTURE = ZangetsuMod.id("textures/entity/zangetsu_eye.png");
    /** Толщина слоёв в пикселях (обычный предмет — 1). */
    private static final float HILT_DEPTH = 2.2F;
    private static final float SPINE_DEPTH = 2.0F;
    private static final float CORE_DEPTH = 1.5F;
    private static final float EDGE_DEPTH = 0.55F;

    /** Центр глаза (в пикселях модели 0..16) — там же, где гнездо на текстуре клинка. */
    private static final float EYE_X = 7.93F;
    private static final float EYE_Y = 7.65F;
    private static final float EYE_W = 1.85F;
    private static final float EYE_H = 1.1F;
    private static final int ROWS = 9;
    private static final float RIM = 0.45F;
    private static final float RIM_HEIGHT = 0.3F;
    private static final Direction[] FACES = {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

    private final Random random = Random.create();

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        int eye = stack.getOrDefault(ModComponents.EYE, Eye.CLOSED);
        VertexConsumer blade = consumers.getBuffer(TexturedRenderLayers.getEntityCutout());
        layer(client.getBakedModelManager().getModel(HILT), HILT_DEPTH, matrices, blade, light, overlay);
        layer(client.getBakedModelManager().getModel(SPINE), SPINE_DEPTH, matrices, blade, light, overlay);
        layer(client.getBakedModelManager().getModel(eye == Eye.BURNING ? CORE_BURNING : CORE), CORE_DEPTH, matrices, blade, light, overlay);
        layer(client.getBakedModelManager().getModel(EDGE), EDGE_DEPTH, matrices, blade, light, overlay);
        renderEye(eye, mode, matrices, consumers, light, overlay);
    }

    /** Плоский предметный слой, выдавленный на нужную толщину вокруг середины. */
    private void layer(BakedModel model, float depth, MatrixStack matrices, VertexConsumer vertices, int light, int overlay) {
        if (model == null) {
            return;
        }
        matrices.push();
        matrices.translate(0.0F, 0.0F, 0.5F);
        matrices.scale(1.0F, 1.0F, depth);
        matrices.translate(0.0F, 0.0F, -0.5F);
        MatrixStack.Entry entry = matrices.peek();
        for (Direction face : FACES) {
            this.random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, face, this.random)) {
                vertices.quad(entry, quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
            }
        }
        matrices.pop();
    }

    // ---------------------------------------------------------------- глаз

    private void renderEye(int state, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        float surface = 8.0F + CORE_DEPTH / 2.0F;
        Matrix4f model = matrices.peek().getPositionMatrix();
        float[] gaze = EyeTracker.gaze(mode, model, new Vector3f(EYE_X / 16.0F, EYE_Y / 16.0F, surface / 16.0F));
        float openness = switch (state) {
            case Eye.OPEN -> 1.0F;
            case Eye.BURNING -> 1.0F;
            case Eye.ANGRY -> 0.55F;
            default -> 0.0F;
        };
        openness = EyeTracker.blink(mode, openness);
        // Зрачок: шире в темноте, тоньше, когда глаз горит или злится.
        float pupil = state == Eye.BURNING ? 0.14F : (state == Eye.ANGRY ? 0.12F : 0.24F);
        float irisU = state == Eye.BURNING ? 8.0F : (state == Eye.ANGRY ? 16.0F : 0.0F);
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(EYE_TEXTURE));
        for (int side = 0; side < 2; side++) {
            boolean front = side == 0;
            Face f = new Face(matrices.peek(), vertices, front, surface, light, overlay);
            eyeSide(f, gaze, openness, state == Eye.ANGRY, pupil, irisU);
            if (state == Eye.OPEN || state == Eye.BURNING) {
                // Радужка светится в темноте.
                Face glow = new Face(matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(EYE_TEXTURE)), front, surface,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay);
                glow.quad(EYE_X + gaze[0] - 0.75F, EYE_Y + gaze[1] - 0.75F, EYE_X + gaze[0] + 0.75F, EYE_Y + gaze[1] + 0.75F, 0.06F,
                        irisU / 32.0F, 8.0F / 32.0F, (irisU + 8.0F) / 32.0F, 16.0F / 32.0F, state == Eye.BURNING ? 0xFFFFFFFF : 0xFF9A7A3A);
            }
        }
    }

    private static float halfWidth(float y) {
        float t = MathHelper.clamp(y / EYE_H, -1.0F, 1.0F);
        return EYE_W * (float) Math.pow(1.0F - t * t, 0.8F);
    }

    /** Одна сторона глаза: белок, радужка, зрачок, блик — и поверх обод с веками. */
    private static void eyeSide(Face f, float[] gaze, float openness, boolean angry, float pupil, float irisU) {
        float rowH = 2.0F * EYE_H / ROWS;
        float ix = EYE_X + gaze[0], iy = EYE_Y + gaze[1];
        // Белок — строками по миндалине.
        for (int r = 0; r < ROWS; r++) {
            float y0 = -EYE_H + r * rowH, y1 = y0 + rowH, yc = (y0 + y1) / 2.0F;
            float hw = halfWidth(yc) + 0.05F;
            f.quad(EYE_X - hw, EYE_Y + y0, EYE_X + hw, EYE_Y + y1, 0.02F,
                    (EYE_W - hw) / (2 * EYE_W) * 16.0F / 32.0F, (EYE_H - y1) / (2 * EYE_H) * 8.0F / 32.0F,
                    (EYE_W + hw) / (2 * EYE_W) * 16.0F / 32.0F, (EYE_H - y0) / (2 * EYE_H) * 8.0F / 32.0F, 0xFFFFFFFF);
        }
        f.quad(ix - 0.75F, iy - 0.75F, ix + 0.75F, iy + 0.75F, 0.05F,
                irisU / 32.0F, 8.0F / 32.0F, (irisU + 8.0F) / 32.0F, 16.0F / 32.0F, 0xFFFFFFFF);
        f.quad(ix - pupil / 2.0F, iy - 0.58F, ix + pupil / 2.0F, iy + 0.58F, 0.07F, 24.5F / 32.0F, 16.5F / 32.0F, 27.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);
        f.quad(ix - 0.42F, iy + 0.22F, ix - 0.2F, iy + 0.44F, 0.08F, 28.5F / 32.0F, 16.5F / 32.0F, 31.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);

        // Веки: верхнее опускается, нижнее поднимается; у сердитого глаза верхнее скошено.
        float meet = -0.15F;
        float upper = MathHelper.lerp(openness, meet, EYE_H);
        float lower = MathHelper.lerp(openness, meet, -EYE_H);
        float slope = angry ? 0.28F : 0.0F;
        for (int r = 0; r < ROWS; r++) {
            float y0 = -EYE_H + r * rowH, y1 = y0 + rowH, yc = (y0 + y1) / 2.0F;
            float hw = halfWidth(yc);
            // Обод вокруг отверстия.
            f.box(EYE_X - hw - RIM, EYE_Y + y0, EYE_X - hw, EYE_Y + y1, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
            f.box(EYE_X + hw, EYE_Y + y0, EYE_X + hw + RIM, EYE_Y + y1, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
            // Где строка закрыта веком — веко (у кромки — тёмные ресницы).
            float visibleFrom = -hw, visibleTo = hw;
            if (yc < lower) {
                visibleFrom = visibleTo;
            } else if (slope == 0.0F) {
                if (yc > upper) {
                    visibleFrom = visibleTo;
                }
            } else {
                // Верхнее веко — наклонная линия: upper + slope·x/W. Видно там, где строка ниже линии.
                float cut = (yc - upper) / slope * EYE_W;
                visibleFrom = Math.max(visibleFrom, cut);
            }
            if (visibleFrom >= visibleTo) {
                lid(f, EYE_X - hw, EYE_Y + y0, EYE_X + hw, EYE_Y + y1, Math.abs(yc - upper) < rowH || Math.abs(yc - lower) < rowH);
            } else if (visibleFrom > -hw) {
                lid(f, EYE_X - hw, EYE_Y + y0, EYE_X + visibleFrom, EYE_Y + y1, true);
            }
        }
        // Верх и низ обода — сплошные полосы.
        f.box(EYE_X - EYE_W - RIM, EYE_Y + EYE_H, EYE_X + EYE_W + RIM, EYE_Y + EYE_H + RIM * 0.8F, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
        f.box(EYE_X - EYE_W - RIM, EYE_Y - EYE_H - RIM * 0.8F, EYE_X + EYE_W + RIM, EYE_Y - EYE_H, RIM_HEIGHT, 16.0F, 16.0F, 24.0F, 20.0F);
    }

    private static void lid(Face f, float x0, float y0, float x1, float y1, boolean lashes) {
        f.box(x0, y0, x1, y1, 0.22F, 0.0F, 16.0F, 16.0F, lashes ? 17.0F : 20.0F);
        if (lashes) {
            f.quad(x0, y0 + (y1 - y0) * 0.35F, x1, y0 + (y1 - y0) * 0.65F, 0.225F, 24.5F / 32.0F, 16.5F / 32.0F, 27.5F / 32.0F, 19.5F / 32.0F, 0xFFFFFFFF);
        }
    }

    /** Рисование на одной стороне клинка: z отсчитывается от поверхности наружу. */
    private record Face(MatrixStack.Entry entry, VertexConsumer vertices, boolean front, float surface, int light, int overlay) {
        float z(float lift) {
            return (this.front ? this.surface + lift : 16.0F - this.surface - lift) / 16.0F;
        }

        void quad(float x0, float y0, float x1, float y1, float lift, float u0, float v0, float u1, float v1, int color) {
            float z = this.z(lift);
            float nz = this.front ? 1.0F : -1.0F;
            this.vertex(x0, y0, z, u0, v1, color, 0, 0, nz);
            this.vertex(x1, y0, z, u1, v1, color, 0, 0, nz);
            this.vertex(x1, y1, z, u1, v0, color, 0, 0, nz);
            this.vertex(x0, y1, z, u0, v0, color, 0, 0, nz);
        }

        /** Выпуклый брусок высотой {@code lift} над поверхностью: верх и четыре бока. UV — в пикселях текстуры 32×32. */
        void box(float x0, float y0, float x1, float y1, float lift, float tu0, float tv0, float tu1, float tv1) {
            float u0 = tu0 / 32.0F, v0 = tv0 / 32.0F, u1 = tu1 / 32.0F, v1 = tv1 / 32.0F;
            this.quad(x0, y0, x1, y1, lift, u0, v0, u1, v1, 0xFFFFFFFF);
            float zb = this.z(0.0F), zt = this.z(lift);
            int shade = 0xFFB0B0B0;
            // Бока.
            this.side(x0, y0, x1, y0, zb, zt, u0, v0, u1, v1, shade, 0, -1);
            this.side(x1, y1, x0, y1, zb, zt, u0, v0, u1, v1, shade, 0, 1);
            this.side(x0, y1, x0, y0, zb, zt, u0, v0, u1, v1, shade, -1, 0);
            this.side(x1, y0, x1, y1, zb, zt, u0, v0, u1, v1, shade, 1, 0);
        }

        private void side(float xa, float ya, float xb, float yb, float zb, float zt, float u0, float v0, float u1, float v1,
                int color, int nx, int ny) {
            this.vertex(xa, ya, zb, u0, v1, color, nx, ny, 0);
            this.vertex(xb, yb, zb, u1, v1, color, nx, ny, 0);
            this.vertex(xb, yb, zt, u1, v0, color, nx, ny, 0);
            this.vertex(xa, ya, zt, u0, v0, color, nx, ny, 0);
        }

        private void vertex(float x, float y, float z, float u, float v, int color, float nx, float ny, float nz) {
            Matrix4f m = this.entry.getPositionMatrix();
            this.vertices.vertex(m, x / 16.0F, y / 16.0F, z).color(color).texture(u, v).overlay(this.overlay).light(this.light)
                    .normal(this.entry, nx, ny, nz);
        }
    }
}
