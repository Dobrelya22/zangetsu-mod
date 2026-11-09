package com.zangetsu.client.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Согнутые руки и ноги в сценах: подбор меча, превращения. Рисуются отдельно от обычной модели ({@link BentLimbs}). */
public class BentLimbFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public BentLimbFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        float[] bends = PlayerPoses.bends(player);
        if (bends != null && !player.isInvisible()) {
            BentLimbs.render(matrices, consumers, light, this.getContextModel(), this.getTexture(player), bends);
        }
    }
}
