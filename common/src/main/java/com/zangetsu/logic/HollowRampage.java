package com.zangetsu.logic;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.CallingPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.zangetsu.network.Net;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Смерть в финальной форме Пустого. Игрок падает на колени, вбивает Зангецу в землю: «это ещё не конец». В груди раскрывается круглая дыра, и Пустой забирает тело.
 * Дальше целый бой идёт сам: он рубит монстров по очереди разными приёмами. Потом его окружает толпа, всё ближе и ближе. Он смотрит на красную луну, загорается глаз, силой расталкивает всех,
 * проносится по каждому, и они ещё стоят, на каждом светится жёлтый след разреза. Он появляется за их спинами с мечом в руке: «Жалкие.» Хлёстким взмахом стряхивает кровь с клинка, и на этом взмахе вся толпа разваливается на куски. Маска разбивается, и форма недоступна сутки.
 *
 * <p>Мобы здесь без своего разума: всё, что они делают, делает сцена, поэтому они не взрываются, не стреляют и не мешают постановке. На открытом месте сцена идёт там, где игрок умер, и настоящие монстры вокруг тоже становятся её частью. В тесноте игрока переносит на пустое поле {@link #WORLD}.
 */
public final class HollowRampage {
    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, ZangetsuMod.id("rampage"));

    /** Сцена по тикам: упал и вбил меч, сказал, Пустой вышел, встал, бой, окружили, луна, глаз, огонь, толчок, серия, за спиной, «жалкие», взмах, толпа разваливается, маска, колено, конец. */
    public static final int PLANTED = 16, LINE_1 = 30, RELEASE = 96, GROWN = 146, RISEN = 168, FIGHT_FROM = 174,
            SURROUND = 434, MOON = 530, EYE = 564, IGNITE = 576, PUSH = 606, SWEEP = 616, BEHIND = 664, LINE_2 = 682, CHIBURI = 712, SEVER = CHIBURI + 4, MASK = 762, KNEEL = 774, END = 818;
    /** В какую сторону на небе висит красная луна (градусы взгляда, см. CrescentMoon). */
    public static final float MOON_YAW = -150.0F;
    /** Позы на месте: {@code param} анимации. */
    public static final int STANCE_BOWED = 0, STANCE_MOON = 1, STANCE_EYE = 2, STANCE_KNEEL = 3, STANCE_BURST = 4, STANCE_WAIT = 5;

    /** Новые приёмы меча (номера в SwordMoves на клиенте). */
    private static final int MOVE_LEAP = 10, MOVE_DOUBLE_SPIN = 11, MOVE_RISING_LEAP = 12, MOVE_CHIBURI = 13;
    /** Сколько монстров всего и сколько должно окружить его в конце. */
    private static final int CROWD = 30, SURROUNDERS = 14;
    private static final double START_RADIUS = 12.0, END_RADIUS = 3.0;
    private static final int[] SWEEP_MOVES = {0, 4, 1, 2, 6};

    /** Кого вызывает сцена: только те, кто не горит на солнце. Настоящих зомби и скелетов вокруг она просто тушит. */
    private static final List<EntityType<? extends MobEntity>> TYPES = List.of(EntityType.HUSK, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.HUSK, EntityType.PIGLIN_BRUTE,
            EntityType.ENDERMAN, EntityType.WITHER_SKELETON, EntityType.PILLAGER, EntityType.WITHER_SKELETON, EntityType.CREEPER);

    /** Шаг боя: чем рубит, какой приём меча, сколько длится, когда удар, через сколько следующий, скольких задевает, бьёт ли он так, что дрожит земля. */
    private record Step(Execution.Variant cut, int move, int duration, float hit, int next, int targets, boolean slam) {
    }

    private static final Step[] STEPS = {
            new Step(Execution.Variant.KESA, 0, 11, 0.42F, 16, 1, false),
            new Step(Execution.Variant.BEHEAD, 2, 10, 0.42F, 12, 1, false),
            new Step(Execution.Variant.WAIST, 4, 10, 0.40F, 20, 1, false),
            new Step(Execution.Variant.CLEAVE, MOVE_LEAP, 26, 0.50F, 34, 1, true),
            new Step(Execution.Variant.GYAKU, 1, 11, 0.40F, 14, 1, false),
            new Step(Execution.Variant.SPLIT_UP, 6, 12, 0.44F, 22, 1, false),
            new Step(Execution.Variant.CHEST, MOVE_DOUBLE_SPIN, 30, 0.62F, 36, 3, true),
            new Step(Execution.Variant.SLICES, MOVE_RISING_LEAP, 26, 0.42F, 30, 1, false),
            new Step(Execution.Variant.QUARTER, 3, 12, 0.47F, 30, 1, false),
            new Step(Execution.Variant.TRIPLE, 0, 11, 0.42F, 34, 1, true)};

    /** Монстр в сцене: кто он, вызван сценой или настоящий, его место в кольце, скорость, когда летит от толчка, и что с ним уже случилось. */
    private static final class Actor {
        final MobEntity mob;
        final boolean summoned;
        double angle, extra, vx, vy, vz;
        boolean used, marked;
        final int kind;

        Actor(MobEntity mob, boolean summoned, int kind) {
            this.mob = mob;
            this.summoned = summoned;
            this.kind = kind;
        }
    }

    private static final class Session {
        int tick;
        ServerWorld world;
        Vec3d center;
        float yaw;
        RegistryKey<World> homeWorld;
        Vec3d home;
        float homeYaw, homePitch;
        boolean arena;
        final List<Actor> actors = new ArrayList<>();
        int step, nextAt = FIGHT_FROM;
        final List<Actor> sweep = new ArrayList<>();
        int swept;
        Vec3d last;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private HollowRampage() {
    }

    public static boolean isActive(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Пустой неуязвим: сцена поставлена. От пустоты и команд защиты нет. */
    public static boolean protects(ServerPlayerEntity player) {
        return SESSIONS.containsKey(player.getUuid());
    }

    public static boolean interceptDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity player) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || SESSIONS.containsKey(player.getUuid())) {
            return false;
        }
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded || !data.hollowActive || !HollowForm.isFinal(data) || HollowForm.isTransforming(player) || SwordStacks.find(player).isEmpty()) {
            return false;
        }
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).isOf(Items.TOTEM_OF_UNDYING)) {
                return false;
            }
        }
        start(player);
        return true;
    }

    public static void start(ServerPlayerEntity player) {
        Session s = new Session();
        s.yaw = player.getYaw();
        s.homeWorld = player.getWorld().getRegistryKey();
        s.home = player.getPos();
        s.homeYaw = player.getYaw();
        s.homePitch = player.getPitch();
        player.setHealth(1.0F);
        player.clearStatusEffects();
        player.extinguish();
        player.stopRiding();
        player.stopFallFlying();
        player.setVelocity(Vec3d.ZERO);
        Reaction.forget(player);
        if (!SwordStacks.isHolding(player)) {
            ItemStack sword = SwordStacks.find(player).get(0);
            ItemStack moved = sword.copy();
            sword.setCount(0);
            SwordStacks.giveToHand(player, moved);
        }
        ServerWorld world = player.getServerWorld();
        double feet = ground(world, player.getX(), player.getZ(), player.getY());
        if (open(world, player.getBlockPos(), feet)) {
            s.center = new Vec3d(player.getX(), feet, player.getZ());
            s.world = world;
            player.networkHandler.requestTeleport(s.center.x, s.center.y, s.center.z, s.yaw, 0.0F);
            recruit(world, player, s);
        } else {
            ServerWorld arena = player.getServer().getWorld(WORLD);
            if (arena == null) {
                s.center = player.getPos();
                s.world = world;
            } else {
                Vec3d middle = Dream.center(player.getUuid());
                Dream.loadField(arena, middle);
                s.center = new Vec3d(middle.x, Calling.clearing(arena, middle) + 1, middle.z);
                s.world = arena;
                s.arena = true;
                player.teleport(arena, s.center.x, s.center.y, s.center.z, s.yaw, 0.0F);
            }
        }
        s.last = s.center;
        SESSIONS.put(player.getUuid(), s);
        Cinematic.lock(player, END + 4);
        send(player, CallingPayload.BARS_ON, "", 0);
        Sync.animation(player, AnimationPayload.RAMPAGE_BEGIN, END, Math.round(s.yaw));
        Sync.animation(player, AnimationPayload.RAMPAGE_DEATH, RISEN);
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
    }

    /** Открытое место: видно небо, вокруг почти ровно, нет воды. В пещере или на обрыве такую сцену не поставить. */
    private static boolean open(ServerWorld world, BlockPos at, double feet) {
        if (!world.getDimension().hasSkyLight() || world.getDimension().hasCeiling() || world.getRegistryKey() == Calling.WORLD || world.getRegistryKey() == Dream.WORLD) {
            return false;
        }
        BlockPos stand = BlockPos.ofFloored(at.getX() + 0.5, feet, at.getZ() + 0.5);
        if (!world.isSkyVisible(stand) || Math.abs(feet - at.getY()) > 6.0) {
            return false;
        }
        for (int r = 4; r <= 12; r += 4) {
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2.0 * i / 8.0;
                int x = MathHelper.floor(at.getX() + Math.cos(a) * r), z = MathHelper.floor(at.getZ() + Math.sin(a) * r);
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(top - feet) > 3.0 || !world.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double ground(ServerWorld world, double x, double z, double near) {
        double top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
        return top < world.getBottomY() + 1 ? near : top;
    }

    private static void recruit(ServerWorld world, ServerPlayerEntity player, Session s) {
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(22.0), m -> m instanceof Monster && m.isAlive()
                && !(m instanceof WardenEntity) && !(m instanceof WitherEntity) && !(m instanceof ElderGuardianEntity))) {
            s.actors.add(prepare(mob, false, s, player.getRandom().nextInt(3)));
        }
    }

    private static Actor prepare(MobEntity mob, boolean summoned, Session s, int kind) {
        mob.setAiDisabled(true);
        mob.setPersistent();
        if (mob instanceof CreeperEntity creeper) {
            creeper.setFuseSpeed(-1);
        }
        Actor actor = new Actor(mob, summoned, kind);
        actor.angle = Math.atan2(mob.getZ() - s.center.z, mob.getX() - s.center.x);
        return actor;
    }

    private static void spawnCrowd(ServerPlayerEntity player, Session s, int count, double from, double to) {
        ServerWorld world = s.world;
        for (int i = 0; i < count; i++) {
            double a = player.getRandom().nextDouble() * Math.PI * 2.0, r = from + player.getRandom().nextDouble() * (to - from);
            double x = s.center.x + Math.cos(a) * r, z = s.center.z + Math.sin(a) * r;
            double y = ground(world, x, z, s.center.y);
            EntityType<? extends MobEntity> type = TYPES.get(player.getRandom().nextInt(TYPES.size()));
            MobEntity mob = type.create(world);
            if (mob == null) {
                continue;
            }
            mob.refreshPositionAndAngles(x, y, z, player.getRandom().nextFloat() * 360.0F, 0.0F);
            mob.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(x, y, z)), SpawnReason.EVENT, null);
            mob.setBaby(false);
            world.spawnEntity(mob);
            Execution.noLoot(mob);
            s.actors.add(prepare(mob, true, s, i));
            Vec3d at = new Vec3d(x, y + 0.9, z);
            Fx.particles(world, ParticleTypes.LARGE_SMOKE, at, 10, 0.3, 0.02);
            Fx.particles(world, Fx.REIATSU_BLACK, at, 16, 0.35, 0.02);
        }
    }

    private static void send(ServerPlayerEntity player, int event, String key, int ticks) {
        Net.send(player, new CallingPayload(event, key, ticks, 0.0, 0.0, 0.0));
    }

    // ---------------------------------------------------------------- ход сцены

    public static void tick(ServerPlayerEntity player) {
        Session s = SESSIONS.get(player.getUuid());
        if (s == null) {
            return;
        }
        if (!player.isAlive()) {
            cleanUp(player, s);
            return;
        }
        s.tick++;
        int t = s.tick;
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0F;
        ServerWorld world = s.world;
        drive(player, s);

        if (t == PLANTED) {
            Fx.sound(player, SoundEvents.BLOCK_ANVIL_LAND, 0.8F, 0.5F);
            Fx.sound(player, SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1.0F, 0.6F);
            Fx.ring(world, Fx.REIATSU_BLACK, s.center.add(0.0, 0.1, 0.0), 1.2, 24);
            kick(player, 0.4F);
        }
        if (t > PLANTED && t < RELEASE && t % 22 == 0) {
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.5F);
        }
        if (t == LINE_1) {
            send(player, CallingPayload.SPEECH, "zangetsu.rampage.line_1", 58);
        }
        if (t == RELEASE) {
            release(player, s);
        }
        if (t > RELEASE && t < GROWN) {
            pillar(world, s, t);
        }
        if (t == RELEASE + 8) {
            spawnCrowd(player, s, Math.max(0, CROWD - s.actors.size()), 15.0, 22.0);
        }
        if (t == RISEN - 14) {
            Fx.sound(player, SoundEvents.ITEM_TRIDENT_RETURN, 1.0F, 0.5F);
        }
        if (t == RISEN) {
            shockwave(world, player.getPos(), 5.0);
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_ROAR, 1.0F, 0.8F);
            kick(player, 0.7F);
        }
        if (t >= FIGHT_FROM && t < SURROUND) {
            fight(player, s);
        }
        if (t == SURROUND) {
            surround(player, s);
        }
        if (t >= SURROUND && t < MOON + 40 && t % Math.max(8, 26 - (t - SURROUND) / 8) == 0) {
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.6F);
        }
        if (t == MOON) {
            player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), MOON_YAW, 0.0F);
            player.setHeadYaw(MOON_YAW);
            player.setBodyYaw(MOON_YAW);
            Sync.animation(player, AnimationPayload.RAMPAGE_STANCE, EYE - MOON, STANCE_MOON);
            Fx.sound(player, SoundEvents.BLOCK_BELL_RESONATE, 0.9F, 0.5F);
        }
        if (t == EYE) {
            Sync.animation(player, AnimationPayload.RAMPAGE_STANCE, PUSH - EYE, STANCE_EYE);
        }
        if (t == IGNITE) {
            Fx.sound(player, SoundEvents.ITEM_FIRECHARGE_USE, 1.0F, 0.6F);
            Fx.sound(player, SoundEvents.ENTITY_BLAZE_SHOOT, 0.8F, 0.5F);
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.9F, 0.8F);
        }
        if (t == PUSH) {
            push(player, s);
        }
        if (t == SWEEP) {
            beginSweep(s);
        }
        if (t >= SWEEP && t < BEHIND) {
            sweep(player, s);
        }
        if (t > BEHIND && t < CHIBURI && t % 3 == 0) {
            drip(world, player, s);
        }
        if (t == BEHIND) {
            behind(player, s);
        }
        if (t == LINE_2) {
            send(player, CallingPayload.SPEECH, "zangetsu.rampage.line_2", 30);
        }
        if (t == CHIBURI) {
            Sync.animation(player, AnimationPayload.EXECUTE_FLASH, 14, MOVE_CHIBURI);
        }
        if (t == SEVER) {
            sever(player, s);
        }
        if (t == MASK - 24 || t == MASK - 16 || t == MASK - 8) {
            crack(player, t == MASK - 24 ? 29 : (t == MASK - 16 ? 14 : 4));
        }
        if (t == MASK) {
            breakMask(player, s);
        }
        if (t == END - 10) {
            Sync.fx(player, ClientFxPayload.WHITE_FLASH, 24);
        }
        if (t >= END) {
            cleanUp(player, s);
        }
    }

    /** Толпа идёт сама: пока он дерётся, подступает к нему, потом сжимается кольцом. От толчка летит, а во время серии всё стоит, будто время встало. */
    private static void drive(ServerPlayerEntity player, Session s) {
        int t = s.tick;
        for (Actor a : s.actors) {
            a.mob.extinguish();
        }
        if (t >= SWEEP) {
            return;
        }
        ServerWorld world = s.world;
        float closing = MathHelper.clamp((t - SURROUND) / (float) (MOON + 12 - SURROUND), 0.0F, 1.0F);
        double radius = MathHelper.lerp(closing * closing * (3.0F - 2.0F * closing), START_RADIUS, END_RADIUS);
        for (Actor a : s.actors) {
            MobEntity mob = a.mob;
            if (a.used || !mob.isAlive()) {
                continue;
            }
            if (t >= PUSH) {
                a.vy -= 0.07;
                double x = mob.getX() + a.vx, y = mob.getY() + a.vy, z = mob.getZ() + a.vz;
                a.vx *= 0.92;
                a.vz *= 0.92;
                double floor = ground(world, x, z, mob.getY());
                if (y < floor) {
                    y = floor;
                    a.vy = 0.0;
                }
                mob.setPosition(x, y, z);
                continue;
            }
            double goalX, goalZ, stop, speed;
            if (t >= SURROUND) {
                goalX = s.center.x + Math.cos(a.angle) * (radius + a.extra);
                goalZ = s.center.z + Math.sin(a.angle) * (radius + a.extra);
                stop = 0.05;
                speed = 0.17;
                mob.setAttacking(true);
            } else {
                goalX = player.getX();
                goalZ = player.getZ();
                stop = 2.8;
                speed = 0.06;
            }
            double dx = goalX - mob.getX(), dz = goalZ - mob.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (MathHelper.atan2(player.getZ() - mob.getZ(), player.getX() - mob.getX()) * (180.0 / Math.PI)) - 90.0F;
            mob.setYaw(yaw);
            mob.setBodyYaw(yaw);
            mob.setHeadYaw(yaw);
            if (d > stop) {
                double step = Math.min(speed, d - stop);
                double x = mob.getX() + dx / d * step, z = mob.getZ() + dz / d * step;
                mob.setPosition(x, ground(world, x, z, mob.getY()), z);
            }
        }
    }

    /** Пустой вышел: удар, столб чёрного и алого, рёв. */
    private static void release(ServerPlayerEntity player, Session s) {
        ServerWorld world = s.world;
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_ROAR, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.ENTITY_WITHER_SPAWN, 0.9F, 0.5F);
        Fx.sound(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.9F, 0.5F);
        shockwave(world, player.getPos(), 7.0);
        Sync.fx(player, ClientFxPayload.IMPACT_FLASH, 12);
        kick(player, 0.9F);
    }

    private static void pillar(ServerWorld world, Session s, int t) {
        for (int i = 0; i < 6; i++) {
            Vec3d at = s.center.add(0.0, 2.6 + i * 1.3 + (t % 4) * 0.3, 0.0);
            Fx.particles(world, Fx.REIATSU_BLACK, at, 12, 0.4 + i * 0.05, 0.02);
            Fx.particles(world, Fx.REIATSU_RED, at, 6, 0.3 + i * 0.05, 0.02);
        }
    }

    private static void shockwave(ServerWorld world, Vec3d at, double radius) {
        for (double r = 1.0; r <= radius; r += 1.2) {
            Fx.ring(world, Fx.REIATSU_RED, at.add(0.0, 0.15, 0.0), r, (int) (r * 14.0));
            Fx.ring(world, Fx.REIATSU_BLACK, at.add(0.0, 0.3, 0.0), r * 0.85, (int) (r * 10.0));
        }
    }

    private static void kick(ServerPlayerEntity player, float strength) {
        Sync.fx(player, ClientFxPayload.CAMERA_KICK, Math.round(strength * 100.0F));
    }

    // ---------------------------------------------------------------- бой

    private static void fight(ServerPlayerEntity player, Session s) {
        if (s.step >= STEPS.length || s.tick < s.nextAt) {
            return;
        }
        Step step = STEPS[s.step++];
        s.nextAt = s.tick + step.next();
        List<Actor> targets = nearest(player, s, step.targets());
        if (targets.isEmpty()) {
            return;
        }
        ServerWorld world = s.world;
        Actor first = targets.get(0);
        Execution.flash(world, player, first.mob);
        for (Actor a : targets) {
            a.used = true;
            Vec3d toPlayer = player.getPos().subtract(a.mob.getPos());
            float yaw = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0F;
            a.mob.setYaw(yaw);
            a.mob.setBodyYaw(yaw);
            a.mob.setHeadYaw(yaw);
        }
        Sync.animation(player, AnimationPayload.EXECUTE_FLASH, step.duration(), step.move());
        UUID self = player.getUuid();
        Scheduler.later(Math.round(step.duration() * step.hit()), () -> {
            if (!SESSIONS.containsKey(self)) {
                return;
            }
            for (int i = 0; i < targets.size(); i++) {
                Actor a = targets.get(i);
                if (a.mob.isAlive() && a.mob.getWorld() == player.getWorld()) {
                    Execution.Variant cut = i == 0 ? step.cut() : (i == 1 ? Execution.Variant.SLICES : Execution.Variant.WAIST);
                    Execution.scripted(world, player, a.mob, cut);
                }
            }
            kick(player, step.slam() ? 0.8F : 0.3F);
            if (step.slam()) {
                shockwave(world, player.getPos(), 4.0);
                Fx.sound(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.6F, 0.7F);
                Fx.sound(player, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 1.0F, 0.6F);
            }
        });
    }

    /** Ближайшие монстры, которых можно разрубить. Кончились — приходят новые. */
    private static List<Actor> nearest(ServerPlayerEntity player, Session s, int count) {
        List<Actor> free = new ArrayList<>();
        for (Actor a : s.actors) {
            if (!a.used && a.mob.isAlive() && Limbs.canBeExecuted(a.mob)) {
                free.add(a);
            }
        }
        if (free.size() < count) {
            spawnCrowd(player, s, count + 2, 5.0, 8.0);
            return nearest(player, s, count);
        }
        free.sort(Comparator.comparingDouble(a -> a.mob.squaredDistanceTo(player)));
        return new ArrayList<>(free.subList(0, count));
    }

    // ---------------------------------------------------------------- окружение

    private static void surround(ServerPlayerEntity player, Session s) {
        ServerWorld world = s.world;
        Vec3d from = player.getPos();
        trail(world, from, s.center);
        player.networkHandler.requestTeleport(s.center.x, s.center.y, s.center.z, s.yaw, 0.0F);
        player.setHeadYaw(s.yaw);
        player.setBodyYaw(s.yaw);
        Sync.animation(player, AnimationPayload.RAMPAGE_STANCE, MOON - SURROUND, STANCE_BOWED);
        int alive = 0;
        for (Actor a : s.actors) {
            if (!a.used && a.mob.isAlive()) {
                alive++;
            }
        }
        if (alive < SURROUNDERS) {
            spawnCrowd(player, s, SURROUNDERS - alive, 13.0, 15.0);
        }
        List<Actor> ring = new ArrayList<>();
        for (Actor a : s.actors) {
            if (!a.used && a.mob.isAlive()) {
                ring.add(a);
            }
        }
        ring.sort(Comparator.comparingDouble(a -> Math.atan2(a.mob.getZ() - s.center.z, a.mob.getX() - s.center.x)));
        for (int i = 0; i < ring.size(); i++) {
            ring.get(i).angle = Math.PI * 2.0 * i / ring.size();
            ring.get(i).extra = i % 2 == 0 ? 0.0 : 1.5;
        }
        Fx.sound(player, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9F, 0.8F);
    }

    /** Он силой расталкивает всех: мобы взлетают и расходятся кругом. */
    private static void push(ServerPlayerEntity player, Session s) {
        ServerWorld world = s.world;
        Sync.animation(player, AnimationPayload.RAMPAGE_STANCE, SWEEP - PUSH + 2, STANCE_BURST);
        for (Actor a : s.actors) {
            if (a.used || !a.mob.isAlive()) {
                continue;
            }
            double dx = a.mob.getX() - s.center.x, dz = a.mob.getZ() - s.center.z, d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            double power = 0.72 + player.getRandom().nextDouble() * 0.34;
            a.vx = dx / d * power;
            a.vz = dz / d * power;
            a.vy = 0.46 + player.getRandom().nextDouble() * 0.16;
        }
        shockwave(world, s.center, 11.0);
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.0F, 0.6F);
        Fx.sound(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.ENTITY_WITHER_SHOOT, 0.9F, 0.5F);
        Sync.fx(player, ClientFxPayload.IMPACT_FLASH, 10);
        kick(player, 1.3F);
    }

    // ---------------------------------------------------------------- серия

    private static void beginSweep(Session s) {
        for (Actor a : s.actors) {
            if (!a.used && a.mob.isAlive()) {
                s.sweep.add(a);
            }
        }
        // Идёт по кругу, а не как попало: так видно, что он пробегает всех.
        s.sweep.sort(Comparator.comparingDouble(a -> Math.atan2(a.mob.getZ() - s.center.z, a.mob.getX() - s.center.x)));
    }

    private static void sweep(ServerPlayerEntity player, Session s) {
        int count = s.sweep.size();
        if (count == 0) {
            return;
        }
        ServerWorld world = s.world;
        int upTo = Math.min(count, (s.tick - SWEEP + 1) * count / (BEHIND - SWEEP - 2));
        while (s.swept < upTo) {
            int index = s.swept++;
            Actor a = s.sweep.get(index);
            a.marked = true;
            MobEntity mob = a.mob;
            double dx = mob.getX() - s.center.x, dz = mob.getZ() - s.center.z, d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            double x = mob.getX() + dx / d * 1.5, z = mob.getZ() + dz / d * 1.5;
            double y = ground(world, x, z, s.center.y);
            float yaw = (float) (MathHelper.atan2(mob.getZ() - z, mob.getX() - x) * (180.0 / Math.PI)) - 90.0F;
            trail(world, s.last, new Vec3d(x, y, z));
            s.last = new Vec3d(x, y, z);
            player.networkHandler.requestTeleport(x, y, z, yaw, 0.0F);
            player.setHeadYaw(yaw);
            player.setBodyYaw(yaw);
            Sync.animation(player, AnimationPayload.EXECUTE_FLASH, 6, SWEEP_MOVES[index % SWEEP_MOVES.length]);
            Sync.mark(mob);
            Vec3d at = mob.getPos().add(0.0, mob.getHeight() * 0.5, 0.0);
            Fx.particles(world, Fx.CUT_GLOW, at, 14, 0.25, 0.04);
            Fx.particles(world, ParticleTypes.SWEEP_ATTACK, at, 1, 0.1, 0.0);
            float k = (float) index / count;
            Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.8F + k * 0.8F);
            Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.6F, 1.0F + k * 0.6F);
        }
    }

    /** Чёрно-алая полоса там, где он пронёсся. */
    private static void trail(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d line = to.subtract(from);
        double length = line.length();
        for (double d = 0.0; d < length; d += 0.7) {
            Vec3d at = from.add(line.multiply(d / Math.max(0.01, length))).add(0.0, 1.0, 0.0);
            Fx.particles(world, Fx.REIATSU_BLACK, at, 5, 0.18, 0.01);
            Fx.particles(world, Fx.REIATSU_RED, at, 3, 0.14, 0.01);
        }
    }

    /** Он уже за их спинами, лицом от них: стоит и ждёт. Всё замерло. */
    private static void behind(ServerPlayerEntity player, Session s) {
        ServerWorld world = s.world;
        double reach = END_RADIUS;
        for (Actor a : s.sweep) {
            reach = Math.max(reach, Math.sqrt(a.mob.squaredDistanceTo(s.center.x, a.mob.getY(), s.center.z)));
        }
        double r = Math.toRadians(s.yaw);
        Vec3d forward = new Vec3d(-Math.sin(r), 0.0, Math.cos(r));
        Vec3d spot = s.center.add(forward.multiply(reach + 3.5));
        double y = ground(world, spot.x, spot.z, s.center.y);
        player.networkHandler.requestTeleport(spot.x, y, spot.z, s.yaw, 0.0F);
        player.setHeadYaw(s.yaw);
        player.setBodyYaw(s.yaw);
        Sync.animation(player, AnimationPayload.RAMPAGE_STANCE, CHIBURI - BEHIND, STANCE_WAIT);
        Fx.sound(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.9F, 0.5F);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8F, 0.6F);
    }

    /** Пока он стоит и ждёт, с опущенного клинка капает кровь. */
    private static void drip(ServerWorld world, ServerPlayerEntity player, Session s) {
        double r = Math.toRadians(s.yaw);
        Vec3d forward = new Vec3d(-Math.sin(r), 0.0, Math.cos(r)), right = new Vec3d(-forward.z, 0.0, forward.x);
        Vec3d tip = player.getPos().add(right.multiply(1.25)).add(forward.multiply(0.9)).add(0.0, 0.32, 0.0);
        world.spawnParticles(Fx.BLOOD, tip.x, tip.y - 0.12, tip.z, 1, 0.01, 0.1, 0.01, 0.0);
    }

    /** Хлёсткий взмах: кровь слетает с клинка на землю, и на этом взмахе все, кого он прошёл, разваливаются на куски. */
    private static void sever(ServerPlayerEntity player, Session s) {
        ServerWorld world = s.world;
        double r = Math.toRadians(s.yaw);
        Vec3d forward = new Vec3d(-Math.sin(r), 0.0, Math.cos(r)), right = new Vec3d(-forward.z, 0.0, forward.x);
        for (int i = 1; i <= 10; i++) {
            Vec3d at = player.getPos().add(right.multiply(0.5 + i * 0.28)).add(forward.multiply(0.5)).add(0.0, 1.1 - i * 0.1, 0.0);
            Fx.particles(world, Fx.BLOOD, at, 8, 0.06, 0.04);
        }
        Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
        Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
        Fx.sound(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0F, 0.6F);
        Fx.sound(player, SoundEvents.ITEM_TRIDENT_HIT, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 1.0F, 0.6F);
        Sync.fx(player, ClientFxPayload.IMPACT_FLASH, 6);
        kick(player, 0.9F);
        for (Actor a : s.sweep) {
            if (a.mob.isAlive() && a.mob.getWorld() == player.getWorld()) {
                a.used = true;
                Vec3d away = a.mob.getPos().subtract(s.center).multiply(1.0, 0.0, 1.0);
                away = away.lengthSquared() < 1.0E-4 ? forward : away.normalize();
                Execution.sever(world, player, a.mob, a.kind, away);
            }
        }
    }

    // ---------------------------------------------------------------- маска

    /** Маска трескается: {@code seconds} — столько формы как будто осталось, по этому числу она и выглядит. */
    private static void crack(ServerPlayerEntity player, int seconds) {
        ModAttachments.data(player).hollowUsed = HollowForm.BUDGET - seconds * 20;
        Sync.hollow(player, true);
        Fx.sound(player, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 1.0F, 0.7F);
        Fx.sound(player, SoundEvents.BLOCK_GLASS_HIT, 0.8F, 0.6F);
    }

    private static void breakMask(ServerPlayerEntity player, Session s) {
        ZangetsuData data = ModAttachments.data(player);
        HollowForm.end(player, true);
        data.hollowUsed = HollowForm.BUDGET;
        data.hollowLock = player.getServer().getOverworld().getTimeOfDay() + 24000L;
        player.setHealth(Math.min(player.getMaxHealth(), 10.0F));
        Sync.animation(player, AnimationPayload.RAMPAGE_STANCE, END - MASK, STANCE_KNEEL);
        Fx.sound(player, SoundEvents.BLOCK_GLASS_BREAK, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.ITEM_SHIELD_BREAK, 0.9F, 0.6F);
        Sync.fx(player, ClientFxPayload.IMPACT_FLASH, 8);
        kick(player, 0.6F);
    }

    private static void cleanUp(ServerPlayerEntity player, Session s) {
        SESSIONS.remove(player.getUuid());
        for (Actor a : s.actors) {
            if (!a.mob.isAlive()) {
                continue;
            }
            if (a.summoned) {
                a.mob.discard();
            } else {
                a.mob.setAiDisabled(false);
                a.mob.setAttacking(false);
            }
        }
        send(player, CallingPayload.BARS_OFF, "", 0);
        Sync.animation(player, AnimationPayload.RAMPAGE_END, 1);
        if (s.arena) {
            ServerWorld home = player.getServer().getWorld(s.homeWorld);
            if (home != null) {
                player.teleport(home, s.home.x, s.home.y, s.home.z, s.homeYaw, s.homePitch);
            }
        }
    }

    public static void disconnect(ServerPlayerEntity player) {
        Session s = SESSIONS.get(player.getUuid());
        if (s != null) {
            cleanUp(player, s);
        }
    }

    /** Команда и новый старт: прервать, ничего не меняя в силе. */
    public static void abort(ServerPlayerEntity player) {
        disconnect(player);
    }

    public static void clear() {
        SESSIONS.clear();
    }
}
