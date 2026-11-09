package com.zangetsu.logic;

import com.zangetsu.network.ReactionPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.zangetsu.network.Net;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Казнь через проверку реакции. Враг, которому остался один удар, замирает, и на экране появляется круг со стрелкой. Успел кликнуть на хорошей зоне — казнь.
 * В финальной форме на круге есть ещё маленькая белая зона: отличное попадание — рывок за спину врага, «ты слишком медлителен» и казнь сзади. Промах — враг приходит в себя.
 *
 * <p>Сервер решает, когда начать, и проверяет ответ. Круг рисует и считает клик клиент, потому что важно то, что игрок видит на экране; сервер лишь сверяет время с запасом на задержку сети.
 */
public final class Reaction {
    /** Выключить совсем (для съёмки сцен, где мобов надо убивать обычным ударом). */
    public static boolean enabled = true;
    /** Сколько тиков стрелка стоит на месте: игрок успевает заметить круг. */
    public static final int LEAD = 7;
    /** Клики в начале оборота не считаются: игрок ещё добивает врага и не успел понять, что началась проверка. */
    public static final float IGNORE_UNTIL = 0.2F;
    /** Запас на задержку сети (тики) и перерыв между проверками. */
    private static final int LATENCY = 12, COOLDOWN = 30;
    /** На сколько тиков промах снимает врага с проверки: дальше его можно добить обычным ударом. */
    private static final int REFUSED_FOR = 200;
    /** Как часто искать готового врага (тики) и откуда: обычная казнь достаёт близко, финальная форма — шагом издалека. */
    private static final int SCAN_EVERY = 3;
    private static final double REACH = 6.0;
    /** Насколько враг должен быть перед игроком (косинус угла к взгляду). */
    private static final double IN_FRONT = 0.25;
    /** Где на круге могут начинаться зоны: не сразу, чтобы успеть среагировать, и не в самом конце. */
    private static final float ZONE_FROM = 0.42F, ZONE_TO = 0.9F;

    /** Запас попадания (доля круга): зона засчитывается чуть шире, чем нарисована, чтобы точное нажатие не срывалось из-за пары кадров. */
    public static final float GREAT_MARGIN = 0.022F, GOOD_MARGIN = 0.015F;
    public static final int MISS = 0, GOOD = 1, GREAT = 2;

    /** Куда попала стрелка {@code t}: на сервере и на клиенте считается одинаково. */
    public static int judge(float t, float from, float great, float good) {
        float end = from + great + good;
        if (great > 0.0F) {
            float greatEnd = from + great + GREAT_MARGIN;
            if (t >= from - GREAT_MARGIN && t < greatEnd) {
                return GREAT;
            }
            return t >= greatEnd && t < end + GOOD_MARGIN ? GOOD : MISS;
        }
        return t >= from - GOOD_MARGIN && t < end + GOOD_MARGIN ? GOOD : MISS;
    }

    private record Session(MobEntity mob, long start, int spin, float from, float great, float good) {
    }

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Long> REFUSED = new HashMap<>();

    private Reaction() {
    }

    public static boolean isActive(Entity player) {
        return ACTIVE.containsKey(player.getUuid());
    }

    /** Этого моба сейчас проверяют: меч сам его не трогает. */
    public static boolean isTarget(Entity mob) {
        for (Session session : ACTIVE.values()) {
            if (session.mob() == mob) {
                return true;
            }
        }
        return false;
    }

    private static boolean available(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        Long next = NEXT.get(player.getUuid());
        return enabled && data.bonded && data.level() >= 2 && SwordStacks.isHolding(player) && !isActive(player) && !Execution.isBusy(player) && !Cinematic.isLocked(player)
                && !HollowForm.isTransforming(player) && (next == null || player.getServer().getTicks() >= next);
    }

    /** Этого врага можно проверять: он казнится, ещё цел и не сорвал проверку только что. */
    private static boolean candidate(MobEntity mob, ServerPlayerEntity player) {
        Long refused = REFUSED.get(mob.getUuid());
        return Limbs.canBeExecuted(mob) && (ModAttachments.limbs(mob) & (Limbs.HEAD | Limbs.SPLIT)) == 0 && !Execution.isBusy(mob) && !isTarget(mob)
                && Abilities.isFoe(mob, player) && (refused == null || player.getServer().getTicks() >= refused);
    }

    private static double range(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        return data.hollowActive && HollowForm.isFinal(data) ? Execution.FLASH_RANGE : REACH;
    }

    /**
     * Удар игрока убил бы врага: вместо этого у него остаётся капля здоровья и начинается проверка. Иначе сильный меч убивал бы мобов раньше, чем проверка успеет появиться.
     * Вернёт true, если смерть отменена.
     */
    public static boolean interceptDeath(LivingEntity entity, DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player) || !(entity instanceof MobEntity mob) || !available(player) || !candidate(mob, player)
                || mob.squaredDistanceTo(player) > range(player) * range(player)) {
            return false;
        }
        mob.setHealth(1.0F);
        start(player, mob);
        return true;
    }

    /** Каждый тик игрока: следит за идущей проверкой и ищет врага, которому остался один удар. */
    public static void tick(ServerPlayerEntity player) {
        Session session = ACTIVE.get(player.getUuid());
        long now = player.getServer().getTicks();
        if (session != null) {
            if (!session.mob().isAlive() || session.mob().getWorld() != player.getWorld() || !SwordStacks.isHolding(player)) {
                cancel(player, session);
            } else if (now - session.start() > LEAD + session.spin() + LATENCY) {
                fail(player, session);
            }
            return;
        }
        if (now % SCAN_EVERY == 0 && available(player)) {
            MobEntity mob = ready(player);
            if (mob != null) {
                start(player, mob);
            }
        }
    }

    /** Ближайший враг перед игроком, с которым тот уже дерётся и которому остался один удар (или рубить больше нечего). */
    private static MobEntity ready(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        double range = range(player), oneHit = player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        Vec3d eye = player.getEyePos(), look = player.getRotationVector();
        List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, new Box(player.getBlockPos()).expand(range), mob -> mob.isAlive() && candidate(mob, player)
                && (mob.getAttacker() == player || Limbs.executable(mob, ModAttachments.limbs(mob), false, 0)));
        MobEntity best = null;
        double closest = range * range;
        for (MobEntity mob : mobs) {
            double distance = mob.squaredDistanceTo(player);
            boolean finished = mob.getHealth() <= oneHit || Limbs.executable(mob, ModAttachments.limbs(mob), false, 0);
            if (finished && distance < closest && player.canSee(mob) && look.dotProduct(mob.getBoundingBox().getCenter().subtract(eye).normalize()) > IN_FRONT) {
                closest = distance;
                best = mob;
            }
        }
        return best;
    }

    /** Начать проверку на этого врага. Открыто для сцен и проверок мода. */
    public static void start(ServerPlayerEntity player, MobEntity mob) {
        ZangetsuData data = ModAttachments.data(player);
        boolean hollow = data.hollowActive, finalForm = hollow && HollowForm.isFinal(data);
        // Без маски зона маленькая, в форме Пустого — средняя, в финальной форме к ней прибавляется белая.
        float good = hollow ? 0.17F : 0.1F, great = finalForm ? 0.05F : 0.0F;
        int spin = finalForm ? 20 : (hollow ? 22 : 24);
        float from = ZONE_FROM + player.getRandom().nextFloat() * (ZONE_TO - ZONE_FROM - good - great);
        ACTIVE.put(player.getUuid(), new Session(mob, player.getServer().getTicks(), spin, from, great, good));
        // Враг пошатнулся и замер: у него остался один удар.
        mob.setAiDisabled(true);
        mob.setVelocity(Vec3d.ZERO);
        mob.getNavigation().stop();
        if (mob instanceof CreeperEntity creeper) {
            creeper.setFuseSpeed(-1);
        }
        ServerWorld world = player.getServerWorld();
        Fx.particles(world, ParticleTypes.CRIT, mob.getBoundingBox().getCenter(), 14, 0.3, 0.25);
        Fx.particles(world, Fx.REIATSU_RED, mob.getBoundingBox().getCenter(), 10, 0.35, 0.02);
        Fx.sound(mob, SoundEvents.ITEM_TRIDENT_HIT_GROUND, 0.6F, 0.7F);
        Net.send(player, new ReactionPayload(mob.getId(), LEAD, spin, from, great, good));
    }

    /** Клик игрока: {@code encoded} — положение стрелки на его экране, доля круга × 10000. */
    public static void click(ServerPlayerEntity player, int encoded) {
        Session session = ACTIVE.remove(player.getUuid());
        if (session == null) {
            return;
        }
        long now = player.getServer().getTicks();
        NEXT.put(player.getUuid(), now + COOLDOWN);
        float expected = (now - session.start() - LEAD) / (float) session.spin(), slack = LATENCY / (float) session.spin();
        float t = encoded / 10000.0F;
        // Клиент может отставать (кадр подвис), и это не его вина: важно то, что игрок видел на экране. А вот забежать вперёд часов сервера он не может.
        if (t > expected + slack) {
            t = expected;
        }
        MobEntity mob = session.mob();
        if (!mob.isAlive()) {
            return;
        }
        switch (judge(t, session.from(), session.great(), session.good())) {
            case GREAT -> Execution.behind(player, mob);
            case GOOD -> Execution.fromReaction(player, mob);
            default -> refuse(player, mob);
        }
    }

    /** Время вышло: игрок не успел. */
    private static void fail(ServerPlayerEntity player, Session session) {
        ACTIVE.remove(player.getUuid());
        NEXT.put(player.getUuid(), (long) player.getServer().getTicks() + COOLDOWN);
        refuse(player, session.mob());
    }

    /** Промах: враг приходит в себя, и добить его теперь можно только обычным ударом. */
    private static void refuse(ServerPlayerEntity player, MobEntity mob) {
        release(mob);
        REFUSED.put(mob.getUuid(), (long) player.getServer().getTicks() + REFUSED_FOR);
        Fx.sound(mob, SoundEvents.ITEM_SHIELD_BLOCK, 0.6F, 0.6F);
    }

    /** Цель пропала или меч убрали: круг просто исчезает. */
    private static void cancel(ServerPlayerEntity player, Session session) {
        ACTIVE.remove(player.getUuid());
        NEXT.put(player.getUuid(), (long) player.getServer().getTicks() + COOLDOWN);
        release(session.mob());
        Net.send(player, new ReactionPayload(-1, 0, 1, 0.0F, 0.0F, 0.0F));
    }

    private static void release(MobEntity mob) {
        if (mob.isAlive()) {
            mob.setAiDisabled(false);
        }
    }

    /** Игрок вышел: враг не должен остаться замершим навсегда. */
    public static void forget(ServerPlayerEntity player) {
        Session session = ACTIVE.remove(player.getUuid());
        if (session != null) {
            release(session.mob());
        }
        NEXT.remove(player.getUuid());
    }

    public static void clear() {
        ACTIVE.clear();
        NEXT.clear();
        REFUSED.clear();
    }
}
