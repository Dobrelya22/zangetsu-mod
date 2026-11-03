package com.zangetsu.logic;

import com.zangetsu.entity.SeveredPartEntity;
import com.zangetsu.entity.SeveredPartEntity.Piece;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Казнь. У каждого вида мобов несколько приёмов: голову с плеч, пополам по поясу, сверху вниз на две половины, наискось от плеча, скелет
 * разлетается костями, крипера подбрасывает и он рвётся в воздухе, никого не задев. Обычно игрок замирает, а камера уходит в сторону.
 *
 * <p>В финальной форме (Пустой на уровне V) меч не ждёт: любого такого моба под прицелом можно казнить сразу — вспышка шага, и приём
 * проходит быстрее обычного удара, без замирания камеры.
 */
public final class Execution {
    /**
     * Приём казни: каким ударом меча (номер приёма, его длительность и момент удара — как в SwordMoves на клиенте) и насколько
     * он растянут ради веса.
     */
    public enum Variant {
        BEHEAD(2, 18.0F, 0.42F, 1.7F),
        WAIST(7, 24.0F, 0.42F, 1.4F),
        CLEAVE(3, 20.0F, 0.47F, 1.6F),
        KESA(0, 18.0F, 0.42F, 1.7F),
        SHATTER(3, 20.0F, 0.47F, 1.6F),
        IMPALE(5, 17.0F, 0.40F, 1.7F),
        LAUNCH(6, 19.0F, 0.44F, 1.6F);

        private final int move;
        private final float duration, hit, stretch;

        Variant(int move, float duration, float hit, float stretch) {
            this.move = move;
            this.duration = duration;
            this.hit = hit;
            this.stretch = stretch;
        }

        int ticks(boolean flash) {
            return Math.round(this.duration * (flash ? FLASH_SPEED : this.stretch));
        }

        int hitTick(boolean flash) {
            return Math.round(this.duration * (flash ? FLASH_SPEED : this.stretch) * this.hit);
        }
    }

    /** Откуда обычная казнь и откуда финальная форма достаёт шагом (блоки). */
    public static final double REACH = 6.0, FLASH_RANGE = 18.0;
    /** Насколько быстро идёт приём в финальной форме: обычный удар меча ×0,6. */
    private static final float FLASH_SPEED = 0.6F;
    /** С какого расстояния стоит игрок, вынырнув из шага. */
    private static final double FLASH_STOP = 2.3;
    /** Подброшенный крипер: через сколько тиков рвётся и на какой высоте. */
    private static final int LAUNCH_TICKS = 11;
    private static final double LAUNCH_HEIGHT = 2.4;
    /** Куда падают куски (градусы в плоскости тела). */
    private static final float BACK = 90.0F, TO_LEFT = 180.0F, TO_RIGHT = 0.0F;

    private static final Set<UUID> BUSY = new HashSet<>();
    private static final Map<UUID, Variant> LAST = new HashMap<>();

    private Execution() {
    }

    /** Приёмы, которыми можно казнить этого моба. */
    private static Variant[] pool(LivingEntity target) {
        if (target instanceof WitherSkeletonEntity) {
            return new Variant[]{Variant.SHATTER, Variant.CLEAVE, Variant.WAIST};
        }
        if (target instanceof AbstractSkeletonEntity) {
            return new Variant[]{Variant.SHATTER, Variant.BEHEAD, Variant.KESA};
        }
        if (target instanceof CreeperEntity) {
            return new Variant[]{Variant.LAUNCH, Variant.CLEAVE, Variant.BEHEAD};
        }
        if (target instanceof EndermanEntity) {
            return new Variant[]{Variant.WAIST, Variant.KESA, Variant.CLEAVE};
        }
        if (target instanceof RavagerEntity) {
            return new Variant[]{Variant.CLEAVE, Variant.WAIST};
        }
        if (target instanceof AbstractPiglinEntity) {
            return new Variant[]{Variant.BEHEAD, Variant.KESA, Variant.WAIST, Variant.IMPALE};
        }
        if (target instanceof ZombieEntity) {
            return new Variant[]{Variant.BEHEAD, Variant.WAIST, Variant.CLEAVE, Variant.KESA};
        }
        return new Variant[]{Variant.BEHEAD, Variant.KESA, Variant.IMPALE, Variant.WAIST};
    }

    /** Случайный приём, но не тот же, что в прошлый раз. */
    private static Variant pick(ServerPlayerEntity player, LivingEntity target) {
        Variant[] pool = pool(target);
        Variant last = LAST.get(player.getUuid());
        Variant variant;
        do {
            variant = pool[player.getRandom().nextInt(pool.length)];
        } while (variant == last && pool.length > 1);
        return variant;
    }

    /** Игрок казнит или моба сейчас казнят. */
    public static boolean isBusy(Entity entity) {
        return BUSY.contains(entity.getUuid());
    }

    public static void request(ServerPlayerEntity player, int targetId) {
        request(player, targetId, null);
    }

    /** Казнить моба; {@code forced} — каким приёмом (null — выбрать самому). */
    public static void request(ServerPlayerEntity player, int targetId, Variant forced) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded || !SwordStacks.isHolding(player) || BUSY.contains(player.getUuid())) {
            return;
        }
        boolean flash = data.hollowActive && HollowForm.isFinal(data);
        Entity entity = player.getServerWorld().getEntityById(targetId);
        double reach = flash ? FLASH_RANGE + 2.0 : REACH;
        if (!(entity instanceof LivingEntity target) || BUSY.contains(target.getUuid()) || target.squaredDistanceTo(player) > reach * reach
                || !Limbs.executable(target, ModAttachments.limbs(target), flash, data.level())) {
            return;
        }
        start(player, target, forced != null ? forced : pick(player, target), flash);
    }

    private static void start(ServerPlayerEntity player, LivingEntity target, Variant variant, boolean flash) {
        ServerWorld world = player.getServerWorld();
        LAST.put(player.getUuid(), variant);
        BUSY.add(player.getUuid());
        BUSY.add(target.getUuid());

        // Моб замирает лицом к игроку, игрок — лицом к мобу.
        if (target instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }
        if (target instanceof CreeperEntity creeper) {
            creeper.setFuseSpeed(-1);
        }
        target.setVelocity(Vec3d.ZERO);
        if (flash) {
            flashStep(world, player, target);
        } else {
            Cinematic.face(player, target.getEyePos());
        }
        Vec3d toPlayer = player.getPos().subtract(target.getPos());
        float yaw = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0F;
        target.setYaw(yaw);
        target.setBodyYaw(yaw);
        target.setHeadYaw(yaw);

        int duration = variant.ticks(flash), hit = variant.hitTick(flash);
        Sync.animation(player, flash ? AnimationPayload.EXECUTE_FLASH : AnimationPayload.EXECUTE, duration, variant.move);
        Cinematic.lock(player, duration);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8F, flash ? 0.9F : 0.5F);
        Fx.sound(player, SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 0.6F, flash ? 1.0F : 0.6F);
        Fx.particles(world, Fx.REIATSU_BLACK, target.getPos().add(0, 0.1, 0), 12, 0.5, 0.02);

        Scheduler.later(hit, () -> {
            if (!player.isAlive() || !target.isAlive() || target.getWorld() != player.getWorld()) {
                return;
            }
            strike(world, player, target, variant, flash);
            Bond.change(player, Bond.EXECUTION_BONUS);
        });
        Scheduler.later(Math.max(duration, hit + LAUNCH_TICKS + 2), () -> finish(player, target));
    }

    /** Шаг: игрок исчезает и появляется перед мобом, за ним тает чёрно-алый след. */
    private static void flashStep(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
        Vec3d from = player.getPos();
        Vec3d line = target.getPos().subtract(from);
        double distance = line.length();
        Vec3d direction = distance < 1.0E-3 ? player.getRotationVector() : line.multiply(1.0 / distance);
        double way = 0.0;
        // Идём по прямой, пока есть куда встать.
        for (double d = 0.5; d <= distance - FLASH_STOP; d += 0.5) {
            if (!world.isSpaceEmpty(player, player.getBoundingBox().offset(direction.multiply(d)))) {
                break;
            }
            way = d;
        }
        Vec3d to = from.add(direction.multiply(way));
        for (double d = 0.0; d <= way; d += 0.8) {
            Vec3d at = from.add(direction.multiply(d)).add(0.0, 1.0, 0.0);
            Fx.particles(world, Fx.REIATSU_BLACK, at, 6, 0.25, 0.01);
            Fx.particles(world, Fx.REIATSU_RED, at, 3, 0.2, 0.01);
        }
        Vec3d eye = to.add(0.0, player.getStandingEyeHeight(), 0.0), look = target.getEyePos().subtract(eye);
        float yaw = (float) (MathHelper.atan2(look.z, look.x) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(MathHelper.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * (180.0 / Math.PI));
        player.networkHandler.requestTeleport(to.x, to.y, to.z, yaw, pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
        if (way > 0.0) {
            Fx.sound(world, from, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9F, 1.5F);
            Fx.sound(world, to, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.5F, 0.6F);
        }
    }

    // ---------------------------------------------------------------- приёмы

    /** Где что у моба (пиксели модели, y вниз): шея, пояс, бёдра. */
    private record Body(float neck, float waist, float hip) {
    }

    private static Body body(LivingEntity target, int limbs) {
        if (target instanceof EndermanEntity) {
            // Подрубленный эндермен осел на обрубки (см. EndermanEntityModelMixin).
            float drop = Limbs.hobbled(limbs) ? 16.5F : 0.0F;
            return new Body(-13.2F + drop, -8.0F + drop, -2.0F + drop);
        }
        if (target instanceof CreeperEntity) {
            return new Body(5.8F, 12.0F, 18.0F);
        }
        if (target instanceof RavagerEntity) {
            return new Body(-8.0F, -13.0F, -4.0F);
        }
        return new Body(-0.25F, 7.0F, 12.0F);
    }

    /** Направление оси модели в мире (единичный вектор). */
    private static Vec3d modelAxis(LivingEntity target, double x, double y, double z) {
        return Limbs.modelToWorld(target, x, y, z).subtract(Limbs.modelToWorld(target, 0.0, 0.0, 0.0)).normalize();
    }

    private static void strike(ServerWorld world, ServerPlayerEntity player, LivingEntity target, Variant variant, boolean flash) {
        int limbs = ModAttachments.limbs(target);
        Body body = body(target, limbs);
        Vec3d away = target.getPos().subtract(player.getPos()).multiply(1.0, 0.0, 1.0);
        away = away.lengthSquared() < 1.0E-4 ? player.getRotationVector().multiply(1.0, 0.0, 1.0).normalize() : away.normalize();
        Vec3d center = Limbs.modelToWorld(target, 0.0, body.waist(), 0.0);
        Fx.Gore gore = Fx.Gore.of(target);
        Fx.sound(target, SoundEvents.ITEM_TRIDENT_HIT, 1.2F, 0.6F);
        Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
        Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 0.55F);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, center, 2, 0.3, 0.0);
        Fx.particles(world, ParticleTypes.CRIT, center, 20, 0.35, 0.5);
        if (flash) {
            Fx.ring(world, Fx.REIATSU_RED, center, 1.4, 28);
            Fx.ring(world, Fx.REIATSU_BLACK, center, 0.9, 20);
            Fx.sound(target, SoundEvents.ENTITY_WITHER_SHOOT, 0.5F, 0.7F);
        }
        switch (variant) {
            case BEHEAD -> behead(world, target, limbs, body, away, gore);
            case WAIST -> waist(world, target, limbs, body, away, gore);
            case CLEAVE -> cleave(world, target, limbs, body, gore);
            case KESA -> kesa(world, target, limbs, body, away, gore);
            case SHATTER -> shatter(world, target, limbs, away, gore);
            case IMPALE -> impale(world, player, target, away, gore, variant, flash);
            case LAUNCH -> launch(world, player, target, limbs, body, gore);
        }
        if (variant != Variant.IMPALE && variant != Variant.LAUNCH) {
            hideAndKill(world, player, target, limbs);
        }
    }

    /** Голову с плеч: тело ещё миг стоит, из шеи бьёт фонтан, потом рушится. */
    private static void behead(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore) {
        float neck = body.neck();
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, -1.0F, 0.0F, -neck}}, 0.0F, neck - 4.0F, 0.0F, 0.2F,
                away.multiply(0.16).add(0.0, 0.44, 0.0), BACK, SeveredPartEntity.FREE, 26.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, neck}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, BACK, 11, 0.0F));
        Vec3d at = Limbs.modelToWorld(target, 0.0, neck, 0.0);
        burst(world, gore, at, 1.0);
        for (int i = 1; i <= 10; i++) {
            Scheduler.later(i, () -> {
                Fx.particles(world, gore.dust, at, 5, 0.06, 0.02);
                world.spawnParticles(gore.dust, at.x, at.y + 0.25, at.z, 4, 0.1, 0.2, 0.1, 0.0);
            });
        }
    }

    /** Пополам по поясу: верх взлетает и кувыркается, ноги подкашиваются. */
    private static void waist(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore) {
        float waist = body.waist();
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, -1.0F, 0.0F, -waist}}, 0.0F, waist - 6.0F, 0.0F, 0.26F,
                away.multiply(0.13).add(0.0, 0.3, 0.0), BACK, SeveredPartEntity.FREE, 15.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, waist}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, BACK, 6, 0.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, waist, 0.0), 1.2);
    }

    /** Сверху вниз: моб распадается на левую и правую половины. */
    private static void cleave(ServerWorld world, LivingEntity target, int limbs, Body body, Fx.Gore gore) {
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{1.0F, 0.0F, 0.0F, 0.01F}}, 1.0F, 24.0F, 0.0F, 0.0F, left.multiply(0.03), TO_LEFT, 2, 0.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-1.0F, 0.0F, 0.0F, 0.01F}}, -1.0F, 24.0F, 0.0F, 0.0F, left.multiply(-0.03), TO_RIGHT, 2, 0.0F));
        for (float y = body.neck() - 4.0F; y < 22.0F; y += 5.0F) {
            burst(world, gore, Limbs.modelToWorld(target, 0.0, y, 0.0), 0.4);
        }
    }

    /** Наискось от плеча к бедру: верхний кусок съезжает по срезу, нижний стоит и валится. */
    private static void kesa(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore) {
        float y = body.waist() - 1.0F, nx = -0.678F, ny = -0.735F;
        float d = ny * y;
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{nx, ny, 0.0F, d}}, -1.5F, y - 7.0F, 0.0F, 0.26F,
                left.multiply(-0.11).add(away.multiply(0.03)).add(0.0, 0.1, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 8.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-nx, -ny, 0.0F, -d}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, BACK, 8, 0.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.2);
    }

    /** Скелет разлетается костями: череп, рёбра, руки и ноги — каждая часть сама по себе. */
    private static void shatter(ServerWorld world, LivingEntity target, int limbs, Vec3d away, Fx.Gore gore) {
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        float[][] head = {{0.0F, -1.0F, 0.0F, 0.25F}};
        float[][] ribs = {{0.0F, 1.0F, 0.0F, -0.25F}, {0.0F, -1.0F, 0.0F, -12.0F}, {1.0F, 0.0F, 0.0F, -4.0F}, {-1.0F, 0.0F, 0.0F, -4.0F}};
        float[][] leftArm = {{1.0F, 0.0F, 0.0F, 4.01F}, {0.0F, 1.0F, 0.0F, -0.5F}};
        float[][] rightArm = {{-1.0F, 0.0F, 0.0F, 4.01F}, {0.0F, 1.0F, 0.0F, -0.5F}};
        float[][] leftLeg = {{0.0F, 1.0F, 0.0F, 12.0F}, {1.0F, 0.0F, 0.0F, 0.0F}, {-1.0F, 0.0F, 0.0F, -3.99F}};
        float[][] rightLeg = {{0.0F, 1.0F, 0.0F, 12.0F}, {-1.0F, 0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F, -3.99F}};
        var random = target.getRandom();
        SeveredPartEntity.spawn(world, target, limbs, new Piece(head, 0.0F, -4.0F, 0.0F, 0.2F, away.multiply(0.14).add(0.0, 0.34, 0.0), BACK, SeveredPartEntity.FREE, 24.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(ribs, 0.0F, 6.0F, 0.0F, 0.16F, away.multiply(0.1).add(0.0, 0.12, 0.0), BACK, SeveredPartEntity.FREE, 12.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(leftArm, 5.0F, 7.0F, 0.0F, 0.08F, left.multiply(0.16).add(0.0, 0.24, 0.0), TO_LEFT, SeveredPartEntity.FREE, 26.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(rightArm, -5.0F, 7.0F, 0.0F, 0.08F, left.multiply(-0.16).add(0.0, 0.2, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 30.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(leftLeg, 2.0F, 18.0F, 0.0F, 0.08F, left.multiply(0.1).add(away.multiply(0.07)).add(0.0, 0.12, 0.0), TO_LEFT, SeveredPartEntity.FREE, 18.0F + random.nextFloat() * 8.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(rightLeg, -2.0F, 18.0F, 0.0F, 0.08F, left.multiply(-0.1).add(away.multiply(0.07)).add(0.0, 0.12, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 18.0F + random.nextFloat() * 8.0F));
        Vec3d chest = Limbs.modelToWorld(target, 0.0, 6.0, 0.0);
        burst(world, gore, chest, 1.0);
        Fx.sound(target, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 1.2F, 0.7F);
        Fx.sound(target, SoundEvents.ENTITY_SKELETON_HURT, 0.8F, 0.6F);
        if (gore == Fx.Gore.SOOT) {
            Fx.particles(world, ParticleTypes.LARGE_SMOKE, chest, 24, 0.4, 0.03);
            Fx.particles(world, ParticleTypes.SOUL_FIRE_FLAME, chest, 20, 0.4, 0.04);
        }
    }

    /** Выпад насквозь: кровь бьёт из спины, моб висит на клинке, потом клинок выходит, и он падает назад. */
    private static void impale(ServerWorld world, ServerPlayerEntity player, LivingEntity target, Vec3d away, Fx.Gore gore, Variant variant, boolean flash) {
        Vec3d chest = Limbs.modelToWorld(target, 0.0, 5.0, 0.0);
        target.timeUntilRegen = 0;
        target.damage(world.getDamageSources().playerAttack(player), 0.01F);
        for (int i = 1; i <= 12; i++) {
            Fx.particles(world, gore.dust, chest.add(away.multiply(i * 0.2)), 4, 0.04 + i * 0.012, 0.01);
        }
        burst(world, gore, chest.add(away.multiply(0.3)), 1.0);
        int hold = Math.round(variant.ticks(flash) * 0.24F);
        Scheduler.later(hold, () -> {
            if (!target.isAlive()) {
                return;
            }
            kill(world, player, target);
            target.setVelocity(away.multiply(0.55).add(0.0, 0.24, 0.0));
            target.velocityModified = true;
            burst(world, gore, chest, 0.8);
            Fx.sound(target, SoundEvents.ITEM_TRIDENT_RETURN, 0.8F, 0.6F);
        });
    }

    /** Удар снизу подбрасывает моба, он взлетает и рвётся в воздухе. Взрыв никого не трогает. */
    private static void launch(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int limbs, Body body, Fx.Gore gore) {
        Vec3d from = target.getPos();
        burst(world, gore, Limbs.modelToWorld(target, 0.0, body.waist(), 0.0), 0.8);
        for (int i = 1; i <= LAUNCH_TICKS; i++) {
            double k = i / (double) LAUNCH_TICKS, height = LAUNCH_HEIGHT * (1.0 - (1.0 - k) * (1.0 - k));
            Scheduler.later(i, () -> {
                if (target.isAlive()) {
                    target.setPosition(from.x, from.y + height, from.z);
                    Fx.particles(world, ParticleTypes.SMOKE, target.getPos(), 4, 0.15, 0.01);
                }
            });
        }
        Fx.sound(target, SoundEvents.ENTITY_CREEPER_PRIMED, 1.0F, 1.4F);
        Scheduler.later(LAUNCH_TICKS, () -> {
            if (!target.isAlive()) {
                return;
            }
            burstApart(world, target, limbs, body);
            Vec3d at = Limbs.modelToWorld(target, 0.0, body.waist(), 0.0);
            Fx.particles(world, ParticleTypes.EXPLOSION, at, 5, 0.7, 0.0);
            Fx.particles(world, ParticleTypes.LARGE_SMOKE, at, 18, 0.5, 0.05);
            burst(world, gore, at, 2.0);
            Fx.sound(world, at, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0F, 0.9F);
            hideAndKill(world, player, target, limbs);
        });
    }

    /** Моб распался по всем швам: голова, два куска туловища и четыре ноги в разные стороны. */
    private static void burstApart(ServerWorld world, LivingEntity target, int limbs, Body body) {
        float neck = body.neck(), waist = body.waist(), hip = body.hip();
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0), back = modelAxis(target, 0.0, 0.0, 1.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, -1.0F, 0.0F, -neck}}, 0.0F, neck - 4.0F, 0.0F, 0.2F,
                new Vec3d(0.0, 0.5, 0.0).add(back.multiply(0.1)), BACK, SeveredPartEntity.FREE, 30.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, neck}, {0.0F, -1.0F, 0.0F, -waist}}, 0.0F, (neck + waist) * 0.5F, 0.0F, 0.14F,
                left.multiply(0.2).add(0.0, 0.3, 0.0), TO_LEFT, SeveredPartEntity.FREE, 22.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, waist}, {0.0F, -1.0F, 0.0F, -hip}}, 0.0F, (waist + hip) * 0.5F, 0.0F, 0.14F,
                left.multiply(-0.22).add(back.multiply(-0.1)).add(0.0, 0.2, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 26.0F));
        for (int i = 0; i < 4; i++) {
            float sx = i < 2 ? 1.0F : -1.0F, sz = i % 2 == 0 ? 1.0F : -1.0F;
            SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, hip}, {sx, 0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, sz, 0.0F}},
                    sx * 2.0F, hip + 3.0F, sz * 4.0F, 0.1F, left.multiply(sx * 0.2).add(back.multiply(sz * 0.2)).add(0.0, 0.16, 0.0),
                    sx > 0.0F ? TO_LEFT : TO_RIGHT, SeveredPartEntity.FREE, 24.0F + i * 5.0F));
        }
    }

    /** Брызги у места разреза: своя «кровь» у каждого вида и то, из чего он сделан. */
    private static void burst(ServerWorld world, Fx.Gore gore, Vec3d at, double power) {
        Fx.particles(world, gore.dust, at, (int) (46 * power), 0.22 * power, 0.12);
        if (gore.chunk != null) {
            Fx.particles(world, gore.chunk, at, (int) (12 * power), 0.1, 0.14);
        }
        if (gore == Fx.Gore.ENDER) {
            Fx.particles(world, ParticleTypes.PORTAL, at, (int) (60 * power), 0.4, 0.6);
        }
    }

    /** Настоящий моб больше не рисуется: вместо него куски. Прячем на тик позже, чтобы куски успели появиться у всех. */
    private static void hideAndKill(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int limbs) {
        Scheduler.later(1, () -> {
            Limbs.set(target, limbs | Limbs.SPLIT);
            kill(world, player, target);
        });
    }

    private static void kill(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
        if (target instanceof MobEntity mob) {
            mob.setAiDisabled(false);
        }
        target.timeUntilRegen = 0;
        target.damage(world.getDamageSources().playerAttack(player), 100000.0F);
    }

    private static void finish(ServerPlayerEntity player, LivingEntity target) {
        BUSY.remove(player.getUuid());
        BUSY.remove(target.getUuid());
        if (target.isAlive() && target instanceof MobEntity mob) {
            mob.setAiDisabled(false);
        }
    }
}
