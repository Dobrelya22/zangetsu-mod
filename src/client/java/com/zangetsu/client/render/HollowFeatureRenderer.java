package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Рисует маску Пустого на игроке, который принял эту форму. Во время превращения маска нарастает. */
public class HollowFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEXTURE = ZangetsuMod.id("textures/entity/hollow_mask.png");
    private static final Identifier EYES = ZangetsuMod.id("textures/entity/hollow_mask_eyes.png");

    private final HollowMaskModel mask;

    public HollowFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context,
            EntityModelLoader loader) {
        super(context);
        this.mask = new HollowMaskModel(loader.getModelPart(HollowMaskModel.LAYER));
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
        PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
        int alpha = (int) (grow * 255.0F);
        int color = (alpha << 24) | 0xFFFFFF;

        matrices.push();
        model.head.rotate(matrices);
        this.mask.head.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), light, OverlayTexture.DEFAULT_UV, color);
        this.mask.head.render(matrices, consumers.getBuffer(RenderLayer.getEyes(EYES)), 0xF000F0, OverlayTexture.DEFAULT_UV,
                (255 << 24) | (alpha << 16) | (alpha << 8) | alpha);
        matrices.pop();

        matrices.push();
        model.body.rotate(matrices);
        this.mask.chest.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE)), light, OverlayTexture.DEFAULT_UV, color);
        matrices.pop();
    }
}
