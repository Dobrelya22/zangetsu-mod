package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Рисует маску Пустого на игроке, который принял эту форму. Во время превращения маска нарастает,
 * к концу трескается: трещина, скол (обломан кончик рога), почти рассыпалась (рога сломаны).
 */
public class HollowFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public HollowFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!ClientState.isHollow(player) || player.isInvisible()) {
            return;
        }
        float grow = 1.0F;
        ClientState.Animation animation = ClientState.animation(player);
        if (animation != null && animation.type() == AnimationPayload.HOLLOW) {
            grow = MathHelper.clamp(animation.progress(player, tickDelta) * 1.6F - 0.2F, 0.0F, 1.0F);
        }
        if (grow <= 0.0F) {
            return;
        }
        int crack = MathHelper.clamp(ClientState.maskCrack(player), 0, 3);
        int alpha = (int) (grow * 255.0F);
        // У почти рассыпавшейся маски свечение глаз дрожит.
        int glow = crack == 3 ? (int) (alpha * (0.55F + 0.45F * Math.abs(MathHelper.sin(animationProgress * 0.9F)))) : alpha;
        matrices.push();
        this.getContextModel().head.rotate(matrices);
        HollowMaskRenderer.render(matrices, consumers, light, crack, grow, glow);
        matrices.pop();
    }
}
