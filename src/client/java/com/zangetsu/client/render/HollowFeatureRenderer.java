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

/**
 * Рисует маску Пустого на игроке, который принял эту форму. Во время превращения маска нарастает,
 * к концу трескается: трещина, скол (обломан кончик рога), почти рассыпалась (рога сломаны).
 */
public class HollowFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final Identifier EYES = ZangetsuMod.id("textures/entity/hollow_mask_eyes.png");

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = ZangetsuMod.id("textures/entity/hollow_mask_" + i + ".png");
        }
    }

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
        int crack = MathHelper.clamp(ClientState.maskCrack(player), 0, 3);
        // Обломки рогов: скол — кончик левого, почти рассыпалась — левый до основания и кончик правого.
        this.mask.leftHorn.getChild("mid").getChild("tip").visible = crack < 2;
        this.mask.leftHorn.getChild("mid").visible = crack < 3;
        this.mask.rightHorn.getChild("mid").getChild("tip").visible = crack < 3;

        PlayerEntityModel<AbstractClientPlayerEntity> model = this.getContextModel();
        int alpha = (int) (grow * 255.0F);
        // У почти рассыпавшейся маски свечение глаз дрожит.
        int glow = crack == 3 ? (int) (alpha * (0.55F + 0.45F * Math.abs(MathHelper.sin(animationProgress * 0.9F)))) : alpha;
        matrices.push();
        model.head.rotate(matrices);
        this.mask.head.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(TEXTURES[crack])), light, OverlayTexture.DEFAULT_UV,
                (alpha << 24) | 0xFFFFFF);
        this.mask.head.render(matrices, consumers.getBuffer(RenderLayer.getEyes(EYES)), 0xF000F0, OverlayTexture.DEFAULT_UV,
                (255 << 24) | (glow << 16) | (glow << 8) | glow);
        matrices.pop();
    }
}
