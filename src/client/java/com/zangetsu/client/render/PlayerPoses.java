package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.network.AnimationPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Позы игрока в сценах: тянется к рукояти и поднимает меч; вытаскивает меч из земли во сне; превращается в
 * Пустого с настоящими локтями и коленями. В бою с Зангецу и в казнях — приёмы из {@link SwordMoves}
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
    /** Наклон корпуса в сцене подбора: на подходе и в момент хвата. */
    private static final float CLAIM_LEAN_WALK = 0.12F, CLAIM_LEAN_GRIP = 0.22F;

    private PlayerPoses() {
    }

    public static void apply(BipedEntityModel<?> model, LivingEntity player, float animationProgress) {
        BENDS.remove(player.getId());
        ClientState.Animation animation = ClientState.animation(player);
        if (animation == null) {
            return;
        }
        float t = MathHelper.clamp((animationProgress - animation.startAge()) / Math.max(1, animation.duration()), 0.0F, 1.0F);
        switch (animation.type()) {
            case AnimationPayload.TOUCH_HANDLE -> claim(model, player, animation, animationProgress);
            case AnimationPayload.DREAM_PULL -> dreamPull(model, player, animation, animationProgress);
            case AnimationPayload.HOLLOW -> maskTouch(model, player, t);
            case AnimationPayload.HOLLOW_FINAL -> rite(model, player, t, animationProgress);
            case AnimationPayload.GETSUGA -> getsuga(model, t, animationProgress);
            default -> {
            }
        }
        model.hat.copyTransform(model.head);
        hideBent(model, player);
    }

    // ---------------------------------------------------------------- суставы

    private static final Int2ObjectOpenHashMap<float[]> BENDS = new Int2ObjectOpenHashMap<>();
    /** Индексы сгибов у игрока. */
    private static final int RIGHT_ARM = 0, LEFT_ARM = 1, RIGHT_LEG = 2, LEFT_LEG = 3;
    /** Меньше этого угла (радианы) конечность не режем: она и так прямая. */
    static final float MIN_BEND = 0.02F;
    /** Длина плеча и предплечья до ладони (пиксели модели). */
    private static final float UPPER_ARM = 4.0F, FOREARM = 4.6F;
    /** Вся нога (бедро и голень поровну), где земля в координатах модели и насколько центр лежащей на земле голени выше земли. */
    private static final float LEG_LENGTH = 12.0F, LEG_GROUND = 24.0F, SHIN_ON_GROUND = 2.2F;

    /** Сгибы игрока в этот кадр (четыре угла и четыре признака верхнего слоя скина) или null, если всё прямое. */
    public static float[] bends(LivingEntity player) {
        return BENDS.get(player.getId());
    }

    private static void bend(LivingEntity player, int limb, float angle) {
        BENDS.computeIfAbsent(player.getId(), id -> new float[8])[limb] = angle;
    }

    /** Согнутая конечность рисуется отдельно ({@link BentLimbs}); в обычной модели её не должно быть. */
    private static void hideBent(BipedEntityModel<?> model, LivingEntity player) {
        float[] bends = BENDS.get(player.getId());
        if (bends == null || !(model instanceof PlayerEntityModel<?> skin)) {
            return;
        }
        hide(bends, RIGHT_ARM, skin.rightArm, skin.rightSleeve);
        hide(bends, LEFT_ARM, skin.leftArm, skin.leftSleeve);
        hide(bends, RIGHT_LEG, skin.rightLeg, skin.rightPants);
        hide(bends, LEFT_LEG, skin.leftLeg, skin.leftPants);
    }

    private static void hide(float[] bends, int limb, ModelPart part, ModelPart overlay) {
        if (Math.abs(bends[limb]) >= MIN_BEND) {
            bends[4 + limb] = overlay.visible ? 1.0F : 0.0F;
            part.visible = false;
            overlay.visible = false;
        }
    }

    /**
     * Рука дотягивается ладонью до точки (пиксели модели) с настоящим локтем, вес {@code k}: плечо смотрит мимо цели в сторону локтя, предплечье
     * сгибается к ней. Локоть уходит наружу и вниз, поэтому {@code side} — на какой стороне рука.
     */
    private static void reach(LivingEntity player, ModelPart arm, int limb, float side, Vector3f target, float k) {
        Vector3f shoulder = new Vector3f(arm.pivotX, arm.pivotY, arm.pivotZ);
        Vector3f toTarget = new Vector3f(target).sub(side, 0.0F, 0.0F).sub(shoulder);
        float distance = MathHelper.clamp(toTarget.length(), Math.abs(UPPER_ARM - FOREARM) + 0.2F, UPPER_ARM + FOREARM - 0.02F);
        Vector3f direction = toTarget.normalize();
        Vector3f pole = new Vector3f(side * 1.6F, 0.9F, 0.2F);
        pole.sub(new Vector3f(direction).mul(pole.dot(direction))).normalize();
        float cosShoulder = (UPPER_ARM * UPPER_ARM + distance * distance - FOREARM * FOREARM) / (2.0F * UPPER_ARM * distance);
        float angleShoulder = (float) Math.acos(MathHelper.clamp(cosShoulder, -1.0F, 1.0F));
        Vector3f upper = new Vector3f(direction).mul(MathHelper.cos(angleShoulder)).add(new Vector3f(pole).mul(MathHelper.sin(angleShoulder)));
        Vector3f elbow = new Vector3f(upper).mul(UPPER_ARM);
        Vector3f forearm = new Vector3f(direction).mul(distance).sub(elbow).normalize();
        float dot = MathHelper.clamp(forearm.dot(upper), -1.0F, 1.0F);
        Vector3f toward = new Vector3f(forearm).sub(new Vector3f(upper).mul(dot));
        if (toward.lengthSquared() < 1.0E-5F) {
            toward.set(pole).negate();
        }
        toward.normalize();
        // Ось руки — y, сгиб вперёд — поворот вокруг x с минусом, значит −z смотрит туда, куда сгибается предплечье.
        Vector3f axisZ = new Vector3f(toward).negate();
        Vector3f axisX = new Vector3f(upper).cross(axisZ).normalize();
        Quaternionf rotation = new Quaternionf().setFromNormalized(new Matrix3f(axisX, upper, axisZ));
        Vector3f angles = rotation.getEulerAnglesZYX(new Vector3f());
        arm.pitch = angleLerp(k, arm.pitch, angles.x);
        arm.yaw = angleLerp(k, arm.yaw, angles.y);
        arm.roll = angleLerp(k, arm.roll, angles.z);
        arm.yScale = MathHelper.lerp(k, arm.yScale, 1.0F);
        bend(player, limb, -(float) Math.acos(dot) * k);
    }

    /**
     * Нога ставит ступню в точку (пиксели модели, в плоскости ноги): бедро и голень равной длины, колено смотрит вперёд. Ступня стоит там, где сказано,
     * как бы ни двигалось тело: поэтому она не скользит и не проваливается, а нога сама сгибается ровно настолько, насколько нужно. Вес {@code k}.
     */
    private static void plantFoot(LivingEntity player, ModelPart leg, int limb, float footY, float footZ, float k) {
        float dy = footY - leg.pivotY, dz = footZ - leg.pivotZ;
        float distance = Math.min(MathHelper.sqrt(dy * dy + dz * dz), LEG_LENGTH - 0.02F);
        float half = (float) Math.acos(distance / LEG_LENGTH);
        float direction = (float) Math.atan2(dz, dy);
        leg.pitch = MathHelper.lerp(k, leg.pitch, direction - half);
        leg.yaw = MathHelper.lerp(k, leg.yaw, 0.0F);
        leg.roll = MathHelper.lerp(k, leg.roll, 0.0F);
        bend(player, limb, 2.0F * half * k);
    }

    // ---------------------------------------------------------------- превращение

    /**
     * Уровень IV, как в аниме: голова опускается, свободная рука поднимается ко лбу и медленно проводит по лицу до подбородка, за ладонью проступает маска.
     * Затем рука хлёстко уходит в сторону: плечо первым, предплечье догоняет и распрямляется с перехлёстом, а голова в этот миг поднимается и загораются глаза.
     * Он так и стоит мгновение с отброшенной рукой, потом спокойно опускает её.
     */
    private static void maskTouch(BipedEntityModel<?> m, LivingEntity player, float t) {
        boolean lefty = player.getMainArm() == Arm.LEFT;
        ModelPart off = lefty ? m.rightArm : m.leftArm;
        int limb = lefty ? RIGHT_ARM : LEFT_ARM;
        float side = lefty ? -1.0F : 1.0F;
        float lift = phase(t, 0.02F, 0.2F), whip = phase(t, 0.6F, 0.655F), settle = phase(t, 0.86F, 1.0F);
        float restPitch = off.pitch, restYaw = off.yaw, restRoll = off.roll;

        float headPitch = m.head.pitch, headYaw = m.head.yaw;
        float look = MathHelper.lerp(phase(t, 0.6F, 0.66F), 0.3F, -0.06F);
        float headWeight = lift * (1.0F - settle);
        m.head.pitch = MathHelper.lerp(headWeight, headPitch, look);
        m.head.yaw = MathHelper.lerp(headWeight, headYaw, 0.0F);
        m.body.pitch += 0.12F * lift * (1.0F - whip);

        // Ладонь — в координатах головы: ото лба к подбородку перед своей половиной лица, чтобы вторая половина была видна.
        float swipe = HollowRite.swipe(t);
        Vector3f palm = new Vector3f(side * MathHelper.lerp(swipe, 3.0F, 2.2F), MathHelper.lerp(swipe, -8.2F, 3.0F), -5.8F);
        new Quaternionf().rotationZYX(m.head.roll, m.head.yaw, m.head.pitch).transform(palm).add(m.head.pivotX, m.head.pivotY, m.head.pivotZ);
        reach(player, off, limb, side, palm, lift);
        if (whip > 0.0F) {
            float forearm = phase(t, 0.615F, 0.675F);
            float overshoot = MathHelper.sin(phase(t, 0.655F, 0.73F) * MathHelper.PI) * 0.22F;
            off.pitch = angleLerp(whip, off.pitch, -0.08F);
            off.yaw = angleLerp(whip, off.yaw, 0.0F);
            off.roll = angleLerp(whip, off.roll, -side * (1.42F + overshoot));
            bend(player, limb, bends(player)[limb] * (1.0F - forearm));
        }
        if (settle > 0.0F) {
            off.pitch = angleLerp(settle, off.pitch, restPitch);
            off.yaw = angleLerp(settle, off.yaw, restYaw);
            off.roll = angleLerp(settle, off.roll, restRoll);
        }
    }

    /**
     * Уровень V, всё превращение: злится и дрожит, молния вбивает его на одно колено с мечом в земле, он поднимается, забрасывает меч на плечо и бьёт выпадом.
     * Меч и корпус идут по {@link SwordMoves#RITE}, голова и свободная рука дополняются здесь, а ноги ставят ступни на землю сами.
     */
    private static void rite(BipedEntityModel<?> m, LivingEntity player, float t, float time) {
        float tick = t * HollowForm.RITE_TICKS;
        boolean lefty = player.getMainArm() == Arm.LEFT;
        float headPitchLook = m.head.pitch, headYawLook = m.head.yaw;
        applyMove(m, player, SwordMoves.RITE, t, 0.0F, SlashTrails.BLOOD, null, 0.0F);
        ModelPart off = lefty ? m.rightArm : m.leftArm;
        ModelPart offLeg = lefty ? m.rightLeg : m.leftLeg, mainLeg = lefty ? m.leftLeg : m.rightLeg;
        int offLegIndex = lefty ? RIGHT_LEG : LEFT_LEG, mainLegIndex = lefty ? LEFT_LEG : RIGHT_LEG;
        float side = lefty ? -1.0F : 1.0F;

        float rage = phase(tick, 2.0F, 30.0F) * (1.0F - phase(tick, 40.0F, 43.0F));
        float kneel = phase(tick, 40.0F, 43.0F) * (1.0F - phase(tick, 90.0F, 114.0F));
        float shake = MathHelper.sin(time * 3.7F) * 0.04F * rage;

        // Голова: опущена от злости, запрокинута в крике, на колене склонена, поднимается последней; вид сверху вниз на врага перед выпадом.
        float head = 0.38F * phase(tick, 0.0F, 12.0F);
        head = MathHelper.lerp(phase(tick, 26.0F, 36.0F), head, -0.55F);
        head = MathHelper.lerp(phase(tick, 40.0F, 44.0F), head, 0.55F);
        head = MathHelper.lerp(phase(tick, 104.0F, 116.0F), head, -0.12F);
        head = MathHelper.lerp(phase(tick, 150.0F, 156.0F), head, 0.08F);
        float weight = phase(tick, 0.0F, 8.0F) * (1.0F - phase(tick, 168.0F, 176.0F));
        m.head.pitch = MathHelper.lerp(weight, headPitchLook, head + shake);
        m.head.yaw = MathHelper.lerp(weight, headYawLook, shake * 0.7F);

        // Свободная рука в злости сжата и отведена.
        if (rage > 0.0F) {
            off.roll = MathHelper.lerp(rage, off.roll, -side * 0.42F) + shake;
            off.pitch = MathHelper.lerp(rage, off.pitch, -0.22F) + shake;
        }
        float dip = SwordMoves.sample(SwordMoves.RITE.body(), t, ZERO)[SwordMoves.DIP];
        float ground = LEG_GROUND - dip;
        // Колено: передняя нога стоит на ступне, задняя — на колене, голень лежит на земле позади. Рука опирается на переднее колено.
        if (kneel > 0.0F) {
            float k = Math.min(1.0F, kneel * 5.0F);
            float hip = offLeg.pivotZ;
            plantFoot(player, offLeg, offLegIndex, ground, hip - 6.5F * kneel, k);
            plantFoot(player, mainLeg, mainLegIndex, ground - SHIN_ON_GROUND * kneel, hip + 6.6F * kneel, k);
            Vector3f knee = new Vector3f(offLeg.pivotX, offLeg.pivotY + 6.0F * MathHelper.cos(offLeg.pitch) - 1.6F, offLeg.pivotZ + 6.0F * MathHelper.sin(offLeg.pitch));
            reach(player, off, lefty ? RIGHT_ARM : LEFT_ARM, side, knee, kneel);
        }
        // Выпад: ведущая нога широким шагом вперёд, колено над ступнёй; задняя вытянута назад. Обе ступни на земле.
        float lunge = phase(tick, HollowForm.RITE_LUNGE - 5.0F, HollowForm.RITE_LUNGE) * (1.0F - phase(tick, 166.0F, 176.0F));
        if (lunge > 0.0F) {
            float k = Math.min(1.0F, lunge * 5.0F);
            float hip = mainLeg.pivotZ;
            plantFoot(player, mainLeg, mainLegIndex, ground, hip - 5.1F * lunge, k);
            plantFoot(player, offLeg, offLegIndex, ground, hip + 8.0F * lunge, k);
        }
    }

    // ---------------------------------------------------------------- подбор меча

    /** Где меч в земле сейчас держится за рукоять: для каждого игрока запоминается, потому что сам меч в руке уже исчезнет. */
    private static final Int2ObjectOpenHashMap<float[]> GRIPS = new Int2ObjectOpenHashMap<>();

    /** Кулак, клинок и лезвие воткнутого меча в координатах модели игрока (или последнее, что о них известно). */
    private static float[] grip(LivingEntity player, int swordId, float time) {
        if (player.getWorld().getEntityById(swordId) instanceof ThrownZangetsuEntity sword) {
            GRIPS.put(player.getId(), ThrownZangetsuRenderer.gripInModel(sword, player, time - player.age));
        }
        return GRIPS.get(player.getId());
    }

    /**
     * Меч выбрал его. Всё идёт одним непрерывным движением, без стыков между частями: подходит, клонясь вперёд; рука плавно тянется к рукояти и сжимает её; колени сгибаются,
     * вторая рука ложится на рукоять; ноги распрямляются и выносят клинок из земли; большой дугой через сторону меч ложится на плечо; свободная рука упирается в бок, он смотрит
     * на меч, коротко кивает: да, это мой меч. Потом спокойно смотрит вперёд и опускает меч. Ступни всё это время стоят на земле.
     */
    private static void claim(BipedEntityModel<?> m, LivingEntity player, ClientState.Animation animation, float time) {
        float sinceStart = time - animation.startAge();
        float tick = sinceStart - (animation.duration() - Awakening.CLAIM_TICKS);
        float headPitch = m.head.pitch, headYaw = m.head.yaw;
        boolean lefty = player.getMainArm() == Arm.LEFT;
        float mx = lefty ? -1.0F : 1.0F;
        ModelPart main = lefty ? m.leftArm : m.rightArm, off = lefty ? m.rightArm : m.leftArm;
        ModelPart mainLeg = lefty ? m.leftLeg : m.rightLeg, offLeg = lefty ? m.rightLeg : m.leftLeg;
        int mainLimb = lefty ? LEFT_ARM : RIGHT_ARM, offLimb = lefty ? RIGHT_ARM : LEFT_ARM;
        int mainLegLimb = lefty ? LEFT_LEG : RIGHT_LEG, offLegLimb = lefty ? RIGHT_LEG : LEFT_LEG;

        // Наклон начинается с первого шага и растёт до хвата; приём подхватывает его ровно с этого значения.
        leanBody(m, MathHelper.lerp(phase(tick, 0.0F, Awakening.CLAIM_GRIP - 2.0F), CLAIM_LEAN_WALK, CLAIM_LEAN_GRIP) * phase(sinceStart, 0.0F, 10.0F), 0.0F);
        float[] grip = grip(player, animation.param(), time);
        float t = MathHelper.clamp((tick - Awakening.CLAIM_GRIP) / (Awakening.CLAIM_TICKS - Awakening.CLAIM_GRIP), 0.0F, 1.0F);
        if (tick >= Awakening.CLAIM_GRIP) {
            // Первые тики меч в руке стоит ровно там, где стоял в земле: подмены не видно.
            applyMove(m, player, SwordMoves.CLAIM, t, 0.0F, SlashTrails.STEEL, grip, 1.0F - phase(tick, Awakening.CLAIM_GRIP, Awakening.CLAIM_GRIP + 8.0F));
        } else if (tick > 0.0F && grip != null) {
            reach(player, main, mainLimb, -mx, new Vector3f(grip[0], grip[1], grip[2]), phase(tick, 0.0F, Awakening.CLAIM_REACH));
        }

        // Ступни на земле с момента, как он остановился: ноги сгибаются сами, а опорная нога чуть впереди.
        float stance = phase(tick, 0.0F, 10.0F) * (1.0F - phase(tick, Awakening.CLAIM_HOLD_END + 2.0F, Awakening.CLAIM_TICKS - 2.0F));
        if (stance > 0.0F) {
            float dip = tick < Awakening.CLAIM_GRIP ? 0.0F : SwordMoves.sample(SwordMoves.CLAIM.body(), t, ZERO)[SwordMoves.DIP];
            float spread = phase(tick, 14.0F, Awakening.CLAIM_GRIP + 10.0F);
            plantFoot(player, offLeg, offLegLimb, LEG_GROUND - dip, -1.9F * spread, stance);
            plantFoot(player, mainLeg, mainLegLimb, LEG_GROUND - dip, 2.1F * spread, stance);
        }

        // Свободная рука упирается в бок.
        float proud = phase(tick, Awakening.CLAIM_SHOULDER - 2.0F, Awakening.CLAIM_SHOULDER + 10.0F) * (1.0F - phase(tick, Awakening.CLAIM_HOLD_END, Awakening.CLAIM_HOLD_END + 16.0F));
        if (proud > 0.0F) {
            reach(player, off, offLimb, mx, new Vector3f(mx * 5.4F, 10.0F, -1.0F), proud);
            m.rightLeg.roll = MathHelper.lerp(proud, m.rightLeg.roll, 0.1F);
            m.leftLeg.roll = MathHelper.lerp(proud, m.leftLeg.roll, -0.1F);
        }

        // Голова одна на всю сцену: опущена на рукоять, следит за клинком вверх, поворачивается к плечу, коротко кивает и поднимает подбородок.
        float head = MathHelper.lerp(phase(tick, 0.0F, 12.0F), 0.28F, 0.42F);
        head = MathHelper.lerp(phase(tick, 56.0F, 80.0F), head, -0.14F);
        head = MathHelper.lerp(phase(tick, 86.0F, 98.0F), head, 0.06F);
        head += MathHelper.sin(phase(tick, 110.0F, 122.0F) * MathHelper.PI) * 0.18F;
        head = MathHelper.lerp(phase(tick, 122.0F, 134.0F), head, -0.1F);
        float turn = 0.12F * phase(tick, 0.0F, 12.0F) * (1.0F - phase(tick, 50.0F, 64.0F));
        turn += 0.3F * phase(tick, 64.0F, 80.0F) * (1.0F - phase(tick, 84.0F, 94.0F));
        turn += 0.62F * phase(tick, 84.0F, 98.0F) * (1.0F - phase(tick, 122.0F, 134.0F));
        float weight = phase(sinceStart, 0.0F, 8.0F) * (1.0F - phase(tick, Awakening.CLAIM_HOLD_END + 6.0F, Awakening.CLAIM_TICKS));
        m.head.pitch = MathHelper.lerp(weight, headPitch, head);
        m.head.yaw = MathHelper.lerp(weight, headYaw, mx * turn);
    }

    /** Во сне: подходит, берётся за рукоять обеими руками и тянет; меч поднимается сам и уносит его из сна. */
    private static void dreamPull(BipedEntityModel<?> m, LivingEntity player, ClientState.Animation animation, float time) {
        float tick = time - animation.startAge() - (animation.duration() - Dream.PULL_DURATION);
        float headPitch = m.head.pitch;
        if (tick < 0.0F) {
            m.head.pitch = MathHelper.lerp(0.5F, headPitch, 0.2F);
            return;
        }
        boolean lefty = player.getMainArm() == Arm.LEFT;
        float mx = lefty ? -1.0F : 1.0F;
        float[] grip = grip(player, animation.param(), time);
        float k = phase(tick, 0.0F, 14.0F);
        if (grip != null) {
            Vector3f hold = new Vector3f(grip[0], grip[1], grip[2]);
            reach(player, lefty ? m.leftArm : m.rightArm, lefty ? LEFT_ARM : RIGHT_ARM, -mx, hold, k);
            reach(player, lefty ? m.rightArm : m.leftArm, lefty ? RIGHT_ARM : LEFT_ARM, mx, new Vector3f(hold).sub(grip[3] * SECOND_HAND, grip[4] * SECOND_HAND, grip[5] * SECOND_HAND), k);
        }
        m.body.pitch += 0.18F * k * (1.0F - phase(tick, 16.0F, 26.0F));
        m.head.pitch = MathHelper.lerp(k, headPitch, MathHelper.lerp(phase(tick, 16.0F, 28.0F), 0.42F, -0.4F));
    }

    private static float phase(float t, float from, float to) {
        float x = MathHelper.clamp((t - from) / (to - from), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Питч и рыск руки, чтобы кулак смотрел из плеча в точку (x, y, z) модели. */
    private static float[] aim(ModelPart arm, float x, float y, float z) {
        float dx = x - arm.pivotX, dy = y - arm.pivotY, dz = z - arm.pivotZ;
        return new float[]{(float) Math.atan2(-MathHelper.sqrt(dx * dx + dz * dz), dy), (float) Math.atan2(-dx, -dz)};
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
        applyMove(m, player, SwordMoves.ALL[swing.move], MathHelper.clamp(swing.progress(time), 0.0F, 1.0F), swing.aim, swing.color, null, 0.0F);
    }

    /** Поставить тело и меч в приём {@code move} в его момент {@code t}. */
    private static void applyMove(BipedEntityModel<?> m, LivingEntity player, SwordMoves.Move move, float t, float aim, int color, float[] from, float fromWeight) {
        boolean lefty = player.getMainArm() == Arm.LEFT;
        float mx = lefty ? -1.0F : 1.0F;
        ModelPart main = lefty ? m.leftArm : m.rightArm, off = lefty ? m.rightArm : m.leftArm;
        float[] rest = idle(main, mx, m);
        float[] v = SwordMoves.sample(move.body(), t, rest);
        // Меч начинает приём не из обычной позы, а оттуда, где он сейчас (из земли).
        if (from != null && fromWeight > 0.0F) {
            for (int i = 0; i < 9; i++) {
                float value = i % 3 == 0 ? mx * from[i] : from[i];
                v[SwordMoves.FX + i] = MathHelper.lerp(fromWeight, v[SwordMoves.FX + i], value);
            }
        }
        // Длинный обряд входит и выходит за те же тики, что и обычный удар, а не за ту же долю времени.
        boolean scene = move == SwordMoves.RITE || move == SwordMoves.CLAIM;
        float weight = (move == SwordMoves.CLAIM ? 1.0F : SwordMoves.smooth(0.0F, scene ? 0.03F : 0.1F, t))
                * (1.0F - SwordMoves.smooth(move == SwordMoves.CLAIM ? 0.9F : (scene ? 0.96F : 0.86F), 1.0F, t));

        float bodyYaw = v[SwordMoves.YAW] * mx;
        leanBody(m, v[SwordMoves.PITCH], bodyYaw);
        m.head.pitch += v[SwordMoves.PITCH] * 0.3F;
        (lefty ? m.leftLeg : m.rightLeg).pitch += v[SwordMoves.LEG_R];
        (lefty ? m.rightLeg : m.leftLeg).pitch += v[SwordMoves.LEG_L];
        m.hat.copyTransform(m.head);

        // Кулак и меч: у рывка к цели весь приём повёрнут в её сторону.
        Vector3f target = turned(mx * v[SwordMoves.FX], v[SwordMoves.FX + 1], v[SwordMoves.FX + 2], aim);
        Vector3f blade = turned(mx * v[SwordMoves.BX], v[SwordMoves.BX + 1], v[SwordMoves.BX + 2], aim).normalize();
        Vector3f edge = turned(mx * v[SwordMoves.EX], v[SwordMoves.EX + 1], v[SwordMoves.EX + 2], aim);
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
        // В сценах рука с настоящим локтем: кулак стоит точно на рукояти, а не тянется укорачиванием всей руки.
        if (scene) {
            fist.lerp(target, weight);
            reach(player, main, lefty ? LEFT_ARM : RIGHT_ARM, handX, target, weight);
        }
        SWORD_POSES.put(player.getId(), new SwordPose(fist, blade, edge, color, SwordMoves.trail(move, t)));

        // Свободная рука уходит в противовес; в двуручных — ложится на рукоять.
        float two = MathHelper.clamp(v[SwordMoves.TWO], 0.0F, 1.0F);
        // При подборе рука до этого просто висела: в противовес она уходит не сразу, а за несколько тиков.
        float free = move == SwordMoves.CLAIM ? weight * SwordMoves.smooth(0.0F, 0.12F, t) : weight;
        float freePitch = MathHelper.lerp(free, off.pitch, -0.3F - 0.3F * Math.abs(v[SwordMoves.YAW]));
        float freeYaw = MathHelper.lerp(free, off.yaw, bodyYaw * 0.6F);
        float freeRoll = MathHelper.lerp(free, off.roll, -0.35F * mx);
        Vector3f hold = new Vector3f(fist).sub(new Vector3f(blade).mul(SECOND_HAND));
        if (scene) {
            // В сценах и вторая рука с настоящим локтем: ладонь ложится на рукоять, рука не укорачивается.
            off.pitch = freePitch;
            off.yaw = freeYaw;
            off.roll = freeRoll;
            off.yScale = 1.0F;
            if (two > 0.0F) {
                reach(player, off, lefty ? RIGHT_ARM : LEFT_ARM, -handX, hold, two);
            }
            return;
        }
        float[] onHilt = aim(off, hold.x + handX, hold.y, hold.z);
        float hx = hold.x + handX - off.pivotX, hy = hold.y - off.pivotY, hz = hold.z - off.pivotZ;
        off.pitch = angleLerp(two, freePitch, onHilt[0]);
        off.yaw = angleLerp(two, freeYaw, onHilt[1]);
        off.roll = MathHelper.lerp(two, freeRoll, 0.0F);
        off.yScale = MathHelper.lerp(two, 1.0F, MathHelper.clamp(MathHelper.sqrt(hx * hx + hy * hy + hz * hz) / FIST_Y, MIN_REACH, 1.25F));
    }

    /** Наклон и поворот корпуса: плечи и голова идут за ним, бёдра уходят назад, как у приседа, иначе пояс разъедется с ногами. */
    private static void leanBody(BipedEntityModel<?> m, float pitch, float bodyYaw) {
        m.body.yaw = bodyYaw;
        m.body.pitch = pitch;
        float lean = MathHelper.clamp(pitch / 0.5F, 0.0F, 1.0F);
        m.rightArm.pivotZ = MathHelper.sin(bodyYaw) * 5.0F;
        m.rightArm.pivotX = -MathHelper.cos(bodyYaw) * 5.0F;
        m.leftArm.pivotZ = -MathHelper.sin(bodyYaw) * 5.0F;
        m.leftArm.pivotX = MathHelper.cos(bodyYaw) * 5.0F;
        m.rightArm.pivotY = m.leftArm.pivotY = 2.0F + 3.2F * lean;
        m.body.pivotY = 3.2F * lean;
        m.head.pivotY = 4.2F * lean;
        m.rightLeg.pivotZ = m.leftLeg.pivotZ = 4.0F * lean;
        m.rightLeg.pivotY = m.leftLeg.pivotY = 12.0F + 0.2F * lean;
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
        ClientState.Animation animation = ClientState.animation(player);
        if (animation != null && animation.type() == AnimationPayload.HOLLOW_FINAL) {
            float t = MathHelper.clamp((time - animation.startAge()) / Math.max(1, animation.duration()), 0.0F, 1.0F);
            float[] v = SwordMoves.sample(SwordMoves.RITE.body(), t, ZERO);
            return new float[]{v[SwordMoves.SPIN], v[SwordMoves.DIP]};
        }
        if (animation != null && animation.type() == AnimationPayload.TOUCH_HANDLE) {
            // Присед при подборе: тело опускается, а ноги в это время сгибаются (см. claim).
            float tick = time - animation.startAge() - (animation.duration() - Awakening.CLAIM_TICKS);
            if (tick <= Awakening.CLAIM_GRIP) {
                return null;
            }
            float t = MathHelper.clamp((tick - Awakening.CLAIM_GRIP) / (Awakening.CLAIM_TICKS - Awakening.CLAIM_GRIP), 0.0F, 1.0F);
            return new float[]{0.0F, SwordMoves.sample(SwordMoves.CLAIM.body(), t, ZERO)[SwordMoves.DIP]};
        }
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
}
