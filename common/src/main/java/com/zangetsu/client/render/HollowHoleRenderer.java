package com.zangetsu.client.render;

import com.zangetsu.client.RampageScene;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Дыра в груди Пустого: идеально круглая, сквозная, сквозь неё видно то, что за спиной. Обычное туловище в этот кадр спрятано (см. BipedEntityModelMixin), а здесь рисуется такое же, со скином игрока,
 * только передняя и задняя грани огибают круглое отверстие. Внутри тёмные стенки, по краю тонкая алая кайма.
 */
public class HollowHoleRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** Где середина дыры на туловище и её радиус (пиксели модели). */
    private static final float CENTER_X = 0.0F, CENTER_Y = 4.7F, RADIUS = 2.55F;
    private static final float[][] EDGE = edge();

    public HollowHoleRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    /** Точки по краю передней грани: и все четыре угла. Кольцо вокруг дыры строится лучами к ним. */
    private static float[][] edge() {
        int perSide = 32;
        float[][] out = new float[perSide * 4][];
        for (int i = 0; i < perSide; i++) {
            float k = (float) i / perSide;
            out[i] = new float[]{MathHelper.lerp(k, -4.0F, 4.0F), 0.0F};
            out[perSide + i] = new float[]{4.0F, MathHelper.lerp(k, 0.0F, 12.0F)};
            out[perSide * 2 + i] = new float[]{MathHelper.lerp(k, 4.0F, -4.0F), 12.0F};
            out[perSide * 3 + i] = new float[]{-4.0F, MathHelper.lerp(k, 12.0F, 0.0F)};
        }
        return out;
    }

    /** Точка круга на том же луче, что и точка края грани. */
    private static float[] ring(float[] edge, float open) {
        float angle = (float) Math.atan2(edge[1] - CENTER_Y, edge[0] - CENTER_X);
        float r = RADIUS * open;
        return new float[]{CENTER_X + (float) Math.cos(angle) * r, CENTER_Y + (float) Math.sin(angle) * r};
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        float open = RampageScene.hole(player, animationProgress);
        if (open <= 0.0F || player.isInvisible()) {
            return;
        }
        PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
        Identifier skin = player.getSkinTextures().texture();
        int overlay = LivingEntityRenderer.getOverlay(player, 0.0F);
        matrices.push();
        model.body.rotate(matrices);
        MatrixStack.Entry entry = matrices.peek();
        shell(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(skin)), entry, open, 0.0F, 0, light, overlay);
        shell(consumers.getBuffer(RenderLayer.getEntityTranslucent(skin)), entry, open, 0.25F, 16, light, overlay);
        VertexConsumer walls = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Flames.WHITE));
        tube(walls, entry, open, light, overlay);
        VertexConsumer glow = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(Flames.WHITE));
        rim(glow, entry, open, -2.05F, overlay, animationProgress);
        rim(glow, entry, open, 2.05F, overlay, animationProgress);
        matrices.pop();
    }

    /** Туловище скина: те же грани и та же развёртка, что у обычного, но передняя и задняя с дырой. {@code grow} и {@code rowV} — для слоя куртки поверх. */
    private static void shell(VertexConsumer out, MatrixStack.Entry e, float open, float grow, int rowV, int light, int overlay) {
        float sx = (4.0F + grow) / 4.0F, sy = (6.0F + grow) / 6.0F, front = -2.0F - grow, back = 2.0F + grow;
        for (int i = 0; i < EDGE.length; i++) {
            float[] a = EDGE[i], b = EDGE[(i + 1) % EDGE.length], ra = ring(a, open), rb = ring(b, open);
            // Передняя: x от -4 к 4 это u от 20 к 28. Задняя зеркальна: u от 40 к 32.
            quadUv(out, e, ra, a, b, rb, sx, sy, front, 0.0F, 0.0F, -1.0F, 20.0F, 1.0F, 20.0F + rowV, light, overlay);
            quadUv(out, e, rb, b, a, ra, sx, sy, back, 0.0F, 0.0F, 1.0F, 40.0F, -1.0F, 20.0F + rowV, light, overlay);
        }
        float x0 = -4.0F - grow, x1 = 4.0F + grow, y0 = -grow, y1 = 12.0F + grow;
        // Боковые, верх и низ как в кубе модели.
        side(out, e, x0, y0, front, x0, y0, back, x0, y1, back, x0, y1, front, 20.0F, 20.0F + rowV, 16.0F, 20.0F + rowV, 16.0F, 32.0F + rowV, 20.0F, 32.0F + rowV, -1.0F, 0.0F, 0.0F, light, overlay);
        side(out, e, x1, y0, back, x1, y0, front, x1, y1, front, x1, y1, back, 32.0F, 20.0F + rowV, 28.0F, 20.0F + rowV, 28.0F, 32.0F + rowV, 32.0F, 32.0F + rowV, 1.0F, 0.0F, 0.0F, light, overlay);
        side(out, e, x1, y0, back, x0, y0, back, x0, y0, front, x1, y0, front, 28.0F, 16.0F + rowV, 20.0F, 16.0F + rowV, 20.0F, 20.0F + rowV, 28.0F, 20.0F + rowV, 0.0F, -1.0F, 0.0F, light, overlay);
        side(out, e, x1, y1, front, x0, y1, front, x0, y1, back, x1, y1, back, 36.0F, 20.0F + rowV, 28.0F, 20.0F + rowV, 28.0F, 16.0F + rowV, 36.0F, 16.0F + rowV, 0.0F, 1.0F, 0.0F, light, overlay);
    }

    private static void quadUv(VertexConsumer out, MatrixStack.Entry e, float[] p0, float[] p1, float[] p2, float[] p3, float sx, float sy, float z, float nx, float ny, float nz,
            float u0, float uDir, float v0, int light, int overlay) {
        for (float[] p : new float[][]{p0, p1, p2, p3}) {
            float u = u0 + uDir * (p[0] + 4.0F), v = v0 + p[1];
            out.vertex(e, p[0] * sx / 16.0F, (6.0F + (p[1] - 6.0F) * sy) / 16.0F, z / 16.0F).color(-1).texture(u / 64.0F, v / 64.0F).overlay(overlay).light(light).normal(e, nx, ny, nz);
        }
    }

    private static void side(VertexConsumer out, MatrixStack.Entry e, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float au, float av, float bu, float bv, float cu, float cv, float du, float dv, float nx, float ny, float nz, int light, int overlay) {
        point(out, e, ax, ay, az, au, av, nx, ny, nz, light, overlay);
        point(out, e, bx, by, bz, bu, bv, nx, ny, nz, light, overlay);
        point(out, e, cx, cy, cz, cu, cv, nx, ny, nz, light, overlay);
        point(out, e, dx, dy, dz, du, dv, nx, ny, nz, light, overlay);
    }

    private static void point(VertexConsumer out, MatrixStack.Entry e, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light, int overlay) {
        out.vertex(e, x / 16.0F, y / 16.0F, z / 16.0F).color(-1).texture(u / 64.0F, v / 64.0F).overlay(overlay).light(light).normal(e, nx, ny, nz);
    }

    /** Стенки дыры изнутри: тёмное мясо реацу. */
    private static void tube(VertexConsumer out, MatrixStack.Entry e, float open, int light, int overlay) {
        for (int i = 0; i < EDGE.length; i++) {
            float[] a = ring(EDGE[i], open), b = ring(EDGE[(i + 1) % EDGE.length], open);
            float nx = -(a[0] - CENTER_X), ny = -(a[1] - CENTER_Y);
            float l = Math.max(0.01F, MathHelper.sqrt(nx * nx + ny * ny));
            wall(out, e, a[0], a[1], -2.0F, 34, 8, 12, nx / l, ny / l, light, overlay);
            wall(out, e, b[0], b[1], -2.0F, 34, 8, 12, nx / l, ny / l, light, overlay);
            wall(out, e, b[0], b[1], 2.0F, 34, 8, 12, nx / l, ny / l, light, overlay);
            wall(out, e, a[0], a[1], 2.0F, 34, 8, 12, nx / l, ny / l, light, overlay);
        }
    }

    private static void wall(VertexConsumer out, MatrixStack.Entry e, float x, float y, float z, int r, int g, int b, float nx, float ny, int light, int overlay) {
        out.vertex(e, x / 16.0F, y / 16.0F, z / 16.0F).color(r, g, b, 255).texture(0.5F, 0.5F).overlay(overlay).light(light).normal(e, nx, ny, 0.0F);
    }

    /** Ровная алая кайма по кругу, чуть дышит. */
    private static void rim(VertexConsumer out, MatrixStack.Entry e, float open, float z, int overlay, float time) {
        int alpha = (int) ((0.55F + 0.3F * MathHelper.sin(time * 0.18F)) * 255.0F * Math.min(1.0F, open * 2.0F));
        int full = 15728880;
        for (int i = 0; i < EDGE.length; i++) {
            float[] a = ring(EDGE[i], open), b = ring(EDGE[(i + 1) % EDGE.length], open);
            float ax = a[0] + (a[0] - CENTER_X) * 0.14F, ay = a[1] + (a[1] - CENTER_Y) * 0.14F, bx = b[0] + (b[0] - CENTER_X) * 0.14F, by = b[1] + (b[1] - CENTER_Y) * 0.14F;
            glow(out, e, a[0], a[1], z, alpha, overlay, full);
            glow(out, e, b[0], b[1], z, alpha, overlay, full);
            glow(out, e, bx, by, z, 0, overlay, full);
            glow(out, e, ax, ay, z, 0, overlay, full);
        }
    }

    private static void glow(VertexConsumer out, MatrixStack.Entry e, float x, float y, float z, int alpha, int overlay, int light) {
        out.vertex(e, x / 16.0F, y / 16.0F, z / 16.0F).color(235, 40, 28, alpha).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, 0.0F, 0.0F, -1.0F);
    }
}
