package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Рисует маску Пустого на игроке, который принял эту форму. Во время превращения маска проступает ({@link HollowRite}),
 * к концу трескается: трещина, скол, почти рассыпалась. На уровне V — финальная маска с одним рогом.
 */
public class HollowFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public HollowFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    /** Глаз маски горит: огонь срывается вверх и назад, сердцевина светится. */
    private static void eyeFire(MatrixStack matrices, VertexConsumerProvider consumers, float fire, float time) {
        MatrixStack.Entry entry = matrices.peek();
        float x = 2.05F / 16.0F, y = -2.9F / 16.0F, z = -4.75F / 16.0F;
        Flames.Frame frame = Flames.Frame.world(entry, new org.joml.Vector3f(0.0F, 0.02F, 0.0F), time * 0.6F, 1.0F);
        VertexConsumer body = consumers.getBuffer(Flames.bodyLayer());
        Flames.flame(body, frame, x, y, z, 0.42F * fire, 0.2F * fire, 3.1F, 1.0F);
        Flames.flame(body, frame, x + 0.03F, y - 0.01F, z, 0.3F * fire, 0.16F * fire, 7.7F, 1.0F);
        VertexConsumer core = consumers.getBuffer(Flames.coreLayer());
        Flames.glow(core, frame, x, y, z, 0.42F * fire, 0.2F * fire, 3.1F, 1.0F);
        Flames.glow(core, frame, x + 0.03F, y - 0.01F, z, 0.3F * fire, 0.16F * fire, 7.7F, 1.0F);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!ClientState.isHollow(player) || player.isInvisible()) {
            return;
        }
        HollowRite.Mask mask = HollowRite.mask(player, tickDelta);
        int crack = MathHelper.clamp(ClientState.maskCrack(player), 0, 3);
        int alpha = (int) (mask.glow() * 255.0F);
        // У почти рассыпавшейся маски свечение глаз дрожит.
        int glow = crack == 3 ? (int) (alpha * (0.55F + 0.45F * Math.abs(MathHelper.sin(animationProgress * 0.9F)))) : alpha;
        matrices.push();
        this.getContextModel().head.rotate(matrices);
        HollowMaskRenderer.render(matrices, consumers, light, crack, mask, glow, ClientState.isFinal(player));
        float fire = com.zangetsu.client.RampageScene.eyeFire(player, animationProgress);
        if (fire > 0.0F) {
            eyeFire(matrices, consumers, fire, animationProgress);
        }
        matrices.pop();
    }
}
