package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.GetsugaEntity;
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
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** Гецуга Теншо: два скрещённых серпа (поперёк полёта и плашмя), растут и гаснут к концу. */
public class GetsugaRenderer extends EntityRenderer<GetsugaEntity> {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/entity/getsuga.png");

    public GetsugaRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(GetsugaEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float age = entity.age + tickDelta;
        float t = age / GetsugaEntity.LIFETIME;
        float alpha = MathHelper.clamp(t < 0.8F ? 1.0F : (1.0F - t) / 0.2F, 0.0F, 1.0F);
        float width = 1.6F + 1.4F * Math.min(1.0F, age / 6.0F);
        int color = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(TEXTURE));
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        matrices.push();
        matrices.translate(0.0F, 0.3F, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.getPitch()));
        // Плашмя: выпуклость серпа смотрит по ходу полёта.
        quad(vertices, matrices, width, width * 0.5F, true, color, full);
        // Поперёк полёта, с наклоном — как косой удар клинком.
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30.0F));
        quad(vertices, matrices, width, width * 0.5F, false, color, full);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    private static void quad(VertexConsumer vertices, MatrixStack matrices, float w, float h, boolean flat, int color, int light) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        float hw = w / 2.0F;
        if (flat) {
            // Плоскость XZ: верх картинки — вперёд (+Z).
            put(vertices, entry, m, -hw, 0.0F, h * 0.5F, 0.0F, 0.0F, color, light);
            put(vertices, entry, m, hw, 0.0F, h * 0.5F, 1.0F, 0.0F, color, light);
            put(vertices, entry, m, hw, 0.0F, -h * 0.5F, 1.0F, 1.0F, color, light);
            put(vertices, entry, m, -hw, 0.0F, -h * 0.5F, 0.0F, 1.0F, color, light);
        } else {
            put(vertices, entry, m, -hw, h * 0.5F, 0.0F, 0.0F, 0.0F, color, light);
            put(vertices, entry, m, hw, h * 0.5F, 0.0F, 1.0F, 0.0F, color, light);
            put(vertices, entry, m, hw, -h * 0.5F, 0.0F, 1.0F, 1.0F, color, light);
            put(vertices, entry, m, -hw, -h * 0.5F, 0.0F, 0.0F, 1.0F, color, light);
        }
    }

    private static void put(VertexConsumer vertices, MatrixStack.Entry entry, Matrix4f m, float x, float y, float z, float u, float v,
            int color, int light) {
        vertices.vertex(m, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public Identifier getTexture(GetsugaEntity entity) {
        return TEXTURE;
    }
}
