package com.zangetsu.client.mixin;

import com.zangetsu.client.ClientState;
import com.zangetsu.client.TooSlowScene;
import com.zangetsu.client.render.PlayerPoses;
import com.zangetsu.logic.Limbs;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рассечённый пополам эндермен сам не рисуется — вместо него падают две половины. Игрок в приёме Зангецу приседает в удар и
 * разворачивается всем телом.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void zangetsu$swingRoot(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale,
            CallbackInfo ci) {
        if (entity instanceof PlayerEntity) {
            float[] root = PlayerPoses.root(entity, animationProgress);
            if (root != null) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(root[0]));
                matrices.translate(0.0F, -root[1] / 16.0F, 0.0F);
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void zangetsu$hideSplit(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
            int light, CallbackInfo ci) {
        if ((ClientState.limbs(entity) & Limbs.SPLIT) != 0 || TooSlowScene.hidden(entity, tickDelta)) {
            ci.cancel();
        }
    }

    /** След разреза рисуется поверх моба, пока тот ещё стоит. */
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void zangetsu$cutMark(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        com.zangetsu.client.render.CutMarks.render(entity, matrices, consumers, tickDelta);
    }
}
