package com.zangetsu.logic;

import com.zangetsu.network.HollowPayload;
import com.zangetsu.network.LimbPayload;
import com.zangetsu.network.Net;
import com.zangetsu.registry.ModAttachments;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Все серверные события мода и ежетиковая забота о каждом игроке. Подписывает их загрузчик:
 * Fabric — на события Fabric API, NeoForge — на свои; здесь только то, что делать.
 */
public final class ServerEvents {
    private ServerEvents() {
    }

    /** Конец тика сервера. */
    public static void serverTick(MinecraftServer server) {
        Scheduler.tick();
        for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
            tickPlayer(player);
        }
    }

    public static void serverStopped() {
        Scheduler.clear();
        Reaction.clear();
        Calling.clear();
        SwordBreak.clear();
        HollowRampage.clear();
        Trial.clear();
    }

    /** Удар, который убил бы врага, вместо этого начинает проверку реакции. false — сущность не умирает. */
    public static boolean allowDeath(LivingEntity entity, DamageSource source) {
        return !Reaction.interceptDeath(entity, source) && !HollowRampage.interceptDeath(entity, source) && !SwordBreak.interceptDeath(entity, source);
    }

    /** Новый зритель сразу видит отрубленные руки и маску Пустого. */
    public static void startTracking(Entity entity, ServerPlayerEntity viewer) {
        if (entity instanceof LivingEntity living && ModAttachments.limbs(living) != 0) {
            Net.send(viewer, new LimbPayload(living.getId(), ModAttachments.limbs(living)));
        }
        if (entity instanceof ServerPlayerEntity other && ModAttachments.data(other).hollowActive) {
            Net.send(viewer, new HollowPayload(other.getId(), true, HollowForm.crack(other), HollowForm.isFinal(ModAttachments.data(other))));
        }
    }

    public static void join(ServerPlayerEntity player) {
        Sync.player(player);
        if (ModAttachments.data(player).hollowActive) {
            Sync.hollow(player, true);
        }
    }

    /** После портала клиент заводит игрока заново — напоминаем ему про маску и связь. */
    public static void changedWorld(ServerPlayerEntity player) {
        join(player);
    }

    public static void disconnect(ServerPlayerEntity player) {
        Calling.disconnect(player);
        SwordBreak.disconnect(player);
        HollowRampage.disconnect(player);
        Trial.disconnect(player);
        Awakening.cancel(player);
        Sync.forget(player);
        Eye.forget(player);
        Abilities.forget(player);
        Cinematic.forget(player);
        Reaction.forget(player);
    }

    /** Меч не выпадает при смерти: он остаётся у старого тела и переезжает к новому. alive — это не смерть, а выход из Энда. */
    public static void copyFrom(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        boolean keepInventory = oldPlayer.getServerWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
        if (alive || keepInventory) {
            return;
        }
        for (ItemStack sword : SwordStacks.find(oldPlayer)) {
            newPlayer.getInventory().insertStack(sword.copy());
        }
    }

    public static void afterRespawn(ServerPlayerEntity newPlayer) {
        ZangetsuData data = ModAttachments.data(newPlayer);
        data.hollowActive = false;
        if (data.bonded) {
            Awakening.applyHealthBonus(newPlayer);
            newPlayer.setHealth(newPlayer.getMaxHealth());
        }
        Sync.player(newPlayer);
        Sync.hollow(newPlayer, false);
    }

    /** Кровать во сне не для сна: лечь в неё — значит отказаться от меча. PASS — пусть игра решает сама. */
    public static ActionResult useBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.getRegistryKey() != Calling.WORLD || !world.getBlockState(hit.getBlockPos()).isIn(BlockTags.BEDS)) {
            return ActionResult.PASS;
        }
        if (player instanceof ServerPlayerEntity sleeper && hand == Hand.MAIN_HAND) {
            Calling.refuse(sleeper);
        }
        return ActionResult.SUCCESS;
    }

    /** Меч нельзя повесить в рамку, отдать стойке или тихоне. */
    public static ActionResult useEntity(PlayerEntity player, World world, Hand hand, Entity entity) {
        if (SwordStacks.isSword(player.getStackInHand(hand))
                && (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity || entity instanceof AllayEntity)) {
            if (!world.isClient) {
                player.sendMessage(Text.translatable("zangetsu.no_drop").formatted(Formatting.DARK_RED), true);
            }
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    /** Можно ли сущности получить этот урон. false — урона не будет вовсе. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        // Враг на проверке реакции замер: уже начатый удар меча не должен добить его и сорвать круг.
        if (Reaction.isTarget(entity) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }
        // Во сне ничто не ранит.
        if ((Awakening.protects(player) || Calling.inCalling(player) || SwordBreak.protects(player) || Trial.isActive(player.getUuid())) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        // Пока идёт сцена превращения, игрок не управляет собой: ранить его было бы нечестно. То же и в сцене за спиной врага.
        if ((HollowForm.isTransforming(player) || Execution.inBehindScene(player) || HollowRampage.protects(player)) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        ZangetsuData data = ModAttachments.data(player);
        if (data.bonded && (data.level() >= 2 || data.hollowActive) && Eye.awake(player)) {
            return !Abilities.tryParryMelee(player, source, data);
        }
        return true;
    }

    /** Игрок убил сущность (смерть уже не отменят). */
    public static void onKill(ServerPlayerEntity player, LivingEntity killed) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded) {
            return;
        }
        if (Bond.isEvil(killed)) {
            Bond.change(player, Bond.rewardFor(killed));
            HollowForm.onEvilKill(player, data, killed);
        } else if (Bond.isGood(killed)) {
            Bond.change(player, -Bond.penaltyFor(killed));
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        Awakening.tick(player);
        Calling.tick(player, data);
        SwordBreak.tick(player);
        HollowRampage.tick(player);
        Trial.tick(player, data);
        if (!data.bonded) {
            List<ItemStack> swords = SwordStacks.find(player);
            if (!swords.isEmpty()) {
                // Меч попал в руки другим путём (командой или из творческого) — значит, выбрал этого игрока.
                bondByItem(player, data, swords);
            } else if (Calling.shouldBegin(player, data)) {
                Calling.begin(player);
            }
            Sync.playerIfChanged(player);
            return;
        }

        Dream.tick(player, data);
        HollowForm.tick(player, data);
        Reaction.tick(player);
        if (player.age % 20 == 0) {
            checkSwords(player, data);
        }
        if (Dream.shouldBegin(player, data)) {
            Dream.begin(player);
        }
        if (player.age % 4 == 0) {
            Eye.update(player, data);
        }
        if (Eye.awake(player) && !player.isSpectator()) {
            Abilities.parryProjectiles(player);
            if (data.level() >= 3 || data.hollowActive) {
                Abilities.autoAttack(player);
            }
        }
        Sync.playerIfChanged(player);
    }

    private static void bondByItem(ServerPlayerEntity player, ZangetsuData data, List<ItemStack> swords) {
        // Кто отказался, тому меч уже не служит. Выдать его заново может только творческий режим или команда.
        if (data.refused && !player.isCreative()) {
            for (ItemStack sword : swords) {
                sword.setCount(0);
            }
            player.sendMessage(Text.translatable("zangetsu.refused").formatted(Formatting.DARK_RED), true);
            return;
        }
        for (ItemStack sword : swords) {
            if (!SwordStacks.ownedBy(sword, player)) {
                sword.setCount(0);
                player.sendMessage(Text.translatable("zangetsu.not_yours").formatted(Formatting.DARK_RED), true);
                return;
            }
        }
        data.bonded = true;
        data.lost = false;
        data.broken = false;
        data.refused = false;
        Awakening.applyHealthBonus(player);
        SwordStacks.refreshAll(player);
        Sync.player(player);
    }

    /** Раз в секунду: меч на месте, он один, он свой. Нет меча — значит, потерян, и вернётся во сне. */
    private static void checkSwords(ServerPlayerEntity player, ZangetsuData data) {
        Awakening.applyHealthBonus(player);
        List<ItemStack> swords = SwordStacks.find(player);
        boolean kept = false;
        for (ItemStack sword : swords) {
            if (!SwordStacks.ownedBy(sword, player) || kept) {
                sword.setCount(0);
                continue;
            }
            kept = true;
            SwordStacks.apply(sword, player);
        }
        if (!kept && !data.lost && data.dream == null && !Awakening.isActive(player.getUuid()) && !SwordBreak.isActive(player.getUuid())) {
            data.lost = true;
            player.sendMessage(Text.translatable("zangetsu.lost").formatted(Formatting.GRAY, Formatting.ITALIC), false);
        } else if (kept && data.lost) {
            data.lost = false;
            data.broken = false;
        }
    }
}
