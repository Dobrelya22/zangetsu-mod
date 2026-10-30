package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Позы игрока в сценах: тянется к рукояти и поднимает меч; замах двумя руками и удар сверху (казнь);
 * горизонтальный взмах (эндермен пополам); вытаскивает меч из земли во сне; раскидывает руки и
 * запрокидывает голову, превращаясь в Пустого. В бою с Зангецу — тяжёлые взмахи всем корпусом (три удара по очереди),
 * рывок, когда меч рубит сам, и Гецуга: замах высоко за правое плечо, левая рука указывает цель — и взмах по горизонтали.
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
            case AnimationPayload.AUTO_SLASH -> auto(model, t, animation.param());
            case AnimationPayload.GETSUGA -> getsuga(model, t, animationProgress);
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

    /**
     * Насколько клинок развёрнут вперёд в кулаке (0 — висит лезвием вниз, 1 — по ходу удара): во время удара мечом,
     * рывка и Гецуги.
     */
    public static float bladeForward(PlayerEntity player) {
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        ClientState.Animation animation = ClientState.animation(player);
        float t;
        if (animation == null) {
            t = player.getHandSwingProgress(tickDelta);
        } else if (animation.type() == AnimationPayload.AUTO_SLASH || animation.type() == AnimationPayload.GETSUGA) {
            t = animation.progress(player, tickDelta);
        } else {
            return 0.0F;
        }
        if (t <= 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        return phase(t, 0.0F, 0.18F) * (1.0F - phase(t, 0.62F, 1.0F));
    }

    // ---------------------------------------------------------------- бой

    /**
     * Поза бойца: правая рука (питч, рыск, крен), левая рука (то же), поворот и наклон корпуса. Тяжёлый меч
     * ведёт весь корпус — плечи поворачиваются вместе с ним. Рыск и поворот плюс — вправо (к руке с мечом),
     * минус — влево, поперёк корпуса.
     */
    private static final float[][][] CLEAVES = {
            // Диагональ: замах над правым плечом — удар вниз влево.
            {{-2.8F, 0.45F, 0.2F, -0.2F, -0.2F, -0.25F, 0.4F, -0.05F}, {-0.55F, -0.85F, -0.15F, -0.6F, -0.4F, -0.35F, -0.45F, 0.22F}},
            // Восходящий: снизу слева — вверх вправо.
            {{-0.35F, -0.95F, -0.3F, -0.5F, -0.3F, -0.2F, -0.45F, 0.15F}, {-2.6F, 0.55F, 0.3F, -0.15F, -0.1F, -0.4F, 0.4F, -0.1F}},
            // Горизонтальный: справа налево на уровне плеч.
            {{-1.55F, 1.15F, 0.0F, -0.4F, -0.4F, -0.2F, 0.5F, 0.0F}, {-1.55F, -1.15F, 0.0F, -0.8F, -0.6F, -0.3F, -0.55F, 0.05F}},
    };

    /** Обычный удар мечом ({ p} — ход ванильного взмаха 0..1, { variant} — какой из трёх). */
    public static void cleave(BipedEntityModel<?> model, float p, int variant) {
        float[][] pose = CLEAVES[MathHelper.clamp(variant, 0, 2)];
        strike(model, p, pose[0], pose[1], 0.2F, 0.45F, 0.6F);
    }

    /** Меч рубит сам: рывок к цели ({ toward} — угол к ней в градусах), корпус подаётся вперёд. */
    private static void auto(BipedEntityModel<?> model, float t, int toward) {
        float aim = MathHelper.clamp(toward * MathHelper.RADIANS_PER_DEGREE, -1.2F, 1.2F);
        float[] wind = {-2.2F, 0.7F, 0.3F, -0.3F, -0.2F, -0.3F, 0.35F, 0.0F};
        float[] hit = {-1.25F, -0.6F + aim * 0.8F, -0.2F, -0.9F, -0.5F, -0.5F, -0.3F + aim * 0.4F, 0.3F};
        strike(model, t, wind, hit, 0.25F, 0.42F, 0.65F);
    }

    /** Гецуга: замах за правое плечо, левая рука указывает цель; дрожь перед ударом; взмах по горизонтали. */
    private static void getsuga(BipedEntityModel<?> model, float t, float time) {
        float[] wind = {-3.0F, 0.75F, 0.35F, -1.45F, -0.35F, 0.0F, 0.55F, -0.12F};
        float[] hit = {-1.5F, -1.2F, 0.0F, -0.6F, -0.8F, -0.4F, -0.6F, 0.18F};
        strike(model, t, wind, hit, 0.33F, 0.48F, 0.62F);
        if (t > 0.2F && t < 0.38F) {
            float tremble = MathHelper.sin(time * 3.3F) * 0.03F;
            model.rightArm.roll += tremble;
            model.body.pitch += tremble * 0.5F;
        }
    }

    /** Покой → замах (до { tWind}) → удар (до { tHit}) → держит → возврат (с { tBack}). */
    private static void strike(BipedEntityModel<?> model, float t, float[] wind, float[] hit, float tWind, float tHit, float tBack) {
        if (t < tWind) {
            blend(model, wind, phase(t, 0.0F, tWind));
        } else if (t < tBack) {
            float k = phase(t, tWind, tHit);
            float[] pose = new float[wind.length];
            for (int i = 0; i < pose.length; i++) {
                pose[i] = MathHelper.lerp(k, wind[i], hit[i]);
            }
            blend(model, pose, 1.0F);
        } else {
            blend(model, hit, 1.0F - phase(t, tBack, 1.0F));
        }
    }

    private static void blend(BipedEntityModel<?> m, float[] pose, float k) {
        float bodyYaw = MathHelper.lerp(k, m.body.yaw, pose[6]);
        m.rightArm.pitch = MathHelper.lerp(k, m.rightArm.pitch, pose[0]);
        m.rightArm.yaw = MathHelper.lerp(k, m.rightArm.yaw, pose[1]) + bodyYaw * k;
        m.rightArm.roll = MathHelper.lerp(k, m.rightArm.roll, pose[2]);
        m.leftArm.pitch = MathHelper.lerp(k, m.leftArm.pitch, pose[3]);
        m.leftArm.yaw = MathHelper.lerp(k, m.leftArm.yaw, pose[4]) + bodyYaw * k;
        m.leftArm.roll = MathHelper.lerp(k, m.leftArm.roll, pose[5]);
        m.body.yaw = bodyYaw;
        m.body.pitch = MathHelper.lerp(k, m.body.pitch, pose[7]);
        // Плечи идут за корпусом.
        m.rightArm.pivotZ = MathHelper.sin(bodyYaw) * 5.0F;
        m.rightArm.pivotX = -MathHelper.cos(bodyYaw) * 5.0F;
        m.leftArm.pivotZ = -MathHelper.sin(bodyYaw) * 5.0F;
        m.leftArm.pivotX = MathHelper.cos(bodyYaw) * 5.0F;
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
