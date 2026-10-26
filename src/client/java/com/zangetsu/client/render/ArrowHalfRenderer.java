package com.zangetsu.client.render;

import com.zangetsu.entity.ArrowHalfEntity;
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

/**
 * Половинка стрелы: та же геометрия, что у ванильной стрелы (крест из двух плоскостей),
 * только длиной в половину и с половиной текстуры — наконечник или хвост с оперением.
 */
public class ArrowHalfRenderer extends EntityRenderer<ArrowHalfEntity> {
    private static final Identifier ARROW = Identifier.ofVanilla("textures/entity/projectiles/arrow.png");
    private static final Identifier SPECTRAL = Identifier.ofVanilla("textures/entity/projectiles/spectral_arrow.png");

    public ArrowHalfRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(ArrowHalfEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) - 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45.0F + MathHelper.lerp(tickDelta, entity.prevRoll, entity.roll)));
        matrices.scale(0.05625F, 0.05625F, 0.05625F);
        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(entity.isSpectral() ? SPECTRAL : ARROW));
        // Хвост: x от −8 до 0, u от 0 до 0,25; наконечник: x от 0 до 8, u от 0,25 до 0,5. Центр половинки — в нуле.
        boolean tip = entity.getHalf() == ArrowHalfEntity.TIP;
        float u0 = tip ? 0.25F : 0.0F;
        float u1 = tip ? 0.5F : 0.25F;
        for (int i = 0; i < 2; i++) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
            MatrixStack.Entry entry = matrices.peek();
            Matrix4f m = entry.getPositionMatrix();
            vertex(vertices, entry, m, -4, -2, u0, 0.0F, light);
            vertex(vertices, entry, m, 4, -2, u1, 0.0F, light);
            vertex(vertices, entry, m, 4, 2, u1, 0.15625F, light);
            vertex(vertices, entry, m, -4, 2, u0, 0.15625F, light);
        }
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    private static void vertex(VertexConsumer vertices, MatrixStack.Entry entry, Matrix4f m, float x, float y, float u, float v, int light) {
        vertices.vertex(m, x, y, 0.0F).color(-1).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public Identifier getTexture(ArrowHalfEntity entity) {
        return ARROW;
    }
}
