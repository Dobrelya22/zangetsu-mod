package com.zangetsu.logic;

import com.zangetsu.entity.GetsugaEntity;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModAttachments;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * Форма Пустого (уровень IV): рогатая маска, горящий глаз меча, серповидная луна в небе
 * и Гецуга Теншо по правой кнопке. Две минуты в игровые сутки; время можно тратить частями.
 */
public final class HollowForm {
    public static final int BUDGET = 2 * 60 * 20;
    public static final int TRANSFORM_TICKS = 30;
    public static final int GETSUGA_COOLDOWN = 24;

    @SuppressWarnings("unchecked")
    private static final RegistryEntry<StatusEffect>[] BUFFS = new RegistryEntry[]{
            StatusEffects.STRENGTH, StatusEffects.SPEED, StatusEffects.RESISTANCE, StatusEffects.REGENERATION, StatusEffects.JUMP_BOOST
    };
    private static final int[] BUFF_LEVELS = {1, 1, 1, 0, 0};

    private HollowForm() {
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

    public static int ticksLeft(ServerPlayerEntity player, ZangetsuData data) {
        rollDay(player, data);
        return Math.max(0, BUDGET - data.hollowUsed);
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
        if (data.level() < Bond.MAX_LEVEL) {
            player.sendMessage(Text.translatable("zangetsu.hollow.locked").formatted(Formatting.GRAY), true);
            return;
        }
        if (data.bond < 0) {
            player.sendMessage(Text.translatable("zangetsu.hollow.refused").formatted(Formatting.DARK_RED), true);
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
        ServerWorld world = player.getServerWorld();
        data.hollowActive = true;
        Cinematic.play(player, AnimationPayload.HOLLOW, TRANSFORM_TICKS);
        Sync.hollow(player, true);
        Sync.fx(player, ClientFxPayload.RED_FLASH, TRANSFORM_TICKS);
        Vec3d chest = player.getPos().add(0.0, 1.2, 0.0);
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_ROAR, 1.0F, 0.9F);
        Fx.sound(player, SoundEvents.ENTITY_WITHER_SPAWN, 0.5F, 1.4F);
        Fx.particles(world, Fx.REIATSU_BLACK, chest, 30, 0.7, 0.05);
        Fx.particles(world, Fx.REIATSU_RED, chest, 30, 0.9, 0.05);
        Fx.particles(world, ParticleTypes.SONIC_BOOM, chest, 1, 0.0, 0.0);
        Fx.ring(world, ParticleTypes.LARGE_SMOKE, player.getPos().add(0, 0.2, 0), 2.5, 30);
        player.sendMessage(Text.translatable("zangetsu.hollow.on").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        applyBuffs(player);
        SwordStacks.refreshAll(player);
        Sync.player(player);
    }

    static void tick(ServerPlayerEntity player, ZangetsuData data) {
        rollDay(player, data);
        if (!data.hollowActive) {
            return;
        }
        data.hollowUsed++;
        int left = BUDGET - data.hollowUsed;
        if (left <= 0) {
            end(player, true);
            return;
        }
        if (left == 200) {
            player.sendMessage(Text.translatable("zangetsu.hollow.ending").formatted(Formatting.RED), true);
        }
        if (player.age % 20 == 0) {
            applyBuffs(player);
        }
        ServerWorld world = player.getServerWorld();
        // Алая и чёрная аура — у пояса и ног, чтобы не застилать вид от первого лица.
        if (player.age % 3 == 0) {
            Fx.particles(world, Fx.REIATSU_RED, player.getPos().add(0.0, 0.6, 0.0), 2, 0.45, 0.0);
        }
        if (player.age % 5 == 0) {
            Fx.particles(world, Fx.REIATSU_BLACK, player.getPos().add(0.0, 0.15, 0.0), 2, 0.4, 0.0);
        }
    }

    private static void applyBuffs(ServerPlayerEntity player) {
        for (int i = 0; i < BUFFS.length; i++) {
            player.addStatusEffect(new StatusEffectInstance(BUFFS[i], 60, BUFF_LEVELS[i], false, false, true));
        }
    }

    public static void end(ServerPlayerEntity player, boolean timeUp) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.hollowActive) {
            return;
        }
        data.hollowActive = false;
        for (RegistryEntry<StatusEffect> buff : BUFFS) {
            player.removeStatusEffect(buff);
        }
        Sync.hollow(player, false);
        ServerWorld world = player.getServerWorld();
        Vec3d head = player.getEyePos();
        // Маска раскалывается.
        Fx.particles(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.getDefaultState()), head, 50, 0.3, 0.1);
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

    /** Правая кнопка с мечом в форме Пустого — Гецуга Теншо. */
    public static boolean tryGetsuga(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.hollowActive || Cinematic.isLocked(player)) {
            return false;
        }
        GetsugaEntity.fire(player);
        return true;
    }
}
