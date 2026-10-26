package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.GetsugaEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Гецуга Теншо из света, а не из картинки. Серп лежит горизонтально — изогнутая вперёд стена энергии:
 * чёрное ядро, вокруг алое свечение (складывается со светом), по верхней и нижней кромке — раскалённые
 * нити, позади — гаснущие отпечатки, а плашмя под волной — мягкий алый серп, видный сверху.
 */
public class GetsugaRenderer extends EntityRenderer<GetsugaEntity> {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/misc/white.png");
    private static final int SEGMENTS = 90;
    /** Высота чёрного ядра (доля высоты волны). */
    private static final float CORE_WIDTH = 0.7F;

    public GetsugaRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(GetsugaEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float age = entity.age + tickDelta;
        float size = GetsugaEntity.size(age);
        float alpha = GetsugaEntity.alpha(age);
        if (alpha <= 0.0F) {
            return;
        }
        matrices.push();
        matrices.multiply(GetsugaEntity.orientation(entity.getYaw(), entity.getPitch()));
        Matrix4f m = matrices.peek().getPositionMatrix();

        // Отпечатки позади: тот же серп, всё прозрачнее (кольцом — иначе засветили бы чёрное ядро).
        for (int k = 3; k >= 1; k--) {
            float trailAlpha = alpha * (0.42F - k * 0.11F);
            float trailSize = size * (1.0F - k * 0.04F);
            float back = -k * 0.7F * size;
            ring(consumers.getBuffer(RenderLayer.getLightning()), m, trailSize, back, CORE_WIDTH, 1.9F, 255, 20, 12, trailAlpha * 0.6F, age, true);
            ring(consumers.getBuffer(RenderLayer.getLightning()), m, trailSize, back, CORE_WIDTH, 1.05F, 255, 40, 25, trailAlpha, age, false);
        }
        // Плашмя: мягкий алый серп в горизонтальной плоскости — форма удара видна сверху и сбоку.
        flat(consumers.getBuffer(RenderLayer.getLightning()), m, size, 255, 30, 18, alpha * 0.45F);
        // Свечение и алая кайма вокруг ядра.
        ring(consumers.getBuffer(RenderLayer.getLightning()), m, size, 0.0F, CORE_WIDTH, 2.6F, 255, 18, 10, alpha * 0.6F, age, true);
        ring(consumers.getBuffer(RenderLayer.getLightning()), m, size, 0.0F, CORE_WIDTH, 1.3F, 255, 50, 30, alpha * 0.95F, age, false);
        // Чёрное ядро — обычный полупрозрачный слой, не светится.
        core(consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), matrices.peek(), size, CORE_WIDTH, alpha * 0.97F, age);
        // Раскалённые нити по верхней и нижней кромке ядра.
        band(consumers.getBuffer(RenderLayer.getLightning()), m, size, 0.12F, CORE_WIDTH + 0.13F, 255, 215, 195, alpha, age);
        band(consumers.getBuffer(RenderLayer.getLightning()), m, size, 0.1F, -(CORE_WIDTH + 0.13F), 255, 160, 130, alpha * 0.85F, age);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    private static float t(int i) {
        return -1.0F + 2.0F * i / SEGMENTS;
    }

    /** Мерцание высоты — плавно вдоль серпа. */
    private static float flicker(float t, float age) {
        return 1.0F + 0.07F * MathHelper.sin(age * 2.3F + t * 9.0F) * MathHelper.sin(age * 0.9F + t * 4.0F);
    }

    /** Точка на волне: средняя линия, сдвиг по высоте {@code up} (в долях высоты) и вдоль полёта {@code forward}. */
    private static Vector3f point(float t, float size, float up, float forward, float age) {
        float h = GetsugaEntity.halfWidth(t, size) * flicker(t, age);
        return GetsugaEntity.arc(t, size).add(GetsugaEntity.across(t).mul(h * up)).add(0.0F, 0.0F, forward);
    }

    /** Ровная лента по высоте от {@code center - width} до {@code center + width} (в долях высоты волны). */
    private static void band(VertexConsumer v, Matrix4f m, float size, float width, float center, int r, int g, int b, float alpha, float age) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = t(i), t1 = t(i + 1);
            quad(v, m, point(t0, size, center - width, 0.0F, age), point(t1, size, center - width, 0.0F, age),
                    point(t1, size, center + width, 0.0F, age), point(t0, size, center + width, 0.0F, age), r, g, b, a, a, a, a);
        }
    }

    /**
     * Кольцо вокруг ядра: от края ядра ({@code inner}) до {@code outer} сверху и снизу. {@code soft} — к наружному
     * краю гаснет до нуля, иначе ровная кайма.
     */
    private static void ring(VertexConsumer v, Matrix4f m, float size, float forward, float inner, float outer,
            int r, int g, int b, float alpha, float age, boolean soft) {
        if (alpha <= 0.01F) {
            return;
        }
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int edge = soft ? 0 : a;
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = t(i), t1 = t(i + 1);
            quad(v, m, point(t0, size, outer, forward, age), point(t1, size, outer, forward, age),
                    point(t1, size, inner, forward, age), point(t0, size, inner, forward, age), r, g, b, edge, edge, a, a);
            quad(v, m, point(t0, size, -inner, forward, age), point(t1, size, -inner, forward, age),
                    point(t1, size, -outer, forward, age), point(t0, size, -outer, forward, age), r, g, b, a, a, edge, edge);
        }
    }

    /** Мягкий серп плашмя: от вогнутой стороны к выпуклой, яркий посередине. */
    private static void flat(VertexConsumer v, Matrix4f m, float size, int r, int g, int b, float alpha) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = t(i), t1 = t(i + 1);
            float w0 = GetsugaEntity.halfWidth(t0, size) * 1.8F, w1 = GetsugaEntity.halfWidth(t1, size) * 1.8F;
            Vector3f c0 = GetsugaEntity.arc(t0, size), c1 = GetsugaEntity.arc(t1, size);
            Vector3f r0 = GetsugaEntity.radial(t0), r1 = GetsugaEntity.radial(t1);
            Vector3f in0 = new Vector3f(c0).sub(new Vector3f(r0).mul(w0)), in1 = new Vector3f(c1).sub(new Vector3f(r1).mul(w1));
            Vector3f out0 = new Vector3f(c0).add(new Vector3f(r0).mul(w0 * 0.6F)), out1 = new Vector3f(c1).add(new Vector3f(r1).mul(w1 * 0.6F));
            quad(v, m, in0, in1, c1, c0, r, g, b, 0, 0, a, a);
            quad(v, m, c0, c1, out1, out0, r, g, b, a, a, 0, 0);
        }
    }

    /** Чёрное ядро серпа. */
    private static void core(VertexConsumer v, MatrixStack.Entry entry, float size, float width, float alpha, float age) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        Matrix4f m = entry.getPositionMatrix();
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = t(i), t1 = t(i + 1);
            Vector3f[] quad = {point(t0, size, -width, 0.0F, age), point(t1, size, -width, 0.0F, age),
                    point(t1, size, width, 0.0F, age), point(t0, size, width, 0.0F, age)};
            for (int side = 0; side < 2; side++) {
                for (int k = 0; k < 4; k++) {
                    Vector3f p = quad[side == 0 ? k : 3 - k];
                    v.vertex(m, p.x, p.y, p.z).color(8, 0, 3, a).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV)
                            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 0.0F, side == 0 ? -1.0F : 1.0F);
                }
            }
        }
    }

    /** Четырёхугольник с двух сторон (свечение рисуется с отсечением задних граней). */
    private static void quad(VertexConsumer v, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
            int r, int g, int b, int a0, int a1, int a2, int a3) {
        v.vertex(m, p0.x, p0.y, p0.z).color(r, g, b, a0);
        v.vertex(m, p1.x, p1.y, p1.z).color(r, g, b, a1);
        v.vertex(m, p2.x, p2.y, p2.z).color(r, g, b, a2);
        v.vertex(m, p3.x, p3.y, p3.z).color(r, g, b, a3);
        v.vertex(m, p3.x, p3.y, p3.z).color(r, g, b, a3);
        v.vertex(m, p2.x, p2.y, p2.z).color(r, g, b, a2);
        v.vertex(m, p1.x, p1.y, p1.z).color(r, g, b, a1);
        v.vertex(m, p0.x, p0.y, p0.z).color(r, g, b, a0);
    }

    @Override
    public boolean shouldRender(GetsugaEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public Identifier getTexture(GetsugaEntity entity) {
        return TEXTURE;
    }
}
