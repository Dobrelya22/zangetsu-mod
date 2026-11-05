package com.zangetsu.client;

import com.zangetsu.client.render.FormPhysics;
import com.zangetsu.client.render.HollowRite;
import com.zangetsu.client.render.SlashTrails;
import com.zangetsu.client.render.SwordMoves;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.Fx;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Как ощущается удар. Нажатие начинает замах, а урон приходит не сразу, а в тот миг, когда клинок действительно доходит до цели;
 * если попал — удар на мгновение замирает, камера вздрагивает, летят искры и глухо звучит сталь. Нажатия во время удара
 * запоминаются: следующий приём серии начнётся, как только это станет возможно.
 */
public final class ZangetsuCombat {
    /** Сколько тиков удар замирает при попадании и сколько длится толчок камеры. */
    private static final float HIT_STOP = 2.5F, KICK_TICKS = 6.0F, EXECUTION_STOP = 3.0F;
    /** Из какой дальности ещё достаём цель, на которую замахивались (блоки в квадрате). */
    private static final double KEEP_TARGET = 4.5 * 4.5;

    private static boolean queued;
    private static float kickAt = -100.0F;

    private ZangetsuCombat() {
    }

    /** Нажата атака. true — обработано здесь, обычная атака не нужна. */
    public static boolean onAttackKey(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || player.isSpectator()) {
            return false;
        }
        // Идёт проверка реакции: клик принадлежит ей.
        if (ReactionCheck.onAttackKey()) {
            queued = false;
            return true;
        }
        // Пока идёт превращение, бить нельзя ничем.
        ClientState.Animation animation = ClientState.animation(player);
        if (animation != null && (animation.type() == AnimationPayload.HOLLOW || animation.type() == AnimationPayload.HOLLOW_FINAL)) {
            return true;
        }
        if (!player.getMainHandStack().isOf(ModItems.ZANGETSU) || animation != null) {
            return false;
        }
        // Блоки ломаются как обычно.
        HitResult hit = client.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return false;
        }
        float now = player.age;
        ClientState.Swing swing = ClientState.swing(player, now);
        if (swing != null && swing.execution) {
            return true;
        }
        if (swing != null && swing.progress(now) < SwordMoves.ALL[swing.move].chain()) {
            queued = true;
            return true;
        }
        attack(client, player);
        return true;
    }

    private static void attack(MinecraftClient client, ClientPlayerEntity player) {
        queued = false;
        ClientState.Swing swing = start(player, 0.0F, SwordMoves.combo(ClientState.nextSwing(player)), 1.0F, trailColor(player));
        swing.attacking = true;
        swing.target = client.crosshairTarget instanceof EntityHitResult entity ? entity.getEntity() : null;
        // Другие игроки увидят начало удара, рука тоже считается занятой.
        player.swingHand(Hand.MAIN_HAND);
    }

    /** Начать приём у игрока (свой или чужой). */
    public static ClientState.Swing start(PlayerEntity player, float aim, int move, float speed, int color) {
        SwordMoves.Move definition = SwordMoves.ALL[move];
        ClientState.Swing swing = new ClientState.Swing(move, player.age, definition.duration() * speed, aim, color, ClientState.ticks);
        ClientState.SWINGS.put(player.getId(), swing);
        flare(player, 0.3F, 0.0F);
        return swing;
    }

    /** Казнь: приём {@code move}, растянутый или сжатый до {@code duration} тиков. */
    public static void execution(PlayerEntity player, int move, int duration) {
        start(player, 0.0F, move, duration / SwordMoves.ALL[move].duration(), trailColor(player)).execution = true;
        flare(player, 0.8F, 0.15F);
        queued = false;
    }

    /** Отличная казнь: тот же приём, но в верхней точке замаха он замирает на {@code pause} тиков. */
    public static void charged(PlayerEntity player, int move, int duration, float pause) {
        SwordMoves.Move definition = SwordMoves.ALL[move];
        ClientState.Swing swing = start(player, 0.0F, move, duration / definition.duration(), trailColor(player));
        swing.execution = true;
        swing.freeze(swing.start + swing.duration * definition.trailFrom(), pause);
        flare(player, 0.8F, 0.15F);
        queued = false;
    }

    /** Огонь вспыхивает, а плащ и цепи получают толчок назад, против удара. Только у тех, на ком есть огонь. */
    public static void flare(PlayerEntity player, float heat, float push) {
        if (!ClientState.isHollow(player)) {
            return;
        }
        FormPhysics physics = FormPhysics.of(player);
        physics.kick(heat);
        Vec3d look = player.getRotationVector();
        physics.impulse((float) -look.x * push, 0.03F, (float) -look.z * push);
    }

    private static int trailColor(PlayerEntity player) {
        return ClientState.isHollow(player) ? SlashTrails.BLOOD : SlashTrails.STEEL;
    }

    /** Каждый тик: начала чужих ударов, свист клинка, момент удара и следующий приём серии. */
    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            return;
        }
        for (PlayerEntity player : client.world.getPlayers()) {
            HollowRite.tick(player);
            noticeSwing(client, player);
            ClientState.Swing swing = ClientState.swing(player, player.age);
            if (swing == null) {
                continue;
            }
            SwordMoves.Move move = SwordMoves.ALL[swing.move];
            float t = swing.progress(player.age);
            if (!swing.whooshed && t >= move.hit() - 0.1F) {
                swing.whooshed = true;
                whoosh(client, player);
            }
            if (swing.attacking && !swing.struck && t >= move.hit()) {
                swing.struck = true;
                strike(client, player, swing);
            }
            if (swing.execution && !swing.struck && t >= move.hit()) {
                swing.struck = true;
                swing.freeze(player.age, EXECUTION_STOP);
                if (player == client.player) {
                    kickAt = ClientState.ticks;
                }
            }
        }
        ClientPlayerEntity player = client.player;
        if (player != null) {
            walkToSword(client, player);
            TooSlowScene.holdStance(player);
            RampageScene.tick(client);
        }
        if (queued && player != null) {
            ClientState.Swing swing = ClientState.swing(player, player.age);
            boolean holding = player.getMainHandStack().isOf(ModItems.ZANGETSU);
            if (!holding) {
                queued = false;
            } else if ((swing == null || swing.progress(player.age) >= SwordMoves.ALL[swing.move].chain()) && ClientState.animation(player) == null) {
                attack(client, player);
            }
        }
    }

    /**
     * Сцена подбора меча: своего игрока ведём к мечу шагом (настоящим движением, поэтому и ноги идут сами), а пока рука не сжала рукоять, он смотрит на неё,
     * что бы ни делала мышь.
     */
    private static int walkScene = Integer.MIN_VALUE;
    private static Vec3d walkSpot;
    private static float walkYaw;

    private static void walkToSword(MinecraftClient client, ClientPlayerEntity player) {
        ClientState.Animation animation = ClientState.animation(player);
        if (animation == null || (animation.type() != AnimationPayload.TOUCH_HANDLE && animation.type() != AnimationPayload.DREAM_PULL)
                || !(client.world.getEntityById(animation.param()) instanceof ThrownZangetsuEntity sword)) {
            return;
        }
        int scene = animation.type() == AnimationPayload.TOUCH_HANDLE ? Awakening.CLAIM_TICKS : Dream.PULL_DURATION;
        int walk = animation.duration() - scene;
        int tick = player.age - animation.startAge();
        // Куда идти и куда смотреть решается один раз, в начале сцены: если считать заново на каждом шаге, точка уползает вместе с игроком, и он кружит вокруг меча.
        if (walkScene != animation.startAge()) {
            walkScene = animation.startAge();
            walkSpot = Awakening.gripSpot(player, sword.getPos());
            Vec3d to = sword.getPos().subtract(walkSpot);
            walkYaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F;
        }
        // Поворачивается плавно: корпус догоняет голову, а не встаёт сразу.
        float yaw = player.getYaw() + MathHelper.wrapDegrees(walkYaw - player.getYaw()) * 0.16F * Math.min(1.0F, (tick + 1) / 12.0F);
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.setBodyYaw(player.getBodyYaw() + MathHelper.wrapDegrees(yaw - player.getBodyYaw()) * 0.3F);
        if (tick < walk + Awakening.WALK_SETTLE) {
            Vec3d way = walkSpot.subtract(player.getPos()).multiply(1.0, 0.0, 1.0);
            double left = way.length();
            if (left > 0.01) {
                // Трогается мягко, а последние шаги всё короче: он замедляется и встаёт, а не упирается в точку.
                double pace = Math.min(left * 0.3, Awakening.WALK_SPEED * Math.min(1.0, (tick + 1) / 6.0));
                Vec3d step = way.multiply(pace / left);
                player.setVelocity(step.x, player.getVelocity().y, step.z);
            }
        }
    }

    /** В сцене подбора взгляд ведёт сама сцена: мышь иначе дёргала бы камеру каждый тик. */
    public static boolean holdsLook(Entity entity) {
        if (!(entity instanceof ClientPlayerEntity)) {
            return false;
        }
        ClientState.Animation animation = ClientState.animation((ClientPlayerEntity) entity);
        return animation != null && (animation.type() == AnimationPayload.TOUCH_HANDLE || animation.type() == AnimationPayload.DREAM_PULL || animation.type() == AnimationPayload.EXECUTE_BEHIND);
    }

    /** Рука махнула сама (чужой удар, рубка блоков, сцены) — показываем приём. */
    private static void noticeSwing(MinecraftClient client, PlayerEntity player) {
        if (!player.handSwinging || player.handSwingTicks != 0 || !player.getMainHandStack().isOf(ModItems.ZANGETSU) || ClientState.animation(player) != null) {
            return;
        }
        ClientState.Swing swing = ClientState.swing(player, player.age);
        if (swing != null && (swing.execution || ClientState.ticks - swing.startTick <= 1 || swing.progress(player.age) < SwordMoves.ALL[swing.move].chain())) {
            return;
        }
        start(player, 0.0F, SwordMoves.combo(ClientState.nextSwing(player)), 1.0F, trailColor(player));
    }

    private static void whoosh(MinecraftClient client, PlayerEntity player) {
        client.world.playSound(client.player, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS,
                0.7F, 0.55F + player.getRandom().nextFloat() * 0.15F);
    }

    /** Клинок дошёл: бьём того, кто под прицелом, а если он сместился — того, на кого замахивались, пока он в досягаемости. */
    private static void strike(MinecraftClient client, PlayerEntity player, ClientState.Swing swing) {
        if (player != client.player || client.interactionManager == null) {
            return;
        }
        Entity target = client.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (target == null && swing.target != null && swing.target.isAlive() && swing.target.squaredDistanceTo(player) <= KEEP_TARGET) {
            target = swing.target;
        }
        if (target == null) {
            player.resetLastAttackedTicks();
            return;
        }
        client.interactionManager.attackEntity(player, target);
        impact(client, player, swing, target);
    }

    private static void impact(MinecraftClient client, PlayerEntity player, ClientState.Swing swing, Entity target) {
        swing.freeze(player.age, HIT_STOP);
        flare(player, 0.7F, 0.1F);
        kickAt = ClientState.ticks;
        Vec3d at = target.getBoundingBox().getCenter();
        client.world.playSound(client.player, at.x, at.y, at.z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.9F, 0.65F);
        client.world.playSound(client.player, at.x, at.y, at.z, SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.5F, 0.8F);
        client.world.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0.0, 0.0, 0.0);
        boolean hollow = ClientState.isHollow(player);
        var random = player.getRandom();
        for (int i = 0; i < 16; i++) {
            double vx = (random.nextDouble() - 0.5) * 0.9, vy = random.nextDouble() * 0.6, vz = (random.nextDouble() - 0.5) * 0.9;
            client.world.addParticle(i % 2 == 0 ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, vx, vy, vz);
            if (hollow && i % 2 == 0) {
                client.world.addParticle(Fx.REIATSU_RED, at.x + vx, at.y + vy, at.z + vz, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Толчок камеры при попадании: 0 — нет, 1 — в самом начале. */
    public static float kick(float tickDelta) {
        float age = ClientState.ticks + tickDelta - kickAt;
        if (age < 0.0F || age >= KICK_TICKS) {
            return 0.0F;
        }
        float fade = 1.0F - age / KICK_TICKS;
        return fade * fade;
    }

    public static float kickAge(float tickDelta) {
        return ClientState.ticks + tickDelta - kickAt;
    }
}
