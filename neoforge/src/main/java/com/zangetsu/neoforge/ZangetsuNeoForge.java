package com.zangetsu.neoforge;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.command.ZangetsuCommand;
import com.zangetsu.logic.ServerEvents;
import com.zangetsu.network.ActionPayload;
import com.zangetsu.network.ClientBridge;
import com.zangetsu.network.ZangetsuNetworking;
import com.zangetsu.platform.Platform;
import com.zangetsu.registry.ModItems;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemGroups;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;

/** NeoForge: подключить общий мод к событиям NeoForge. */
@Mod(ZangetsuMod.MOD_ID)
public final class ZangetsuNeoForge {
    /** Смерть уже перехвачена до тотема: само событие смерти тогда просто отменяем. */
    private static final Set<LivingEntity> INTERCEPTED = Collections.newSetFromMap(new WeakHashMap<>());

    public ZangetsuNeoForge(IEventBus modBus) {
        Platform.set(new NeoForgePlatform());
        NeoForgePlatform.ATTACHMENTS.register(modBus);
        // Реестры игры открыты только на время RegisterEvent: предмет, сущности и прочее кладём туда.
        modBus.addListener(RegisterEvent.class, event -> ZangetsuMod.registerContent());
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey() == ItemGroups.COMBAT) {
                event.add(ModItems.ZANGETSU);
            }
        });
        modBus.addListener(RegisterPayloadHandlersEvent.class, ZangetsuNeoForge::payloads);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterCommandsEvent.class, event -> ZangetsuCommand.register(event.getDispatcher()));
        bus.addListener(ServerTickEvent.Post.class, event -> ServerEvents.serverTick(event.getServer()));
        bus.addListener(ServerStoppedEvent.class, event -> ServerEvents.serverStopped());
        bus.addListener(LivingIncomingDamageEvent.class, event -> {
            if (!event.getEntity().getWorld().isClient && !ServerEvents.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
                event.setCanceled(true);
            }
        });
        // На Fabric проверка смерти идёт до тотема бессмертия; здесь тотем раньше, поэтому смотрим и в нём.
        bus.addListener(LivingUseTotemEvent.class, event -> {
            if (!event.getEntity().getWorld().isClient && !ServerEvents.allowDeath(event.getEntity(), event.getSource())) {
                INTERCEPTED.add(event.getEntity());
                event.setCanceled(true);
            }
        });
        bus.addListener(EventPriority.HIGHEST, false, LivingDeathEvent.class, event -> {
            if (event.getEntity().getWorld().isClient) {
                return;
            }
            if (INTERCEPTED.remove(event.getEntity()) || !ServerEvents.allowDeath(event.getEntity(), event.getSource())) {
                event.setCanceled(true);
            }
        });
        bus.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, event -> {
            if (!event.getEntity().getWorld().isClient && event.getSource().getAttacker() instanceof ServerPlayerEntity player) {
                ServerEvents.onKill(player, event.getEntity());
            }
        });
        bus.addListener(PlayerEvent.StartTracking.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity viewer) {
                ServerEvents.startTracking(event.getTarget(), viewer);
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                ServerEvents.join(player);
            }
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                ServerEvents.changedWorld(player);
            }
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                ServerEvents.disconnect(player);
            }
        });
        bus.addListener(PlayerEvent.Clone.class, event -> {
            if (event.getOriginal() instanceof ServerPlayerEntity oldPlayer && event.getEntity() instanceof ServerPlayerEntity newPlayer) {
                ServerEvents.copyFrom(oldPlayer, newPlayer, !event.isWasDeath());
            }
        });
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayerEntity player) {
                ServerEvents.afterRespawn(player);
            }
        });
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
            ActionResult result = ServerEvents.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != ActionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        bus.addListener(PlayerInteractEvent.EntityInteract.class, event -> {
            ActionResult result = ServerEvents.useEntity(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget());
            if (result != ActionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        bus.addListener(PlayerInteractEvent.EntityInteractSpecific.class, event -> {
            ActionResult result = ServerEvents.useEntity(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget());
            if (result != ActionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (ZangetsuNetworking.Type<?> type : ZangetsuNetworking.TO_CLIENT) {
            toClient(registrar, type);
        }
        registrar.playToServer(ActionPayload.ID, ActionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayerEntity player) {
                ZangetsuNetworking.receive(payload, player);
            }
        });
    }

    private static <T extends CustomPayload> void toClient(PayloadRegistrar registrar, ZangetsuNetworking.Type<T> type) {
        registrar.playToClient(type.id(), type.codec(), (payload, context) -> ClientBridge.receive(payload));
    }
}
