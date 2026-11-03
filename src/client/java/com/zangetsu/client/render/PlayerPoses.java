package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Позы игрока в сценах: тянется к рукояти и поднимает меч; замах двумя руками и удар сверху (казнь);
 * горизонтальный взмах (эндермен пополам); вытаскивает меч из земли во сне; раскидывает руки и
 * запрокидывает голову, превращаясь в Пустого. В бою с Зангецу — тяжёлые взмахи всем корпусом (три удара по очереди),
 * рывок, когда меч рубит сам, и Гецуга: замах высоко за правое плечо, левая рука указывает цель — и взмах по горизонтали.
 */
public final class PlayerPoses {
    /** На сколько градусов запястье подаёт клинок вперёд в ударе. */
    public static final float BLADE_FLIP = 45.0F;
    /** Угол между рукоятью и осью руки в покое (см. поворот в модели предмета). */
    private static final float GRIP_TO_ARM = 80.0F;
    /** Кулак от плеча вдоль руки и расстояние между кулаками на рукояти (пиксели модели). */
    private static final float FIST_Y = 8.6F;
    private static final float SECOND_HAND = 3.6F;

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

    /**
     * Казнь: обе руки на рукояти. Замах — кулаки перед лбом, клинок поднят над головой; резкий удар вперёд и вниз; пауза.
     * Правая рука идёт по двум точкам, левая каждый кадр ложится на рукоять за ней.
     */
    private static void execute(BipedEntityModel<?> model, float t) {
        float wind = phase(t, 0.0F, 0.4F);
        float strike = phase(t, 0.42F, 0.52F);
        float recover = phase(t, 0.8F, 1.0F);
        float[] up = aim(model.rightArm, 0.0F, -3.0F, -4.6F), down = aim(model.rightArm, -0.6F, 6.5F, -4.8F);
        float pitch = MathHelper.lerp(strike, MathHelper.lerp(wind, model.rightArm.pitch, up[0]), down[0]);
        float yaw = MathHelper.lerp(strike, MathHelper.lerp(wind, model.rightArm.yaw, up[1]), down[1]);
        model.rightArm.pitch = MathHelper.lerp(recover, pitch, 0.0F);
        model.rightArm.yaw = MathHelper.lerp(recover, yaw, 0.0F);
        model.rightArm.roll = 0.0F;
        leftHandOnHilt(model, flip(AnimationPayload.EXECUTE, t), wind * (1.0F - recover));
        model.body.pitch = (-0.18F * wind * (1.0F - strike) + 0.35F * strike) * (1.0F - recover);
        model.head.pitch = 0.25F * strike * (1.0F - recover);
    }

    /** Питч и рыск руки, чтобы кулак смотрел из плеча в точку (x, y, z) модели. */
    private static float[] aim(ModelPart arm, float x, float y, float z) {
        float dx = x - arm.pivotX, dy = y - arm.pivotY, dz = z - arm.pivotZ;
        return new float[]{(float) Math.atan2(-MathHelper.sqrt(dx * dx + dz * dz), dy), (float) Math.atan2(-dx, -dz)};
    }

    /** Левая рука ложится на рукоять за правым кулаком, ближе к навершию ({@code k} — насколько). */
    private static void leftHandOnHilt(BipedEntityModel<?> model, float flip, float k) {
        Quaternionf right = new Quaternionf().rotationZYX(model.rightArm.roll, model.rightArm.yaw, model.rightArm.pitch);
        float tilt = (GRIP_TO_ARM - BLADE_FLIP * flip) * MathHelper.RADIANS_PER_DEGREE;
        Vector3f hilt = right.transform(new Vector3f(0.0F, MathHelper.cos(tilt), -MathHelper.sin(tilt)));
        Vector3f fist = right.transform(new Vector3f(-1.0F, FIST_Y, 0.0F)).add(model.rightArm.pivotX, model.rightArm.pivotY, model.rightArm.pivotZ);
        Vector3f target = fist.sub(hilt.mul(SECOND_HAND));
        float[] pose = aim(model.leftArm, target.x, target.y, target.z);
        model.leftArm.pitch = MathHelper.lerp(k, model.leftArm.pitch, pose[0]);
        model.leftArm.yaw = MathHelper.lerp(k, model.leftArm.yaw, pose[1]);
        model.leftArm.roll = MathHelper.lerp(k, model.leftArm.roll, 0.0F);
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
     * Насколько запястье подало клинок вперёд (0 — лежит лезвием вниз, 1 — по ходу удара): в ударе мечом, рывке,
     * Гецуге и сценах с мечом. Рукоять при этом остаётся в кулаке, поэтому поворот невелик: дальше рукоять легла бы
     * вдоль руки.
     */
    public static float bladeForward(PlayerEntity player) {
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        ClientState.Animation animation = ClientState.animation(player);
        if (animation == null) {
            float swing = player.getHandSwingProgress(tickDelta);
            return swing <= 0.0F || swing >= 1.0F ? 0.0F : phase(swing, 0.12F, 0.3F) * (1.0F - phase(swing, 0.62F, 1.0F));
        }
        return flip(animation.type(), animation.progress(player, tickDelta));
    }

    private static float flip(int animation, float t) {
        if (t <= 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        return switch (animation) {
            case AnimationPayload.AUTO_SLASH, AnimationPayload.GETSUGA -> phase(t, 0.2F, 0.38F) * (1.0F - phase(t, 0.62F, 1.0F));
            case AnimationPayload.EXECUTE, AnimationPayload.SLASH -> phase(t, 0.42F, 0.52F) * (1.0F - phase(t, 0.8F, 1.0F));
            default -> 0.0F;
        };
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
