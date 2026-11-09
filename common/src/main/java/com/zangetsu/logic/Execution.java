package com.zangetsu.logic;

import com.zangetsu.entity.SeveredPartEntity;
import com.zangetsu.entity.SeveredPartEntity.Piece;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
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
 * Казнь. Мобов рубят, а не протыкают: голову с плеч, пополам по поясу, поперёк груди, сверху вниз на две половины, снизу вверх так, что половины взлетают, наискось от плеча и от бедра,
 * ломтиками за один оборот, на четыре части, тремя ударами, одним взмахом, после которого враг ещё миг стоит. Скелет разлетается костями, крипера подбрасывает, и он рвётся в воздухе, никого не задев.
 *
 * <p>Начинает казнь проверка реакции ({@link Reaction}). В финальной форме (Пустой на уровне V): любого такого моба под прицелом можно казнить сразу — вспышка шага, и приём
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
        LAUNCH(6, 19.0F, 0.44F, 1.6F),
        /** Только со спины: косой удар и сразу встречный снизу, моб распадается крестом на четыре куска. */
        CROSS(0, 18.0F, 0.42F, 0.85F),
        /** Наискось снизу, от бедра к плечу: верх отлетает вверх и в сторону. */
        GYAKU(1, 16.0F, 0.40F, 1.7F),
        /** Поперёк груди: верх съезжает, из среза бьёт фонтан. */
        CHEST(4, 16.0F, 0.40F, 1.6F),
        /** Снизу вверх по всему телу: половины взлетают и расходятся. */
        SPLIT_UP(6, 19.0F, 0.44F, 1.5F),
        /** За один оборот: тело распадается ломтями, и каждый съезжает в свою сторону. */
        SLICES(7, 24.0F, 0.42F, 1.2F),
        /** Со спины: сверху вниз и сразу поперёк, на четыре части. */
        QUARTER(3, 20.0F, 0.47F, 0.85F),
        /** Со спины: три удара подряд, распадается на последнем. */
        TRIPLE(0, 18.0F, 0.42F, 0.7F),
        /** Со спины: один взмах, враг ещё стоит, по нему идёт светящаяся линия, и только потом он разъезжается. */
        IAI(2, 18.0F, 0.42F, 1.1F);

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
    private static final float BACK = 90.0F, TO_LEFT = 180.0F, TO_RIGHT = 0.0F, FORWARD = 270.0F;
    /** Сцена отличного попадания (тики): игрок появляется за спиной, замирает в замахе на {@link #CHARGE_PAUSE} тиков, и в {@code BEHIND_STRIKE} приходит резкий удар. */
    public static final int BEHIND_APPEAR = 3, BEHIND_STRIKE = 19, CHARGE_PAUSE = 8;
    /** Встречный удар креста: приём меча и его длительность. */
    private static final int CROSS_SECOND_MOVE = 6, CROSS_SECOND_TICKS = 14;
    private static final float CROSS_SECOND_HIT = 0.44F;
    /** Второй удар «на четыре части» и второй с третьим в серии из трёх (приём, длительность, момент удара). */
    private static final int QUARTER_SECOND_TICKS = 11, TRIPLE_STEP_TICKS = 10;
    /** Сколько враг ещё стоит после взмаха, разрубленный. */
    private static final int IAI_DELAY = 10;
    /** Плоскость косого удара: нормаль в плоскости тела. */
    private static final float DIAG_X = 0.678F, DIAG_Y = 0.735F;

    private static final Set<UUID> BUSY = new HashSet<>();
    private static final Set<UUID> BEHIND = new HashSet<>();
    /** Каким приёмом пойдёт следующая казнь со спины (null — случайно). Для сцен и игровых проверок. */
    public static Variant nextBehind;
    private static final Map<UUID, Variant> LAST = new HashMap<>();
    /** Мобы, вызванные сценой: с них ничего не падает. */
    private static final Set<UUID> NO_LOOT = new HashSet<>();

    private Execution() {
    }

    /** Приёмы, которыми можно казнить этого моба. У каждого вида свой набор, все рубящие. */
    private static Variant[] pool(LivingEntity target) {
        if (target instanceof WitherSkeletonEntity) {
            return new Variant[]{Variant.SHATTER, Variant.CLEAVE, Variant.WAIST, Variant.GYAKU, Variant.CHEST};
        }
        if (target instanceof AbstractSkeletonEntity) {
            return new Variant[]{Variant.SHATTER, Variant.BEHEAD, Variant.KESA, Variant.GYAKU, Variant.CHEST};
        }
        if (target instanceof CreeperEntity) {
            return new Variant[]{Variant.LAUNCH, Variant.CLEAVE, Variant.BEHEAD, Variant.WAIST, Variant.SPLIT_UP};
        }
        if (target instanceof EndermanEntity) {
            return new Variant[]{Variant.WAIST, Variant.KESA, Variant.CLEAVE, Variant.GYAKU, Variant.CHEST};
        }
        if (target instanceof RavagerEntity) {
            return new Variant[]{Variant.CLEAVE, Variant.WAIST};
        }
        return new Variant[]{Variant.BEHEAD, Variant.WAIST, Variant.CLEAVE, Variant.KESA, Variant.GYAKU, Variant.CHEST, Variant.SPLIT_UP};
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

    /** Приёмы со спины: зрелищнее обычных, многие в несколько ударов. */
    private static Variant[] behindPool(LivingEntity target) {
        if (target instanceof WitherSkeletonEntity) {
            return new Variant[]{Variant.SHATTER, Variant.CROSS, Variant.QUARTER, Variant.TRIPLE, Variant.SLICES, Variant.IAI};
        }
        if (target instanceof AbstractSkeletonEntity) {
            return new Variant[]{Variant.SHATTER, Variant.CROSS, Variant.QUARTER, Variant.IAI, Variant.SLICES};
        }
        if (target instanceof CreeperEntity) {
            return new Variant[]{Variant.LAUNCH, Variant.QUARTER, Variant.SLICES, Variant.IAI, Variant.SPLIT_UP};
        }
        if (target instanceof EndermanEntity) {
            return new Variant[]{Variant.CROSS, Variant.QUARTER, Variant.TRIPLE, Variant.SLICES, Variant.IAI};
        }
        if (target instanceof RavagerEntity) {
            return new Variant[]{Variant.QUARTER, Variant.CLEAVE, Variant.WAIST};
        }
        return new Variant[]{Variant.CROSS, Variant.QUARTER, Variant.TRIPLE, Variant.SLICES, Variant.IAI, Variant.SPLIT_UP};
    }

    /** Случайный приём со спины, но не тот же, что в прошлый раз. */
    private static Variant pickBehind(ServerPlayerEntity player, LivingEntity target) {
        Variant[] pool = behindPool(target);
        Variant last = LAST.get(player.getUuid());
        Variant variant;
        do {
            variant = pool[player.getRandom().nextInt(pool.length)];
        } while (variant == last && pool.length > 1);
        return variant;
    }

    /** Идёт сцена за спиной врага: игрока нельзя ранить. */
    public static boolean inBehindScene(Entity player) {
        return BEHIND.contains(player.getUuid());
    }

    /** На каком расстоянии от врага встаёт игрок за его спиной. */
    public static double behindDistance(LivingEntity target) {
        return target.getWidth() * 0.5 + 1.15;
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
        if (!data.bonded || !SwordStacks.isHolding(player) || BUSY.contains(player.getUuid()) || HollowForm.isTransforming(player)) {
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

    /** Хорошее попадание по кругу реакции: казнь. Далеко стоящего игрока выносит к врагу шаг, быстрый удар — только в финальной форме. */
    public static void fromReaction(ServerPlayerEntity player, LivingEntity target) {
        ZangetsuData data = ModAttachments.data(player);
        if (BUSY.contains(player.getUuid()) || BUSY.contains(target.getUuid()) || !target.isAlive()) {
            return;
        }
        boolean flash = data.hollowActive && HollowForm.isFinal(data);
        if (!flash && target.squaredDistanceTo(player) > REACH * REACH) {
            flashStep(player.getServerWorld(), player, target);
        }
        start(player, target, pick(player, target), flash);
    }

    /**
     * Отличное попадание: время будто встаёт, игрок исчезает и выныривает за спиной врага (тот так и смотрит туда, где он стоял), «ты слишком медлителен» и казнь сзади.
     * Если за спиной не встать (стена, обрыв), остаётся обычная быстрая казнь.
     */
    public static void behind(ServerPlayerEntity player, LivingEntity target) {
        if (BUSY.contains(player.getUuid()) || BUSY.contains(target.getUuid()) || !target.isAlive()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d from = player.getPos();
        Vec3d forward = target.getPos().subtract(from).multiply(1.0, 0.0, 1.0);
        forward = forward.lengthSquared() < 1.0E-4 ? player.getRotationVector().multiply(1.0, 0.0, 1.0).normalize() : forward.normalize();
        Vec3d spot = spotBehind(world, player, target, forward);
        if (spot == null) {
            fromReaction(player, target);
            return;
        }
        Variant variant = nextBehind != null ? nextBehind : pickBehind(player, target);
        nextBehind = null;
        LAST.put(player.getUuid(), variant);
        UUID self = player.getUuid();
        BUSY.add(self);
        BUSY.add(target.getUuid());
        BEHIND.add(self);

        // Враг так и смотрит туда, где только что стоял игрок.
        if (target instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }
        if (target instanceof CreeperEntity creeper) {
            creeper.setFuseSpeed(-1);
        }
        target.setVelocity(Vec3d.ZERO);
        float enemyYaw = (float) Math.toDegrees(Math.atan2(from.z - target.getZ(), from.x - target.getX())) - 90.0F;
        target.setYaw(enemyYaw);
        target.setBodyYaw(enemyYaw);
        target.setHeadYaw(enemyYaw);

        // Там, где стоял игрок, тает его тень.
        Vec3d chest = from.add(0.0, 1.0, 0.0);
        Fx.particles(world, Fx.REIATSU_BLACK, chest, 40, 0.35, 0.02);
        Fx.particles(world, Fx.REIATSU_RED, chest, 18, 0.3, 0.02);
        Fx.sound(world, from, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0F, 0.7F);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8F, 1.6F);

        Vec3d eye = spot.add(0.0, player.getStandingEyeHeight(), 0.0), look = target.getEyePos().subtract(eye);
        float yaw = (float) (MathHelper.atan2(look.z, look.x) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(MathHelper.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * (180.0 / Math.PI));
        player.networkHandler.requestTeleport(spot.x, spot.y, spot.z, yaw, pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);

        // Появился, сразу замах, короткая пауза, будто время встало, и резкий удар.
        int duration = variant.ticks(true), hit = variant.hitTick(true);
        int swingAt = Math.max(BEHIND_APPEAR + 1, BEHIND_STRIKE - hit - CHARGE_PAUSE);
        int total = BEHIND_STRIKE + afterHit(variant, duration, hit);
        Sync.animation(player, AnimationPayload.EXECUTE_BEHIND, swingAt - 1, target.getId());
        Cinematic.lock(player, total);

        Scheduler.later(BEHIND_APPEAR, () -> {
            // Появился: удар воздуха, чёрно-алая вспышка, низкий гул.
            Vec3d at = spot.add(0.0, 1.0, 0.0);
            Fx.particles(world, Fx.REIATSU_BLACK, at, 50, 0.4, 0.06);
            Fx.particles(world, Fx.REIATSU_RED, at, 30, 0.4, 0.05);
            Fx.ring(world, Fx.REIATSU_RED, spot.add(0.0, 0.1, 0.0), 1.1, 24);
            Fx.sound(world, spot, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.5F);
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.7F);
        });
        Scheduler.later(swingAt, () -> {
            if (player.isAlive() && target.isAlive()) {
                Sync.animation(player, AnimationPayload.EXECUTE_CHARGED, duration, variant.move);
            }
        });
        Scheduler.later(BEHIND_STRIKE, () -> {
            if (!player.isAlive() || !target.isAlive() || target.getWorld() != player.getWorld()) {
                return;
            }
            strike(world, player, target, variant, true, true);
            impact(world, player, target);
            Bond.change(player, Bond.EXECUTION_BONUS * 2);
        });
        Scheduler.later(total, () -> {
            BEHIND.remove(self);
            finish(player, target);
        });
    }

    /** Вся тяжесть замаха приходит разом: низкий удар, кольцо, встряска. */
    private static void impact(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
        Vec3d at = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0);
        Fx.sound(target, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.7F, 0.6F);
        Fx.sound(target, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.45F, 1.5F);
        Fx.ring(world, Fx.REIATSU_RED, at, 2.4, 40);
        Fx.ring(world, Fx.REIATSU_BLACK, target.getPos().add(0.0, 0.1, 0.0), 3.2, 44);
        Sync.fx(player, ClientFxPayload.IMPACT_FLASH, 6);
    }

    /** Сколько тиков сцена идёт после первого удара: у серий ударов дольше. */
    private static int afterHit(Variant variant, int duration, int hit) {
        return switch (variant) {
            case CROSS -> CROSS_SECOND_TICKS + 8;
            case QUARTER -> QUARTER_SECOND_TICKS + 8;
            case TRIPLE -> TRIPLE_STEP_TICKS * 2 + 8;
            case IAI -> IAI_DELAY + 10;
            case LAUNCH -> LAUNCH_TICKS + 4;
            default -> Math.max(duration - hit, 10);
        };
    }

    /** Куда встать за спиной: прямо сзади, а если там занято — чуть вбок. Земля под ногами должна быть на той же высоте, что у врага, плюс-минус блок. */
    private static Vec3d spotBehind(ServerWorld world, ServerPlayerEntity player, LivingEntity target, Vec3d forward) {
        double distance = behindDistance(target);
        for (double angle : new double[]{0.0, 25.0, -25.0, 50.0, -50.0, 80.0, -80.0}) {
            double r = Math.toRadians(angle), c = Math.cos(r), s = Math.sin(r);
            Vec3d way = new Vec3d(forward.x * c - forward.z * s, 0.0, forward.x * s + forward.z * c);
            Vec3d at = target.getPos().add(way.multiply(distance));
            for (double dy : new double[]{0.0, 1.0, -1.0}) {
                Vec3d spot = at.add(0.0, dy, 0.0);
                var box = player.getDimensions(player.getPose()).getBoxAt(spot);
                if (world.isSpaceEmpty(player, box) && !world.isSpaceEmpty(player, box.offset(0.0, -0.3, 0.0))) {
                    return spot;
                }
            }
        }
        return null;
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
            strike(world, player, target, variant, flash, false);
            Bond.change(player, Bond.EXECUTION_BONUS);
        });
        Scheduler.later(Math.max(duration, hit + afterHit(variant, duration, hit)), () -> finish(player, target));
    }

    /** Шаг: игрок исчезает и появляется перед мобом, за ним тает чёрно-алый след. */
    private static void flashStep(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
        flash(world, player, target);
    }

    /** Шаг к мобу: сцена Пустого пользуется им же. */
    static void flash(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
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

    private static void strike(ServerWorld world, ServerPlayerEntity player, LivingEntity target, Variant variant, boolean flash, boolean behind) {
        float fall = behind ? FORWARD : BACK;
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
            case BEHEAD -> behead(world, target, limbs, body, away, gore, fall);
            case WAIST -> waist(world, target, limbs, body, away, gore, fall);
            case CLEAVE -> cleave(world, target, limbs, body, gore);
            case KESA -> kesa(world, target, limbs, body, away, gore, fall);
            case SHATTER -> shatter(world, target, limbs, away, gore, fall);
            case IMPALE -> impale(world, player, target, away, gore, variant, flash);
            case LAUNCH -> launch(world, player, target, limbs, body, gore);
            case CROSS -> cross(world, player, target, limbs, body, away, gore);
            case GYAKU -> gyaku(world, target, limbs, body, away, gore, fall);
            case CHEST -> chest(world, target, limbs, body, away, gore, fall);
            case SPLIT_UP -> splitUp(world, target, limbs, body, away, gore);
            case SLICES -> slices(world, target, limbs, body, away, gore, fall);
            case QUARTER -> quarter(world, player, target, limbs, body, away, gore);
            case TRIPLE -> triple(world, player, target, limbs, body, away, gore, fall);
            case IAI -> iai(world, player, target, limbs, body, away, gore, fall);
        }
        switch (variant) {
            case IMPALE, LAUNCH, CROSS, QUARTER, TRIPLE, IAI -> {
            }
            default -> hideAndKill(world, player, target, limbs);
        }
    }

    /** Наискось снизу, от бедра к плечу: верхний кусок подбрасывает вверх и в сторону, ноги ещё стоят и валятся. */
    private static void gyaku(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float y = body.waist() - 1.0F, d = -DIAG_Y * y;
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{DIAG_X, -DIAG_Y, 0.0F, d}}, 1.5F, y - 7.0F, 0.0F, 0.26F,
                left.multiply(0.14).add(away.multiply(0.04)).add(0.0, 0.22, 0.0), TO_LEFT, SeveredPartEntity.FREE, 10.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-DIAG_X, DIAG_Y, 0.0F, -d}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 8, 0.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.2);
    }

    /** Поперёк груди: верх медленно съезжает по срезу, из него бьёт фонтан, ноги с низом ещё стоят. */
    private static void chest(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float y = (body.neck() + body.waist()) * 0.5F + 1.0F;
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, -1.0F, 0.0F, -y}}, 0.0F, y - 5.0F, 0.0F, 0.24F,
                away.multiply(0.08).add(left.multiply(0.03)).add(0.0, 0.14, 0.0), fall, SeveredPartEntity.FREE, 6.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, y}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 12, 0.0F));
        Vec3d at = Limbs.modelToWorld(target, 0.0, y, 0.0);
        burst(world, gore, at, 1.2);
        for (int i = 1; i <= 8; i++) {
            Scheduler.later(i, () -> world.spawnParticles(gore.dust, at.x, at.y + 0.2, at.z, 6, 0.12, 0.25, 0.12, 0.0));
        }
    }

    /** Снизу вверх по всему телу: половины взлетают и расходятся, кувыркаясь. */
    private static void splitUp(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore) {
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        float y = body.waist();
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{1.0F, 0.0F, 0.0F, 0.01F}}, 1.5F, y, 0.0F, 0.2F,
                left.multiply(0.19).add(away.multiply(0.03)).add(0.0, 0.34, 0.0), TO_LEFT, SeveredPartEntity.FREE, 11.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-1.0F, 0.0F, 0.0F, 0.01F}}, -1.5F, y, 0.0F, 0.2F,
                left.multiply(-0.19).add(away.multiply(0.03)).add(0.0, 0.34, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 11.0F));
        for (float at = body.neck() - 4.0F; at < 22.0F; at += 5.0F) {
            burst(world, gore, Limbs.modelToWorld(target, 0.0, at, 0.0), 0.5);
        }
    }

    /** За один оборот: верх взлетает, два ломтя съезжают в разные стороны, ноги стоят и подламываются. */
    private static void slices(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float y1 = body.neck() + 3.5F, y2 = body.waist() + 1.0F, y3 = body.hip() + 3.0F;
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, -1.0F, 0.0F, -y1}}, 0.0F, y1 - 5.0F, 0.0F, 0.2F, away.multiply(0.1).add(0.0, 0.26, 0.0), fall, SeveredPartEntity.FREE, 14.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, y1}, {0.0F, -1.0F, 0.0F, -y2}}, 0.0F, (y1 + y2) * 0.5F, 0.0F, 0.12F,
                left.multiply(0.14).add(0.0, 0.09, 0.0), TO_LEFT, SeveredPartEntity.FREE, 9.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, y2}, {0.0F, -1.0F, 0.0F, -y3}}, 0.0F, (y2 + y3) * 0.5F, 0.0F, 0.1F,
                left.multiply(-0.15).add(0.0, 0.06, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 9.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, y3}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 10, 0.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y1, 0.0), 0.8);
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y2, 0.0), 0.8);
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y3, 0.0), 0.6);
    }

    /** Сверху вниз только вскрывает, враг ещё стоит. Следом удар поперёк, и он распадается на четыре части. */
    private static void quarter(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore) {
        for (float at = body.neck() - 4.0F; at < 22.0F; at += 6.0F) {
            burst(world, gore, Limbs.modelToWorld(target, 0.0, at, 0.0), 0.3);
        }
        Sync.animation(player, AnimationPayload.EXECUTE_FLASH, QUARTER_SECOND_TICKS, 2);
        Scheduler.later(Math.round(QUARTER_SECOND_TICKS * 0.42F), () -> {
            if (!target.isAlive()) {
                return;
            }
            quarterPieces(world, target, limbs, body, away, gore);
            Fx.sound(target, SoundEvents.ITEM_TRIDENT_HIT, 1.2F, 0.5F);
            Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
            hideAndKill(world, player, target, limbs);
        });
    }

    /** Четыре части: верхние взлетают и расходятся, нижние подламываются. */
    private static void quarterPieces(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore) {
        float y = body.waist();
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{1.0F, 0.0F, 0.0F, 0.01F}, {0.0F, -1.0F, 0.0F, -y}}, 3.0F, y - 6.0F, 0.0F, 0.2F,
                left.multiply(0.18).add(away.multiply(0.08)).add(0.0, 0.34, 0.0), TO_LEFT, SeveredPartEntity.FREE, 16.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-1.0F, 0.0F, 0.0F, 0.01F}, {0.0F, -1.0F, 0.0F, -y}}, -3.0F, y - 6.0F, 0.0F, 0.2F,
                left.multiply(-0.18).add(away.multiply(0.08)).add(0.0, 0.3, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 16.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{1.0F, 0.0F, 0.0F, 0.01F}, {0.0F, 1.0F, 0.0F, y}}, 2.0F, 24.0F, 0.0F, 0.0F, left.multiply(0.03), TO_LEFT, 5, 0.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-1.0F, 0.0F, 0.0F, 0.01F}, {0.0F, 1.0F, 0.0F, y}}, -2.0F, 24.0F, 0.0F, 0.0F, left.multiply(-0.03), TO_RIGHT, 5, 0.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.5);
        Fx.ring(world, Fx.REIATSU_RED, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.2, 22);
    }

    /** Расчленение по всем суставам: голова, руки, ноги и два куска туловища летят в разные стороны. */
    private static void dismember(ServerWorld world, LivingEntity target, int limbs, Vec3d away, Fx.Gore gore) {
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        var random = target.getRandom();
        float[][] head = {{0.0F, -1.0F, 0.0F, 0.25F}};
        float[][] chest = {{0.0F, 1.0F, 0.0F, -0.25F}, {0.0F, -1.0F, 0.0F, -6.0F}, {1.0F, 0.0F, 0.0F, -4.0F}, {-1.0F, 0.0F, 0.0F, -4.0F}};
        float[][] belly = {{0.0F, 1.0F, 0.0F, 6.0F}, {0.0F, -1.0F, 0.0F, -12.0F}, {1.0F, 0.0F, 0.0F, -4.0F}, {-1.0F, 0.0F, 0.0F, -4.0F}};
        float[][] leftArm = {{1.0F, 0.0F, 0.0F, 4.01F}, {0.0F, 1.0F, 0.0F, -0.5F}};
        float[][] rightArm = {{-1.0F, 0.0F, 0.0F, 4.01F}, {0.0F, 1.0F, 0.0F, -0.5F}};
        float[][] leftLeg = {{0.0F, 1.0F, 0.0F, 12.0F}, {1.0F, 0.0F, 0.0F, 0.0F}, {-1.0F, 0.0F, 0.0F, -3.99F}};
        float[][] rightLeg = {{0.0F, 1.0F, 0.0F, 12.0F}, {-1.0F, 0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F, -3.99F}};
        SeveredPartEntity.spawn(world, target, limbs, new Piece(head, 0.0F, -4.0F, 0.0F, 0.2F, away.multiply(0.2).add(0.0, 0.5, 0.0), BACK, SeveredPartEntity.FREE, 28.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(chest, 0.0F, 3.0F, 0.0F, 0.14F, away.multiply(0.16).add(left.multiply(0.05)).add(0.0, 0.3, 0.0), BACK, SeveredPartEntity.FREE, 15.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(belly, 0.0F, 9.0F, 0.0F, 0.12F, away.multiply(0.1).add(left.multiply(-0.06)).add(0.0, 0.16, 0.0), BACK, SeveredPartEntity.FREE, 18.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(leftArm, 5.0F, 7.0F, 0.0F, 0.08F, left.multiply(0.24).add(0.0, 0.3, 0.0), TO_LEFT, SeveredPartEntity.FREE, 30.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(rightArm, -5.0F, 7.0F, 0.0F, 0.08F, left.multiply(-0.24).add(0.0, 0.26, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 34.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(leftLeg, 2.0F, 18.0F, 0.0F, 0.08F, left.multiply(0.14).add(away.multiply(0.08)).add(0.0, 0.14, 0.0), TO_LEFT, SeveredPartEntity.FREE, 18.0F + random.nextFloat() * 10.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(rightLeg, -2.0F, 18.0F, 0.0F, 0.08F, left.multiply(-0.14).add(away.multiply(0.08)).add(0.0, 0.14, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 18.0F + random.nextFloat() * 10.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, 6.0, 0.0), 2.0);
    }

    /** Три удара подряд: первые два вскрывают, на третьем тело разваливается на три куска по двум косым линиям. */
    private static void triple(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float upper = body.waist() - 5.0F, lower = body.waist() + 3.0F;
        burst(world, gore, Limbs.modelToWorld(target, 0.0, upper, 0.0), 0.6);
        Sync.animation(player, AnimationPayload.EXECUTE_FLASH, TRIPLE_STEP_TICKS, 4);
        int step = Math.round(TRIPLE_STEP_TICKS * 0.4F);
        Scheduler.later(step, () -> {
            if (!target.isAlive()) {
                return;
            }
            burst(world, gore, Limbs.modelToWorld(target, 0.0, lower, 0.0), 0.6);
            Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
            Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
        });
        Scheduler.later(TRIPLE_STEP_TICKS, () -> {
            if (target.isAlive()) {
                Sync.animation(player, AnimationPayload.EXECUTE_FLASH, TRIPLE_STEP_TICKS, 1);
            }
        });
        Scheduler.later(TRIPLE_STEP_TICKS + step, () -> {
            if (!target.isAlive()) {
                return;
            }
            float da = -DIAG_Y * upper, db = -DIAG_Y * lower;
            Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
            float[][] top = {{-DIAG_X, -DIAG_Y, 0.0F, da}};
            float[][] middle = {{DIAG_X, DIAG_Y, 0.0F, -da}, {DIAG_X, -DIAG_Y, 0.0F, db}};
            float[][] legs = {{DIAG_X, DIAG_Y, 0.0F, -da}, {-DIAG_X, DIAG_Y, 0.0F, -db}};
            SeveredPartEntity.spawn(world, target, limbs, new Piece(top, -1.5F, upper - 6.0F, 0.0F, 0.26F, left.multiply(-0.14).add(away.multiply(0.05)).add(0.0, 0.3, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 13.0F));
            SeveredPartEntity.spawn(world, target, limbs, new Piece(middle, 1.5F, (upper + lower) * 0.5F, 0.0F, 0.14F, left.multiply(0.16).add(0.0, 0.1, 0.0), TO_LEFT, SeveredPartEntity.FREE, 10.0F));
            SeveredPartEntity.spawn(world, target, limbs, new Piece(legs, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 7, 0.0F));
            burst(world, gore, Limbs.modelToWorld(target, 0.0, body.waist(), 0.0), 1.6);
            Fx.ring(world, Fx.REIATSU_RED, Limbs.modelToWorld(target, 0.0, body.waist(), 0.0), 1.3, 24);
            Fx.sound(target, SoundEvents.ITEM_TRIDENT_HIT, 1.2F, 0.5F);
            Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
            hideAndKill(world, player, target, limbs);
        });
    }

    /** Один взмах: враг ещё стоит, и только линия разреза светится. Тихий звон, и он разъезжается. */
    private static void iai(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float y = body.waist() - 1.0F;
        Sync.mark(target);
        Scheduler.later(IAI_DELAY, () -> {
            if (!target.isAlive()) {
                return;
            }
            splitAt(world, target, limbs, y, away, gore, fall);
            Fx.sound(target, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.4F, 0.7F);
            Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 0.5F);
            hideAndKill(world, player, target, limbs);
        });
    }

    /** Разрез по косой линии: верх медленно съезжает, низ стоит и валится. Без звука и без смерти моба: их добавляет тот, кто режет. */
    public static void splitAt(ServerWorld world, LivingEntity target, int limbs, float y, Vec3d away, Fx.Gore gore, float fall) {
        float d = -DIAG_Y * y;
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-DIAG_X, -DIAG_Y, 0.0F, d}}, -1.5F, y - 7.0F, 0.0F, 0.26F,
                left.multiply(-0.13).add(away.multiply(0.03)).add(0.0, 0.08, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 7.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{DIAG_X, DIAG_Y, 0.0F, -d}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 6, 0.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.5);
    }

    static void noLoot(LivingEntity mob) {
        NO_LOOT.add(mob.getUuid());
    }

    /** Удар из поставленной сцены: приём выбран там же, игрок уже стоит как надо. Если приём не подходит мобу по форме тела, его рубят сверху вниз. */
    static void scripted(ServerWorld world, ServerPlayerEntity player, LivingEntity target, Variant variant) {
        Variant cut = target instanceof RavagerEntity && variant != Variant.WAIST ? Variant.CLEAVE : variant;
        strike(world, player, target, cut, true, false);
    }

    /** Разваливается на куски сразу, без своего взмаха: взмах уже был. Разные мобы по-разному, тех, кого резать нельзя, просто убивает. */
    static void sever(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int kind, Vec3d away) {
        int limbs = ModAttachments.limbs(target);
        Fx.Gore gore = Fx.Gore.of(target);
        if (!Limbs.canBeExecuted(target)) {
            burst(world, gore, target.getPos().add(0.0, target.getHeight() * 0.5, 0.0), 1.5);
            kill(world, player, target);
            return;
        }
        Body body = body(target, limbs);
        if (target instanceof CreeperEntity) {
            burstApart(world, target, limbs, body);
            burst(world, gore, Limbs.modelToWorld(target, 0.0, body.waist(), 0.0), 2.0);
        } else if (target instanceof RavagerEntity) {
            cleave(world, target, limbs, body, gore);
        } else {
            switch (Math.floorMod(kind, 3)) {
                case 0 -> {
                    if (Limbs.isHumanoid(target)) {
                        dismember(world, target, limbs, away, gore);
                    } else {
                        quarterPieces(world, target, limbs, body, away, gore);
                    }
                }
                case 1 -> slices(world, target, limbs, body, away, gore, BACK);
                default -> quarterPieces(world, target, limbs, body, away, gore);
            }
        }
        hideAndKill(world, player, target, limbs);
    }

    /** Голову с плеч: тело ещё миг стоит, из шеи бьёт фонтан, потом рушится. */
    private static void behead(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float neck = body.neck();
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, -1.0F, 0.0F, -neck}}, 0.0F, neck - 4.0F, 0.0F, 0.2F,
                away.multiply(0.16).add(0.0, 0.44, 0.0), fall, SeveredPartEntity.FREE, 26.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, neck}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 11, 0.0F));
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
    private static void waist(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float waist = body.waist();
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, -1.0F, 0.0F, -waist}}, 0.0F, waist - 6.0F, 0.0F, 0.26F,
                away.multiply(0.13).add(0.0, 0.3, 0.0), fall, SeveredPartEntity.FREE, 15.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{0.0F, 1.0F, 0.0F, waist}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 6, 0.0F));
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
    private static void kesa(ServerWorld world, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore, float fall) {
        float y = body.waist() - 1.0F, nx = -0.678F, ny = -0.735F;
        float d = ny * y;
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{nx, ny, 0.0F, d}}, -1.5F, y - 7.0F, 0.0F, 0.26F,
                left.multiply(-0.11).add(away.multiply(0.03)).add(0.0, 0.1, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 8.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(new float[][]{{-nx, -ny, 0.0F, -d}}, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, fall, 8, 0.0F));
        burst(world, gore, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.2);
    }

    /** Скелет разлетается костями: череп, рёбра, руки и ноги — каждая часть сама по себе. */
    private static void shatter(ServerWorld world, LivingEntity target, int limbs, Vec3d away, Fx.Gore gore, float fall) {
        Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
        float[][] head = {{0.0F, -1.0F, 0.0F, 0.25F}};
        float[][] ribs = {{0.0F, 1.0F, 0.0F, -0.25F}, {0.0F, -1.0F, 0.0F, -12.0F}, {1.0F, 0.0F, 0.0F, -4.0F}, {-1.0F, 0.0F, 0.0F, -4.0F}};
        float[][] leftArm = {{1.0F, 0.0F, 0.0F, 4.01F}, {0.0F, 1.0F, 0.0F, -0.5F}};
        float[][] rightArm = {{-1.0F, 0.0F, 0.0F, 4.01F}, {0.0F, 1.0F, 0.0F, -0.5F}};
        float[][] leftLeg = {{0.0F, 1.0F, 0.0F, 12.0F}, {1.0F, 0.0F, 0.0F, 0.0F}, {-1.0F, 0.0F, 0.0F, -3.99F}};
        float[][] rightLeg = {{0.0F, 1.0F, 0.0F, 12.0F}, {-1.0F, 0.0F, 0.0F, 0.0F}, {1.0F, 0.0F, 0.0F, -3.99F}};
        var random = target.getRandom();
        SeveredPartEntity.spawn(world, target, limbs, new Piece(head, 0.0F, -4.0F, 0.0F, 0.2F, away.multiply(0.14).add(0.0, 0.34, 0.0), fall, SeveredPartEntity.FREE, 24.0F));
        SeveredPartEntity.spawn(world, target, limbs, new Piece(ribs, 0.0F, 6.0F, 0.0F, 0.16F, away.multiply(0.1).add(0.0, 0.12, 0.0), fall, SeveredPartEntity.FREE, 12.0F));
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

    /**
     * Крест со спины: первый косой удар только вскрывает врага, он ещё стоит. Следом встречный снизу, и тело распадается на четыре куска: верх с головой взлетает, бока расходятся,
     * ноги подламываются.
     */
    private static void cross(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int limbs, Body body, Vec3d away, Fx.Gore gore) {
        burst(world, gore, Limbs.modelToWorld(target, 0.0, body.waist() - 2.0F, 0.0), 0.7);
        Sync.animation(player, AnimationPayload.EXECUTE_FLASH, CROSS_SECOND_TICKS, CROSS_SECOND_MOVE);
        Scheduler.later(Math.round(CROSS_SECOND_TICKS * CROSS_SECOND_HIT), () -> {
            if (!target.isAlive()) {
                return;
            }
            float y = body.waist() - 2.0F, a = 0.678F, b = 0.735F, d = b * y;
            Vec3d left = modelAxis(target, 1.0, 0.0, 0.0);
            float[][] top = {{-a, -b, 0.0F, -d}, {a, -b, 0.0F, -d}}, bottom = {{a, b, 0.0F, d}, {-a, b, 0.0F, d}};
            float[][] leftSide = {{a, b, 0.0F, d}, {a, -b, 0.0F, -d}}, rightSide = {{-a, -b, 0.0F, -d}, {-a, b, 0.0F, d}};
            SeveredPartEntity.spawn(world, target, limbs, new Piece(top, 0.0F, y - 7.0F, 0.0F, 0.24F, away.multiply(0.15).add(0.0, 0.38, 0.0), FORWARD, SeveredPartEntity.FREE, 16.0F));
            SeveredPartEntity.spawn(world, target, limbs, new Piece(leftSide, 3.5F, y, 0.0F, 0.14F, left.multiply(0.2).add(away.multiply(0.05)).add(0.0, 0.2, 0.0), TO_LEFT, SeveredPartEntity.FREE, 20.0F));
            SeveredPartEntity.spawn(world, target, limbs, new Piece(rightSide, -3.5F, y, 0.0F, 0.14F, left.multiply(-0.2).add(away.multiply(0.05)).add(0.0, 0.2, 0.0), TO_RIGHT, SeveredPartEntity.FREE, 20.0F));
            SeveredPartEntity.spawn(world, target, limbs, new Piece(bottom, 0.0F, 24.0F, 0.0F, 0.0F, Vec3d.ZERO, FORWARD, 8, 0.0F));
            burst(world, gore, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.5);
            Fx.ring(world, Fx.REIATSU_RED, Limbs.modelToWorld(target, 0.0, y, 0.0), 1.2, 22);
            Fx.sound(target, SoundEvents.ITEM_TRIDENT_HIT, 1.2F, 0.5F);
            Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
            hideAndKill(world, player, target, limbs);
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
        if (NO_LOOT.remove(target.getUuid())) {
            // Вызванный сценой: от него только куски.
            target.discard();
            return;
        }
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
