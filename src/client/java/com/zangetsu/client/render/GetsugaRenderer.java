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
 * Гецуга Теншо из света, а не из картинки: серп собран из лент вдоль дуги. Снаружи — широкое алое
 * свечение (складывается со светом), внутри — чёрное ядро, по выпуклой кромке — раскалённая нить;
 * за серпом тянутся гаснущие отпечатки, края мерцают.
 */
public class GetsugaRenderer extends EntityRenderer<GetsugaEntity> {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/misc/white.png");
    private static final int SEGMENTS = 90;
    /** Ширина чёрного ядра (доля ширины серпа). */
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

        // Отпечатки позади: тот же серп, всё прозрачнее.
        for (int k = 3; k >= 1; k--) {
            float trailAlpha = alpha * (0.42F - k * 0.11F);
            float trailSize = size * (1.0F - k * 0.04F);
            float back = -k * 0.75F * size;
            // Тоже кольцом: шлейф ближе к стрелявшему и иначе засветил бы чёрное ядро.
            ring(consumers.getBuffer(RenderLayer.getLightning()), m, trailSize, back, CORE_WIDTH, 1.9F, 255, 20, 12, trailAlpha * 0.6F, age, true);
            ring(consumers.getBuffer(RenderLayer.getLightning()), m, trailSize, back, CORE_WIDTH, 1.05F, 255, 40, 25, trailAlpha, age, false);
        }
        // Свечение и алая кайма — кольцом вокруг ядра: свет складывается, и там, где ядро, его быть не должно.
        ring(consumers.getBuffer(RenderLayer.getLightning()), m, size, 0.0F, CORE_WIDTH, 2.7F, 255, 18, 10, alpha * 0.6F, age, true);
        ring(consumers.getBuffer(RenderLayer.getLightning()), m, size, 0.0F, CORE_WIDTH, 1.3F, 255, 50, 30, alpha * 0.95F, age, false);
        // Чёрное ядро (обычное смешивание) чуть ближе к игроку, чтобы свечение позади его не засветило.
        core(consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), matrices.peek(), size, 0.0F, CORE_WIDTH, 0.0F, alpha * 0.97F, age);
        // Раскалённая нить по выпуклой кромке.
        band(consumers.getBuffer(RenderLayer.getLightning()), m, size, 0.0F, 0.13F, CORE_WIDTH + 0.14F, 255, 215, 195, alpha, age, false);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    /**
     * Лента вдоль серпа: ширина = {@code width} от ширины серпа, сдвиг поперёк — {@code shift} (−1..1),
     * вдоль полёта — {@code forward}. {@code soft} — края ленты гаснут до нуля (мягкое свечение).
     */
    private static void band(VertexConsumer vertices, Matrix4f m, float size, float forward, float width, float shift,
            int r, int g, int b, float alpha, float age, boolean soft) {
        if (alpha <= 0.01F) {
            return;
        }
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = -1.0F + 2.0F * i / SEGMENTS, t1 = -1.0F + 2.0F * (i + 1) / SEGMENTS;
            Vector3f[] s0 = section(t0, size, forward, width, shift, age, i);
            Vector3f[] s1 = section(t1, size, forward, width, shift, age, i + 1);
            if (soft) {
                // Три полосы: прозрачный край → яркая середина → прозрачный край.
                quad(vertices, m, s0[0], s1[0], s1[1], s0[1], r, g, b, 0, 0, a, a);
                quad(vertices, m, s0[1], s1[1], s1[2], s0[2], r, g, b, a, a, 0, 0);
            } else {
                quad(vertices, m, s0[0], s1[0], s1[2], s0[2], r, g, b, a, a, a, a);
            }
        }
    }

    /**
     * Кольцо вокруг ядра: от края ядра ({ inner}) до { outer} с обеих сторон. { soft} — к наружному
     * краю гаснет до нуля, иначе ровная кайма.
     */
    private static void ring(VertexConsumer vertices, Matrix4f m, float size, float forward, float inner, float outer,
            int r, int g, int b, float alpha, float age, boolean soft) {
        if (alpha <= 0.01F) {
            return;
        }
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int edge = soft ? 0 : a;
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = -1.0F + 2.0F * i / SEGMENTS, t1 = -1.0F + 2.0F * (i + 1) / SEGMENTS;
            Vector3f[] in0 = section(t0, size, forward, inner, 0.0F, age, i), in1 = section(t1, size, forward, inner, 0.0F, age, i + 1);
            Vector3f[] out0 = section(t0, size, forward, outer, 0.0F, age, i), out1 = section(t1, size, forward, outer, 0.0F, age, i + 1);
            quad(vertices, m, out0[0], out1[0], in1[0], in0[0], r, g, b, edge, edge, a, a);
            quad(vertices, m, in0[2], in1[2], out1[2], out0[2], r, g, b, a, a, edge, edge);
        }
    }

    /** Чёрное ядро серпа — обычный полупрозрачный слой сущностей, не светится. */
    private static void core(VertexConsumer v, MatrixStack.Entry entry, float size, float forward, float width, float shift, float alpha, float age) {
        int a = (int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        Matrix4f m = entry.getPositionMatrix();
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = -1.0F + 2.0F * i / SEGMENTS, t1 = -1.0F + 2.0F * (i + 1) / SEGMENTS;
            Vector3f[] s0 = section(t0, size, forward, width, shift, age, i);
            Vector3f[] s1 = section(t1, size, forward, width, shift, age, i + 1);
            Vector3f[] quad = {s0[0], s1[0], s1[2], s0[2]};
            for (int side = 0; side < 2; side++) {
                for (int k = 0; k < 4; k++) {
                    Vector3f p = quad[side == 0 ? k : 3 - k];
                    v.vertex(m, p.x, p.y, p.z).color(8, 0, 3, a).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV)
                            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 0.0F, side == 0 ? -1.0F : 1.0F);
                }
            }
        }
    }

    /** Края и середина ленты в точке t (с мерцанием ширины). */
    private static Vector3f[] section(float t, float size, float forward, float width, float shift, float age, int i) {
        float flicker = 1.0F + 0.07F * MathHelper.sin(age * 2.3F + t * 9.0F) * MathHelper.sin(age * 0.9F + t * 4.0F);
        float hw = GetsugaEntity.halfWidth(t, size) * width * flicker;
        Vector3f center = GetsugaEntity.arc(t, size);
        Vector3f across = GetsugaEntity.across(t);
        center.add(new Vector3f(across).mul(GetsugaEntity.halfWidth(t, size) * shift)).add(0.0F, 0.0F, forward);
        return new Vector3f[]{
                new Vector3f(center).sub(new Vector3f(across).mul(hw)),
                new Vector3f(center),
                new Vector3f(center).add(new Vector3f(across).mul(hw))
        };
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
