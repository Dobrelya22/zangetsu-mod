package com.zangetsu.logic;

import com.zangetsu.entity.GetsugaEntity;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Форма Пустого (уровень IV): маска, горящий глаз меча, серповидная луна в небе и Гецуга Теншо по правой
 * кнопке. Две минуты в игровые сутки; время можно тратить частями. На уровне V это финальная форма: маска
 * с одним рогом, эффекты сильнее, Гецуга крупнее и больнее.
 */
public final class HollowForm {
    public static final int BUDGET = 2 * 60 * 20;
    /** Превращение на уровне IV: рука по лицу и маска. Сколько длится и когда рука хлёстко уходит в сторону. */
    public static final int MASK_TICKS = 72, MASK_WHIP = 44;
    /**
     * Превращение на уровне V: сколько длится весь обряд и его мгновения — молния, конец стояния на колене, глаза загораются, он выпрямился,
     * меч лёг на плечо, выпад.
     */
    public static final int RITE_TICKS = 176, RITE_STRIKE = 40, RITE_KNEEL_END = 86, RITE_IGNITE = 112, RITE_STAND = 118, RITE_SHOULDER = 134, RITE_LUNGE = 155;
    /** Сколько ранит выпад в конце обряда. */
    private static final float LUNGE_DAMAGE = 14.0F;
    /** Форма включается сразу, без сцены: для постановочных съёмок, где важно не превращение. */
    public static boolean skipRite;
    private static final Set<UUID> TRANSFORMING = new HashSet<>();
    public static final int GETSUGA_COOLDOWN = 24;
    /** Замах Гецуги: через столько тиков волна срывается с клинка; вся анимация — дольше. */
    public static final int GETSUGA_WINDUP = 7;
    public static final int GETSUGA_ANIMATION = 18;
    private static final Set<UUID> CHARGING = new HashSet<>();

    @SuppressWarnings("unchecked")
    private static final RegistryEntry<StatusEffect>[] BUFFS = new RegistryEntry[]{
            StatusEffects.STRENGTH, StatusEffects.SPEED, StatusEffects.RESISTANCE, StatusEffects.REGENERATION, StatusEffects.JUMP_BOOST
    };
    private static final int[] BUFF_LEVELS = {1, 1, 1, 0, 0};
    private static final int[] FINAL_BUFF_LEVELS = {2, 2, 1, 1, 1};

    private HollowForm() {
    }

    /** Финальная форма: Пустой на уровне V. */
    public static boolean isFinal(ZangetsuData data) {
        return data.level() >= Bond.FINAL_LEVEL;
    }

    private static long day(ServerPlayerEntity player) {
        return player.getServer().getOverworld().getTimeOfDay() / 24000L;
    }

    private static void rollDay(ServerPlayerEntity player, ZangetsuData data) {
        long day = day(player);
        if (data.hollowDay != day) {
            data.hollowDay = day;
            data.hollowUsed = 0;
        }
    }

    /** Маска разбита в сцене смерти: формы нет целые игровые сутки. */
    public static boolean locked(ServerPlayerEntity player, ZangetsuData data) {
        return player.getServer().getOverworld().getTimeOfDay() < data.hollowLock;
    }

    public static int ticksLeft(ServerPlayerEntity player, ZangetsuData data) {
        rollDay(player, data);
        if (locked(player, data)) {
            return 0;
        }
        return Math.max(0, BUDGET - data.hollowUsed);
    }

    /**
     * Убит монстр: шкала формы заполняется, поэтому маску можно надевать несколько раз за день. Чем крепче монстр, тем больше: за зомби 5 секунд формы. В самой форме вдвое меньше, иначе в бою она не кончалась бы никогда.
     * Разбитую маску это не чинит, и сцена, где Пустой забрал тело, шкалу не заполняет.
     */
    public static void onEvilKill(ServerPlayerEntity player, ZangetsuData data, LivingEntity killed) {
        if (data.level() < Bond.HOLLOW_LEVEL || locked(player, data) || HollowRampage.isActive(player.getUuid())) {
            return;
        }
        rollDay(player, data);
        if (data.hollowUsed <= 0) {
            return;
        }
        int before = Math.max(0, BUDGET - data.hollowUsed);
        int gain = MathHelper.clamp(Math.round(killed.getMaxHealth() * 5.0F), 60, 600);
        data.hollowUsed = Math.max(0, data.hollowUsed - (data.hollowActive ? gain / 2 : gain));
        int left = BUDGET - data.hollowUsed;
        if (data.hollowActive && crackFor(before) != crackFor(left)) {
            // Маска срастается: трещины уходят.
            Sync.hollow(player, true);
        }
        Vec3d from = killed.getPos().add(0.0, killed.getHeight() * 0.5, 0.0);
        ServerWorld world = player.getServerWorld();
        Fx.particles(world, ParticleTypes.SOUL, from, 5, 0.2, 0.03);
        Fx.particles(world, Fx.REIATSU_RED, player.getPos().add(0.0, 1.0, 0.0), 8, 0.3, 0.01);
        Fx.sound(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35F, 0.55F);
        Sync.player(player);
    }

    public static void toggle(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded || Cinematic.isLocked(player)) {
            return;
        }
        if (data.hollowActive) {
            end(player, false);
            return;
        }
        if (data.level() < Bond.HOLLOW_LEVEL) {
            player.sendMessage(Text.translatable("zangetsu.hollow.locked").formatted(Formatting.GRAY), true);
            return;
        }
        if (data.bond < 0) {
            player.sendMessage(Text.translatable("zangetsu.hollow.refused").formatted(Formatting.DARK_RED), true);
            return;
        }
        if (locked(player, data)) {
            player.sendMessage(Text.translatable("zangetsu.hollow.broken").formatted(Formatting.DARK_RED), true);
            return;
        }
        if (ticksLeft(player, data) <= 0) {
            player.sendMessage(Text.translatable("zangetsu.hollow.exhausted").formatted(Formatting.GRAY), true);
            return;
        }
        if (SwordStacks.find(player).isEmpty()) {
            return;
        }
        start(player, data);
    }

    private static void start(ServerPlayerEntity player, ZangetsuData data) {
        data.hollowActive = true;
        boolean finalForm = isFinal(data);
        if (!skipRite) {
            int ticks = finalForm ? RITE_TICKS : MASK_TICKS;
            TRANSFORMING.add(player.getUuid());
            Scheduler.later(ticks, () -> TRANSFORMING.remove(player.getUuid()));
            if (finalForm && !SwordStacks.isHolding(player)) {
                // Меч нужен в руке: вся сцена с ним.
                ItemStack sword = SwordStacks.find(player).get(0);
                ItemStack moved = sword.copy();
                sword.setCount(0);
                SwordStacks.giveToHand(player, moved);
            }
            Cinematic.play(player, finalForm ? AnimationPayload.HOLLOW_FINAL : AnimationPayload.HOLLOW, ticks);
        }
        Sync.hollow(player, true);
        player.sendMessage(Text.translatable(finalForm ? "zangetsu.hollow.on.final" : "zangetsu.hollow.on")
                .formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        applyBuffs(player);
        SwordStacks.refreshAll(player);
        Sync.player(player);
        if (!skipRite) {
            if (finalForm) {
                finalRite(player);
            } else {
                maskRite(player);
            }
        }
    }

    /** Идёт ли превращение: урон не проходит, а время формы не тратится. */
    public static boolean isTransforming(ServerPlayerEntity player) {
        return TRANSFORMING.contains(player.getUuid());
    }

    /** Звук и частицы превращения в свой миг, если игрок ещё в форме. */
    private static void at(int tick, ServerPlayerEntity player, Runnable action) {
        Scheduler.later(tick, () -> {
            if (player.isAlive() && !player.isRemoved() && ModAttachments.data(player).hollowActive) {
                action.run();
            }
        });
    }

    /** Уровень IV: хруст кости под ладонью, с которой проступает маска, и хлёст рукой в сторону, когда маска готова. */
    private static void maskRite(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.9F);
        at(14, player, () -> Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 1.0F));

        for (int tick = 18; tick <= 44; tick += 4) {
            float pitch = 0.7F + (tick - 18) * 0.015F;
            at(tick, player, () -> {
                Fx.sound(player, SoundEvents.BLOCK_BONE_BLOCK_HIT, 0.7F, pitch);
                Vec3d face = player.getEyePos().add(player.getRotationVector().multiply(0.4));
                Fx.particles(world, ParticleTypes.WHITE_ASH, face, 8, 0.16, 0.01);
            });
        }
        at(MASK_WHIP, player, () -> {
            // Хлёст рукой: след реяцу в сторону, куда ушла ладонь, и резкий свист вместо крика.
            Vec3d look = player.getRotationVector().multiply(1.0, 0.0, 1.0).normalize();
            Vec3d out = new Vec3d(look.z, 0.0, -look.x).multiply(player.getMainArm() == Arm.LEFT ? -1.0 : 1.0);
            Vec3d shoulder = player.getPos().add(0.0, 1.35, 0.0);
            for (int i = 1; i <= 7; i++) {
                Vec3d at = shoulder.add(out.multiply(0.35 * i)).add(look.multiply(0.35 - 0.05 * i));
                Fx.particles(world, Fx.REIATSU_RED, at, 2, 0.05, 0.01);
                Fx.particles(world, ParticleTypes.WHITE_ASH, at, 3, 0.08, 0.01);
            }
            Fx.particles(world, ParticleTypes.SWEEP_ATTACK, shoulder.add(out.multiply(1.5)), 1, 0.0, 0.0);
            Sync.fx(player, ClientFxPayload.RED_FLASH, 14);
            Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
            Fx.sound(player, SoundEvents.ITEM_TRIDENT_THROW.value(), 0.9F, 0.8F);
            Fx.sound(player, SoundEvents.ENTITY_BLAZE_SHOOT, 0.7F, 0.6F);
            Fx.sound(player, SoundEvents.ENTITY_WITHER_SPAWN, 0.25F, 1.6F);
            Fx.ring(world, ParticleTypes.SMOKE, player.getPos().add(0, 0.2, 0), 2.5, 30);
        });
    }

    /**
     * Уровень V: сердце бьётся всё чаще, реяцу стягивается к ногам, он кричит; молния не ранит, но вбивает его на колено и приносит огонь, плащ и цепи.
     * Дым рассеивается, он поднимается, глаза загораются, меч со звоном ложится на плечо, а выпад рвёт воздух и отбрасывает всех, кто рядом.
     */
    private static void finalRite(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        for (int tick : new int[]{0, 12, 22, 29, 34, 37}) {
            at(tick, player, () -> Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 1.0F));
        }
        for (int tick = 0; tick < RITE_STRIKE; tick += 4) {
            double radius = 3.4 - tick * 0.07;
            at(tick, player, () -> {
                Fx.ring(world, Fx.REIATSU_BLACK, player.getPos().add(0.0, 0.15, 0.0), radius, 8);
                Fx.ring(world, Fx.REIATSU_RED, player.getPos().add(0.0, 0.5, 0.0), radius * 0.7, 6);
            });
        }
        at(26, player, () -> {
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_ROAR, 1.0F, 0.75F);
            Sync.fx(player, ClientFxPayload.SHAKE, 0);
        });
        at(RITE_STRIKE - 2, player, () -> strike(world, player));
        at(RITE_STRIKE + 6, player, () -> Fx.sound(player, SoundEvents.BLOCK_CHAIN_FALL, 1.0F, 0.7F));
        for (int tick = RITE_STRIKE + 4; tick < RITE_KNEEL_END; tick += 6) {
            at(tick, player, () -> {
                Fx.particles(world, ParticleTypes.SMOKE, player.getPos().add(0.0, 0.3, 0.0), 5, 0.5, 0.01);
                Fx.sound(player, SoundEvents.BLOCK_FIRE_AMBIENT, 0.5F, 0.7F);
            });
        }
        at(RITE_KNEEL_END + 4, player, () -> Fx.sound(player, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.8F, 0.7F));
        at(RITE_IGNITE, player, () -> {
            Sync.fx(player, ClientFxPayload.RED_FLASH, 10);
            Fx.sound(player, SoundEvents.ENTITY_BLAZE_SHOOT, 0.9F, 0.6F);
            Fx.sound(player, SoundEvents.ENTITY_WITHER_SPAWN, 0.35F, 1.5F);
            Fx.ring(world, Fx.REIATSU_RED, player.getPos().add(0.0, 0.2, 0.0), 2.0, 26);
        });
        at(RITE_STAND + 4, player, () -> Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 0.7F));
        at(RITE_SHOULDER, player, () -> {
            Fx.sound(player, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.8F);
            Fx.sound(player, SoundEvents.BLOCK_CHAIN_PLACE, 1.0F, 0.8F);
            Fx.sound(player, SoundEvents.BLOCK_ANVIL_LAND, 0.25F, 1.6F);
        });
        at(RITE_LUNGE, player, () -> lunge(world, player));
    }

    /** Молния вокруг игрока: настоящая, но косметическая (ни урона, ни пожара), и ударная волна. */
    private static void strike(ServerWorld world, ServerPlayerEntity player) {
        Vec3d at = player.getPos();
        bolt(world, at);
        at(2, player, () -> bolt(world, at.add(2.6, 0.0, 1.4)));
        at(4, player, () -> bolt(world, at.add(-2.2, 0.0, -2.0)));
        Sync.fx(player, ClientFxPayload.WHITE_FLASH, 10);
        Sync.fx(player, ClientFxPayload.SHAKE, 0);
        Fx.sound(player, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 1.0F, 0.8F);
        Fx.particles(world, ParticleTypes.EXPLOSION, at.add(0.0, 0.4, 0.0), 6, 0.8, 0.0);
        Fx.particles(world, ParticleTypes.LARGE_SMOKE, at.add(0.0, 0.2, 0.0), 8, 0.7, 0.03);
        Fx.particles(world, Fx.REIATSU_BLACK, at.add(0.0, 1.0, 0.0), 12, 1.2, 0.06);
        Fx.ring(world, ParticleTypes.SMOKE, at.add(0.0, 0.1, 0.0), 2.6, 30);
        shove(world, player, 7.0, 1.3, 0.0F);
    }

    private static void bolt(ServerWorld world, Vec3d at) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.setCosmetic(true);
            bolt.refreshPositionAfterTeleport(at.x, at.y, at.z);
            world.spawnEntity(bolt);
        }
    }

    /** Выпад рвёт воздух: волна вперёд, злых мобов перед ним бьёт, всех рядом отбрасывает. */
    private static void lunge(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVector().multiply(1.0, 0.0, 1.0).normalize();
        Vec3d chest = player.getPos().add(0.0, 1.1, 0.0);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, chest.add(look.multiply(2.2)), 3, 0.5, 0.0);
        Fx.particles(world, Fx.REIATSU_RED, chest.add(look.multiply(2.0)), 18, 0.7, 0.08);
        Fx.ring(world, Fx.REIATSU_RED, player.getPos().add(0.0, 0.2, 0.0), 2.8, 30);
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.0F, 1.1F);
        Fx.sound(player, SoundEvents.BLOCK_CHAIN_FALL, 1.0F, 0.9F);
        Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        shove(world, player, 7.0, 1.0, LUNGE_DAMAGE);
    }

    /** Ударная волна: всех живых вокруг отбрасывает; злых прямо перед игроком ещё и ранит, если задан урон. */
    private static void shove(ServerWorld world, ServerPlayerEntity player, double radius, double strength, float damage) {
        Vec3d look = player.getRotationVector().multiply(1.0, 0.0, 1.0).normalize();
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius), e -> e != player && e.isAlive())) {
            Vec3d away = entity.getPos().subtract(player.getPos()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSquared() > radius * radius || away.lengthSquared() < 1.0E-4) {
                continue;
            }
            away = away.normalize();
            entity.takeKnockback(strength, -away.x, -away.z);
            if (damage > 0.0F && entity instanceof Monster && away.dotProduct(look) > 0.2) {
                entity.damage(world.getDamageSources().playerAttack(player), damage);
            }
        }
    }

    static void tick(ServerPlayerEntity player, ZangetsuData data) {
        rollDay(player, data);
        if (!data.hollowActive) {
            return;
        }
        if (isTransforming(player) || HollowRampage.isActive(player.getUuid())) {
            return;
        }
        data.hollowUsed++;
        int left = BUDGET - data.hollowUsed;
        if (left <= 0) {
            end(player, true);
            return;
        }
        int crack = crackFor(left);
        if (crack != crackFor(left + 1)) {
            onCrack(player, crack);
        }
        if (player.age % 20 == 0) {
            applyBuffs(player);
        }
        ServerWorld world = player.getServerWorld();
        // Алая и чёрная аура — у пояса и ног, чтобы не застилать вид от первого лица. В финальной форме вместо неё горит настоящий огонь.
        if (isFinal(data)) {
            return;
        }
        if (player.age % 3 == 0) {
            Fx.particles(world, Fx.REIATSU_RED, player.getPos().add(0.0, 0.6, 0.0), 2, 0.45, 0.0);
        }
        if (player.age % 5 == 0) {
            Fx.particles(world, Fx.REIATSU_BLACK, player.getPos().add(0.0, 0.15, 0.0), 2, 0.4, 0.0);
        }
    }

    /** Насколько треснула маска: 0 — цела, 1 — первая трещина (30 с), 2 — скол (15 с), 3 — почти рассыпалась (5 с). */
    public static int crackFor(int ticksLeft) {
        if (ticksLeft <= 5 * 20) {
            return 3;
        }
        if (ticksLeft <= 15 * 20) {
            return 2;
        }
        return ticksLeft <= 30 * 20 ? 1 : 0;
    }

    public static int crack(ServerPlayerEntity player) {
        return crackFor(ticksLeft(player, ModAttachments.data(player)));
    }

    /** Маска треснула сильнее: звук, осколки кости и надпись. */
    private static void onCrack(ServerPlayerEntity player, int crack) {
        Sync.hollow(player, true);
        ServerWorld world = player.getServerWorld();
        // Осколки — перед лицом, а не в самой камере.
        Vec3d face = player.getEyePos().add(player.getRotationVector().multiply(0.7));
        Fx.particles(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.getDefaultState()), face, 6 + crack * 6, 0.2, 0.08);
        Fx.sound(player, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 0.9F, 1.3F - crack * 0.15F);
        Fx.sound(player, SoundEvents.BLOCK_GLASS_BREAK, 0.3F + crack * 0.2F, 1.5F - crack * 0.2F);
        player.sendMessage(Text.translatable("zangetsu.hollow.crack." + crack).formatted(crack == 3 ? Formatting.DARK_RED : Formatting.RED), true);
    }

    private static void applyBuffs(ServerPlayerEntity player) {
        int[] levels = isFinal(ModAttachments.data(player)) ? FINAL_BUFF_LEVELS : BUFF_LEVELS;
        for (int i = 0; i < BUFFS.length; i++) {
            player.addStatusEffect(new StatusEffectInstance(BUFFS[i], 60, levels[i], false, false, true));
        }
    }

    public static void end(ServerPlayerEntity player, boolean timeUp) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.hollowActive) {
            return;
        }
        data.hollowActive = false;
        TRANSFORMING.remove(player.getUuid());
        for (RegistryEntry<StatusEffect> buff : BUFFS) {
            player.removeStatusEffect(buff);
        }
        Sync.hollow(player, false);
        ServerWorld world = player.getServerWorld();
        Vec3d face = player.getEyePos().add(player.getRotationVector().multiply(0.8));
        // Маска раскалывается — осколки разлетаются перед лицом.
        Fx.particles(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.getDefaultState()), face, 30, 0.3, 0.1);
        Fx.sound(player, SoundEvents.BLOCK_GLASS_BREAK, 1.0F, 0.6F);
        Fx.sound(player, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 1.0F, 0.8F);
        if (timeUp) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0));
            player.sendMessage(Text.translatable("zangetsu.hollow.time_up").formatted(Formatting.GRAY), true);
        } else {
            player.sendMessage(Text.translatable("zangetsu.hollow.off").formatted(Formatting.GRAY), true);
        }
        SwordStacks.refreshAll(player);
        Sync.player(player);
    }

    /**
     * Правая кнопка с мечом в форме Пустого — Гецуга Теншо: замах за плечо, на клинке собирается чёрно-алая
     * реяцу — и на взмахе волна срывается с меча.
     */
    public static boolean tryGetsuga(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.hollowActive || Cinematic.isLocked(player) || CHARGING.contains(player.getUuid())) {
            return false;
        }
        CHARGING.add(player.getUuid());
        Sync.animation(player, AnimationPayload.GETSUGA, GETSUGA_ANIMATION);
        ServerWorld world = player.getServerWorld();
        Vec3d blade = player.getPos().add(0.0, 2.3, 0.0);
        Fx.particles(world, Fx.REIATSU_BLACK, blade, 14, 0.35, 0.02);
        Fx.particles(world, Fx.REIATSU_RED, blade, 14, 0.4, 0.02);
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.7F, 1.5F);
        Scheduler.later(GETSUGA_WINDUP, () -> {
            CHARGING.remove(player.getUuid());
            if (player.isAlive() && !player.isRemoved() && ModAttachments.data(player).hollowActive) {
                GetsugaEntity.fire(player);
            }
        });
        return true;
    }
}
