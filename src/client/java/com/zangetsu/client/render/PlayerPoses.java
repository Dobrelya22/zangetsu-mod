package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Позы игрока в сценах: тянется к рукояти и поднимает меч; замах двумя руками и удар сверху (казнь);
 * горизонтальный взмах (эндермен пополам); вытаскивает меч из земли во сне; раскидывает руки и
 * запрокидывает голову, превращаясь в Пустого.
 */
public final class PlayerPoses {
    private PlayerPoses() {
    }

    public static void apply(BipedEntityModel<?> model, LivingEntity player, float animationProgress) {
        ClientState.Animation animation = ClientState.animation(player);
        if (animation == null) {
            return;
        }
        float t = MathHelper.clamp((animationProgress - animation.startAge()) / Math.max(1, animation.duration()), 0.0F, 1.0F);
        switch (animation.type()) {
            case AnimationPayload.TOUCH_HANDLE -> touch(model, t, animationProgress, false);
            case AnimationPayload.DREAM_PULL -> touch(model, t, animationProgress, true);
            case AnimationPayload.EXECUTE -> execute(model, t);
            case AnimationPayload.SLASH -> slash(model, t);
            case AnimationPayload.HOLLOW -> hollow(model, t, animationProgress);
            default -> {
            }
        }
        model.hat.copyTransform(model.head);
    }

    private static float phase(float t, float from, float to) {
        float x = MathHelper.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Тянется к рукояти, замирает на миг, затем поднимает меч над головой (во сне — обеими руками). */
    private static void touch(BipedEntityModel<?> model, float t, float time, boolean bothHands) {
        float reach = phase(t, 0.0F, 0.42F);
        float raise = phase(t, 0.58F, 0.9F);
        float tremble = t > 0.35F && t < 0.6F ? MathHelper.sin(time * 2.7F) * 0.035F : 0.0F;
        float pitch = MathHelper.lerp(raise, MathHelper.lerp(reach, model.rightArm.pitch, -1.05F), -2.75F);
        model.rightArm.pitch = pitch + tremble;
        model.rightArm.yaw = MathHelper.lerp(reach, 0.0F, -0.12F) * (1.0F - raise);
        model.rightArm.roll = tremble;
        if (bothHands) {
            model.leftArm.pitch = pitch - tremble;
            model.leftArm.yaw = 0.35F * reach;
            model.leftArm.roll = 0.0F;
        } else {
            model.leftArm.pitch = MathHelper.lerp(reach, model.leftArm.pitch, -0.25F);
            model.leftArm.roll = -0.2F * reach;
        }
        model.body.pitch = 0.32F * reach * (1.0F - raise);
        model.head.pitch = MathHelper.lerp(raise, MathHelper.lerp(reach, model.head.pitch, 0.55F), -0.35F);
    }

    /** Казнь: замах двумя руками над головой — резкий удар сверху вниз — пауза. */
    private static void execute(BipedEntityModel<?> model, float t) {
        float wind = phase(t, 0.0F, 0.4F);
        float strike = phase(t, 0.42F, 0.52F);
        float recover = phase(t, 0.8F, 1.0F);
        float pitch = MathHelper.lerp(strike, MathHelper.lerp(wind, model.rightArm.pitch, -2.95F), -0.35F);
        pitch = MathHelper.lerp(recover, pitch, 0.0F);
        model.rightArm.pitch = pitch;
        model.leftArm.pitch = pitch;
        model.rightArm.yaw = -0.3F * wind * (1.0F - recover);
        model.leftArm.yaw = 0.45F * wind * (1.0F - recover);
        model.rightArm.roll = 0.0F;
        model.leftArm.roll = 0.0F;
        model.body.pitch = (-0.18F * wind * (1.0F - strike) + 0.35F * strike) * (1.0F - recover);
        model.head.pitch = 0.25F * strike * (1.0F - recover);
    }

    /** Горизонтальный взмах: рука уходит влево за корпус и проносится вправо. */
    private static void slash(BipedEntityModel<?> model, float t) {
        float wind = phase(t, 0.0F, 0.4F);
        float strike = phase(t, 0.43F, 0.53F);
        float recover = phase(t, 0.8F, 1.0F);
        float yaw = MathHelper.lerp(strike, -1.25F * wind, 1.35F);
        model.rightArm.pitch = MathHelper.lerp(recover, -1.5F * Math.max(wind, strike), 0.0F);
        model.rightArm.yaw = MathHelper.lerp(recover, yaw, 0.0F);
        model.rightArm.roll = 0.0F;
        model.leftArm.pitch = -0.3F * (1.0F - recover);
        model.leftArm.roll = -0.3F * (1.0F - recover);
        model.body.yaw = MathHelper.lerp(recover, MathHelper.lerp(strike, -0.35F * wind, 0.3F), 0.0F);
    }

    /** Превращение: руки в стороны, голова запрокинута — рёв. */
    private static void hollow(BipedEntityModel<?> model, float t, float time) {
        float open = phase(t, 0.0F, 0.35F) * (1.0F - phase(t, 0.85F, 1.0F));
        float shake = MathHelper.sin(time * 3.1F) * 0.04F * open;
        model.rightArm.roll = 1.2F * open + shake;
        model.leftArm.roll = -1.2F * open - shake;
        model.rightArm.pitch = MathHelper.lerp(open, model.rightArm.pitch, -0.35F);
        model.leftArm.pitch = MathHelper.lerp(open, model.leftArm.pitch, -0.35F);
        model.head.pitch = MathHelper.lerp(open, model.head.pitch, -0.65F) + shake;
        model.body.pitch = -0.15F * open;
    }
}
