package com.zangetsu.logic;

import com.zangetsu.registry.ModAttachments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Дружба с мечом и уровни. Глаз любит, когда рубят злых мобов, и не прощает убийства добрых.
 * <ul>
 *   <li>I «Пробуждение» — в темноте глаз открывается, меч рассекает летящие стрелы;</li>
 *   <li>II «Доверие» — меч парирует удары и отсекает конечности, открывается казнь;</li>
 *   <li>III «Единство» — меч сам бьёт ближайших злых мобов;</li>
 *   <li>IV «Пустой» — две минуты в день формы Пустого и Гецуга Теншо.</li>
 * </ul>
 */
public final class Bond {
    /** Сколько дружбы нужно для уровней II, III и IV. */
    public static final int[] THRESHOLDS = {0, 50, 150, 350};
    public static final int MAX_LEVEL = 4;
    public static final int EXECUTION_BONUS = 4;

    private Bond() {
    }

    public static int levelFor(int bond) {
        int level = 1;
        for (int i = 1; i < THRESHOLDS.length; i++) {
            if (bond >= THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    /** Сколько дружбы нужно до следующего уровня (или -1 на последнем). */
    public static int nextThreshold(int level) {
        return level >= MAX_LEVEL ? -1 : THRESHOLDS[level];
    }

    public static String levelKey(int level) {
        return "zangetsu.level." + Math.max(1, Math.min(MAX_LEVEL, level));
    }

    /** Злой моб — его убийство радует меч. */
    public static boolean isEvil(LivingEntity entity) {
        return entity instanceof Monster || entity instanceof EnderDragonEntity || entity instanceof SlimeEntity;
    }

    /** Добрый моб — его смерть огорчает меч. */
    public static boolean isGood(LivingEntity entity) {
        return !isEvil(entity) && !(entity instanceof PlayerEntity)
                && (entity instanceof PassiveEntity || entity instanceof GolemEntity || entity instanceof AmbientEntity
                || entity instanceof WaterCreatureEntity || entity instanceof AllayEntity);
    }

    /** Сколько дружбы приносит победа над злым мобом: чем он крепче, тем больше. */
    public static int rewardFor(LivingEntity entity) {
        if (entity instanceof WitherEntity || entity instanceof EnderDragonEntity) {
            return 60;
        }
        return Math.max(2, Math.round(entity.getMaxHealth() / 6.0F));
    }

    /** Сколько дружбы отнимает смерть доброго моба: жителей и големов меч не прощает сильнее. */
    public static int penaltyFor(LivingEntity entity) {
        if (entity instanceof MerchantEntity || entity instanceof GolemEntity || entity instanceof AllayEntity) {
            return 40;
        }
        return 15;
    }

    /** Изменить дружбу и сообщить игроку; на новом уровне — заголовок и звук. */
    public static void change(ServerPlayerEntity player, int delta) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded || delta == 0) {
            return;
        }
        int before = data.level();
        boolean wasAngry = data.bond < 0;
        data.bond += delta;
        int after = data.level();
        Text amount = Text.literal((delta > 0 ? "+" : "") + delta).formatted(delta > 0 ? Formatting.GOLD : Formatting.RED);
        player.sendMessage(Text.translatable(delta > 0 ? "zangetsu.bond.up" : "zangetsu.bond.down", amount), true);
        if (after > before) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 50, 20));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("zangetsu.level.up.subtitle." + after)
                    .formatted(Formatting.GRAY)));
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("zangetsu.level.up",
                    Text.translatable(levelKey(after))).formatted(Formatting.GOLD)));
            Fx.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        } else if (after < before) {
            player.sendMessage(Text.translatable("zangetsu.level.down", Text.translatable(levelKey(after)))
                    .formatted(Formatting.DARK_RED), false);
        }
        if (!wasAngry && data.bond < 0) {
            player.sendMessage(Text.translatable("zangetsu.bond.angry").formatted(Formatting.DARK_RED), false);
            Fx.sound(player, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.5F, 1.4F);
        } else if (wasAngry && data.bond >= 0) {
            player.sendMessage(Text.translatable("zangetsu.bond.forgiven").formatted(Formatting.GOLD), false);
        }
        SwordStacks.refreshAll(player);
        Sync.player(player);
    }
}
