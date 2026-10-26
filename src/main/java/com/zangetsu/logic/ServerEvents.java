package com.zangetsu.logic;

import com.zangetsu.network.HollowPayload;
import com.zangetsu.network.LimbPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameRules;

/** Все серверные события мода и ежетиковая забота о каждом игроке. */
public final class ServerEvents {
    private ServerEvents() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick();
            for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
                tickPlayer(player);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Scheduler.clear());

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ServerEvents::allowDamage);
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (killer instanceof ServerPlayerEntity player) {
                onKill(player, killed);
            }
        });

        // Новый зритель сразу видит отрубленные руки и маску Пустого.
        EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
            if (entity instanceof LivingEntity living && ModAttachments.limbs(living) != 0) {
                ServerPlayNetworking.send(viewer, new LimbPayload(living.getId(), ModAttachments.limbs(living)));
            }
            if (entity instanceof ServerPlayerEntity other && ModAttachments.data(other).hollowActive) {
                ServerPlayNetworking.send(viewer, new HollowPayload(other.getId(), true));
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            Sync.player(player);
            if (ModAttachments.data(player).hollowActive) {
                Sync.hollow(player, true);
            }
        });
        // После портала клиент заводит игрока заново — напоминаем ему про маску и связь.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            Sync.player(player);
            if (ModAttachments.data(player).hollowActive) {
                Sync.hollow(player, true);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            Awakening.cancel(player);
            Sync.forget(player);
            Eye.forget(player);
            Abilities.forget(player);
            Cinematic.forget(player);
        });

        // Меч не выпадает при смерти: он остаётся у старого тела и переезжает к новому.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            boolean keepInventory = oldPlayer.getServerWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
            if (alive || keepInventory) {
                return;
            }
            for (ItemStack sword : SwordStacks.find(oldPlayer)) {
                newPlayer.getInventory().insertStack(sword.copy());
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            ZangetsuData data = ModAttachments.data(newPlayer);
            data.hollowActive = false;
            if (data.bonded) {
                Awakening.applyHealthBonus(newPlayer);
                newPlayer.setHealth(newPlayer.getMaxHealth());
            }
            Sync.player(newPlayer);
            Sync.hollow(newPlayer, false);
        });

        // Меч нельзя повесить в рамку, отдать стойке или тихоне.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (SwordStacks.isSword(player.getStackInHand(hand))
                    && (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity || entity instanceof AllayEntity)) {
                if (!world.isClient) {
                    player.sendMessage(Text.translatable("zangetsu.no_drop").formatted(Formatting.DARK_RED), true);
                }
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return true;
        }
        if (Awakening.protects(player) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        ZangetsuData data = ModAttachments.data(player);
        if (data.bonded && (data.level() >= 2 || data.hollowActive) && Eye.awake(player)) {
            return !Abilities.tryParryMelee(player, source, data);
        }
        return true;
    }

    private static void onKill(ServerPlayerEntity player, LivingEntity killed) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded) {
            return;
        }
        if (Bond.isEvil(killed)) {
            Bond.change(player, Bond.rewardFor(killed));
        } else if (Bond.isGood(killed)) {
            Bond.change(player, -Bond.penaltyFor(killed));
        }
    }

    private static void tickPlayer(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        Awakening.tick(player);
        if (!data.bonded) {
            List<ItemStack> swords = SwordStacks.find(player);
            if (!swords.isEmpty()) {
                // Меч попал в руки другим путём (командой или из творческого) — значит, выбрал этого игрока.
                bondByItem(player, data, swords);
            } else if (Awakening.shouldTrigger(player, data)) {
                Awakening.start(player);
            }
            Sync.playerIfChanged(player);
            return;
        }

        Dream.tick(player, data);
        HollowForm.tick(player, data);
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
        for (ItemStack sword : swords) {
            if (!SwordStacks.ownedBy(sword, player)) {
                sword.setCount(0);
                player.sendMessage(Text.translatable("zangetsu.not_yours").formatted(Formatting.DARK_RED), true);
                return;
            }
        }
        data.bonded = true;
        data.lost = false;
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
        if (!kept && !data.lost && data.dream == null && !Awakening.isActive(player.getUuid())) {
            data.lost = true;
            player.sendMessage(Text.translatable("zangetsu.lost").formatted(Formatting.GRAY, Formatting.ITALIC), false);
        } else if (kept && data.lost) {
            data.lost = false;
        }
    }
}
