package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * След разреза на том, кого уже разрубили, но кто ещё стоит: светящаяся жёлтая полоса поперёк тела, острая на концах. Она всегда развёрнута к камере и чуть вынесена вперёд, чтобы тело её не закрывало.
 */
public final class CutMarks {
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final float[] ANGLES = {-58.0F, 52.0F, -72.0F, 66.0F, -44.0F, 38.0F};
    private static final int SEGMENTS = 18;

    private CutMarks() {
    }

    public static void render(LivingEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        if (!ClientState.MARKS.containsKey(entity.getId())) {
            return;
        }
        float age = entity.age + tickDelta - ClientState.MARKS.get(entity.getId());
        float grow = MathHelper.clamp(age / 2.0F, 0.0F, 1.0F);
        float length = (entity.getHeight() * 0.56F + entity.getWidth() * 0.25F) * grow;
        float flicker = 0.86F + 0.14F * MathHelper.sin(age * 1.7F + entity.getId());
        matrices.push();
        matrices.translate(0.0F, entity.getHeight() * 0.55F, 0.0F);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        matrices.translate(0.0F, 0.0F, 0.6F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ANGLES[Math.floorMod(entity.getId(), ANGLES.length)]));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer out = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(Flames.WHITE));
        // Широкое мягкое свечение, поверх узкая почти белая сердцевина.
        strip(out, m, length * 1.06F, 0.2F, 255, 176, 20, (int) (120.0F * flicker));
        strip(out, m, length, 0.065F, 255, 232, 90, (int) (255.0F * flicker));
        strip(out, m, length * 0.92F, 0.024F, 255, 252, 214, 255);
        matrices.pop();
    }

    /** Полоса с острыми концами вдоль оси x. */
    private static void strip(VertexConsumer out, Matrix4f m, float length, float width, int r, int g, int b, int alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            float s0 = -1.0F + 2.0F * i / SEGMENTS, s1 = -1.0F + 2.0F * (i + 1) / SEGMENTS;
            float w0 = width * shape(s0), w1 = width * shape(s1);
            put(out, m, s0 * length, -w0, r, g, b, alpha);
            put(out, m, s1 * length, -w1, r, g, b, alpha);
            put(out, m, s1 * length, w1, r, g, b, alpha);
            put(out, m, s0 * length, w0, r, g, b, alpha);
        }
    }

    private static float shape(float s) {
        return 1.0F - (float) Math.pow(Math.abs(s), 1.8F);
    }

    private static void put(VertexConsumer out, Matrix4f m, float x, float y, int r, int g, int b, int alpha) {
        out.vertex(m, x, y, 0.0F).color(r, g, b, alpha).texture(0.5F, 0.5F).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(0.0F, 0.0F, 1.0F);
    }
}
