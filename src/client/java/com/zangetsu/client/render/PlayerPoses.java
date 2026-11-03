package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Позы игрока в сценах: тянется к рукояти и поднимает меч; замах двумя руками и удар сверху (казнь);
 * горизонтальный взмах (эндермен пополам); вытаскивает меч из земли во сне; раскидывает руки и
 * запрокидывает голову, превращаясь в Пустого. В бою с Зангецу — приёмы из {@link SwordMoves}
 * и Гецуга: замах высоко за правое плечо, левая рука указывает цель — и взмах по горизонтали.
 */
public final class PlayerPoses {
    /** На сколько градусов запястье подаёт клинок вперёд в ударе. */
    public static final float BLADE_FLIP = 45.0F;
    /** Угол между рукоятью и осью руки в покое (см. поворот в модели предмета). */
    private static final float GRIP_TO_ARM = 80.0F;
    /** Кулак от плеча вдоль руки и расстояние между кулаками на рукояти (пиксели модели). */
    private static final float FIST_Y = 8.6F;
    private static final float SECOND_HAND = 3.6F;
    /** На сколько рука может «согнуться» и вытянуться (доля длины): рука в модели не гнётся, поэтому укорачивается. */
    private static final float MIN_REACH = 0.72F, MAX_REACH = 1.12F;
    private static final float[] ZERO = new float[SwordMoves.SIZE];

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
        return animation == null ? 0.0F : flip(animation.type(), animation.progress(player, tickDelta));
    }

    private static float flip(int animation, float t) {
        if (t <= 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        return switch (animation) {
            case AnimationPayload.GETSUGA -> phase(t, 0.2F, 0.38F) * (1.0F - phase(t, 0.62F, 1.0F));
            case AnimationPayload.EXECUTE, AnimationPayload.SLASH -> phase(t, 0.42F, 0.52F) * (1.0F - phase(t, 0.8F, 1.0F));
            default -> 0.0F;
        };
    }

    // ---------------------------------------------------------------- бой

    /** Где сейчас меч в руке идущего удара: кулак (пиксели модели), куда смотрят клинок и лезвие. */
    public record SwordPose(Vector3f fist, Vector3f blade, Vector3f edge, int color, float trail) {
    }

    private static final Int2ObjectOpenHashMap<SwordPose> SWORD_POSES = new Int2ObjectOpenHashMap<>();

    /** Поза меча, которую нужно нарисовать вместо обычной (null — обычная). */
    public static SwordPose sword(LivingEntity player) {
        return SWORD_POSES.get(player.getId());
    }

    /**
     * Идущий приём ({@link SwordMoves}): сплайн даёт кулак и клинок, руки сами приходят к мечу. Правая рука смотрит на кулак и
     * чуть укорачивается или вытягивается (так выглядит согнутый локоть у негнущейся руки), левая в двуручных приёмах ложится на
     * рукоять за правой. Корпус, плечи и ноги идут за ударом. У левши всё зеркально.
     */
    public static void swing(BipedEntityModel<?> m, LivingEntity player, float time) {
        ClientState.Swing swing = ClientState.swing(player, time);
        if (swing == null) {
            SWORD_POSES.remove(player.getId());
            return;
        }
        SwordMoves.Move move = SwordMoves.ALL[swing.move];
        float t = MathHelper.clamp(swing.progress(time), 0.0F, 1.0F);
        boolean lefty = player.getMainArm() == Arm.LEFT;
        float mx = lefty ? -1.0F : 1.0F;
        ModelPart main = lefty ? m.leftArm : m.rightArm, off = lefty ? m.rightArm : m.leftArm;
        float[] rest = idle(main, mx, m);
        float[] v = SwordMoves.sample(move.body(), t, rest);
        float weight = SwordMoves.smooth(0.0F, 0.1F, t) * (1.0F - SwordMoves.smooth(0.86F, 1.0F, t));

        float bodyYaw = v[SwordMoves.YAW] * mx;
        m.body.yaw = bodyYaw;
        m.body.pitch = v[SwordMoves.PITCH];
        // Плечи идут за корпусом, наклон — как у приседа: иначе пояс разъедется с ногами.
        float lean = MathHelper.clamp(v[SwordMoves.PITCH] / 0.5F, 0.0F, 1.0F);
        m.rightArm.pivotZ = MathHelper.sin(bodyYaw) * 5.0F;
        m.rightArm.pivotX = -MathHelper.cos(bodyYaw) * 5.0F;
        m.leftArm.pivotZ = -MathHelper.sin(bodyYaw) * 5.0F;
        m.leftArm.pivotX = MathHelper.cos(bodyYaw) * 5.0F;
        m.rightArm.pivotY = m.leftArm.pivotY = 2.0F + 3.2F * lean;
        m.body.pivotY = 3.2F * lean;
        m.head.pivotY = 4.2F * lean;
        m.head.pitch += v[SwordMoves.PITCH] * 0.3F;
        m.rightLeg.pivotZ = m.leftLeg.pivotZ = 4.0F * lean;
        m.rightLeg.pivotY = m.leftLeg.pivotY = 12.0F + 0.2F * lean;
        (lefty ? m.leftLeg : m.rightLeg).pitch += v[SwordMoves.LEG_R];
        (lefty ? m.rightLeg : m.leftLeg).pitch += v[SwordMoves.LEG_L];
        m.hat.copyTransform(m.head);

        // Кулак и меч: у рывка к цели весь приём повёрнут в её сторону.
        Vector3f target = turned(mx * v[SwordMoves.FX], v[SwordMoves.FX + 1], v[SwordMoves.FX + 2], swing.aim);
        Vector3f blade = turned(mx * v[SwordMoves.BX], v[SwordMoves.BX + 1], v[SwordMoves.BX + 2], swing.aim).normalize();
        Vector3f edge = turned(mx * v[SwordMoves.EX], v[SwordMoves.EX + 1], v[SwordMoves.EX + 2], swing.aim);
        edge.sub(new Vector3f(blade).mul(edge.dot(blade))).normalize();

        // Плечо смотрит на кулак; кисть смещена от оси руки на пиксель.
        float handX = lefty ? 1.0F : -1.0F;
        float[] aimed = aim(main, target.x - handX, target.y, target.z);
        float dx = target.x - handX - main.pivotX, dy = target.y - main.pivotY, dz = target.z - main.pivotZ;
        float reach = MathHelper.clamp(MathHelper.sqrt(dx * dx + dy * dy + dz * dz) / FIST_Y, MIN_REACH, MAX_REACH);
        main.pitch = angleLerp(weight, main.pitch, aimed[0]);
        main.yaw = angleLerp(weight, main.yaw, aimed[1]);
        main.roll = MathHelper.lerp(weight, main.roll, 0.0F);
        main.yScale = MathHelper.lerp(weight, 1.0F, reach);
        Quaternionf arm = new Quaternionf().rotationZYX(main.roll, main.yaw, main.pitch);
        Vector3f fist = arm.transform(new Vector3f(handX, FIST_Y * main.yScale, 0.0F)).add(main.pivotX, main.pivotY, main.pivotZ);
        SWORD_POSES.put(player.getId(), new SwordPose(fist, blade, edge, swing.color, SwordMoves.trail(move, t)));

        // Свободная рука уходит в противовес; в двуручных — ложится на рукоять.
        float two = MathHelper.clamp(v[SwordMoves.TWO], 0.0F, 1.0F);
        float freePitch = MathHelper.lerp(weight, off.pitch, -0.3F - 0.3F * Math.abs(v[SwordMoves.YAW]));
        float freeYaw = MathHelper.lerp(weight, off.yaw, bodyYaw * 0.6F);
        float freeRoll = MathHelper.lerp(weight, off.roll, -0.35F * mx);
        Vector3f hold = new Vector3f(fist).sub(new Vector3f(blade).mul(SECOND_HAND));
        float[] onHilt = aim(off, hold.x + handX, hold.y, hold.z);
        float hx = hold.x + handX - off.pivotX, hy = hold.y - off.pivotY, hz = hold.z - off.pivotZ;
        off.pitch = angleLerp(two, freePitch, onHilt[0]);
        off.yaw = angleLerp(two, freeYaw, onHilt[1]);
        off.roll = MathHelper.lerp(two, freeRoll, 0.0F);
        off.yScale = MathHelper.lerp(two, 1.0F, MathHelper.clamp(MathHelper.sqrt(hx * hx + hy * hy + hz * hz) / FIST_Y, MIN_REACH, 1.25F));
    }

    /** Поза покоя в координатах приёма: где сейчас кулак и меч у опущенной руки. */
    private static float[] idle(ModelPart main, float mx, BipedEntityModel<?> m) {
        Quaternionf arm = new Quaternionf().rotationZYX(main.roll, main.yaw, main.pitch);
        Vector3f fist = arm.transform(new Vector3f(-mx, FIST_Y, 0.0F)).add(main.pivotX, main.pivotY, main.pivotZ);
        float tilt = GRIP_TO_ARM * MathHelper.RADIANS_PER_DEGREE;
        Vector3f blade = arm.transform(new Vector3f(0.0F, MathHelper.cos(tilt), -MathHelper.sin(tilt)));
        Vector3f edge = arm.transform(new Vector3f(0.0F, MathHelper.sin(tilt), MathHelper.cos(tilt)));
        float[] rest = new float[SwordMoves.SIZE];
        rest[SwordMoves.FX] = mx * fist.x;
        rest[SwordMoves.FX + 1] = fist.y;
        rest[SwordMoves.FX + 2] = fist.z;
        rest[SwordMoves.BX] = mx * blade.x;
        rest[SwordMoves.BX + 1] = blade.y;
        rest[SwordMoves.BX + 2] = blade.z;
        rest[SwordMoves.EX] = mx * edge.x;
        rest[SwordMoves.EX + 1] = edge.y;
        rest[SwordMoves.EX + 2] = edge.z;
        rest[SwordMoves.YAW] = mx * m.body.yaw;
        rest[SwordMoves.PITCH] = m.body.pitch;
        return rest;
    }

    /** Повернуть вокруг вертикали на {@code aim} радиан вправо: рывок к цели. */
    private static Vector3f turned(float x, float y, float z, float aim) {
        if (aim == 0.0F) {
            return new Vector3f(x, y, z);
        }
        float c = MathHelper.cos(aim), s = MathHelper.sin(aim);
        return new Vector3f(x * c + z * s, y, -x * s + z * c);
    }

    private static float angleLerp(float k, float from, float to) {
        float d = to - from;
        while (d > MathHelper.PI) {
            d -= MathHelper.TAU;
        }
        while (d < -MathHelper.PI) {
            d += MathHelper.TAU;
        }
        return from + d * k;
    }

    /** Поворот всего тела в приёме (градусы) и присед (пиксели): {разворот, присед}. */
    public static float[] root(LivingEntity player, float time) {
        ClientState.Swing swing = ClientState.swing(player, time);
        if (swing == null) {
            return null;
        }
        float[] v = SwordMoves.sample(SwordMoves.ALL[swing.move].body(), MathHelper.clamp(swing.progress(time), 0.0F, 1.0F), ZERO);
        return new float[]{v[SwordMoves.SPIN], v[SwordMoves.DIP]};
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
