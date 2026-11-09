package com.zangetsu.fabric;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.command.ZangetsuCommand;
import com.zangetsu.logic.ServerEvents;
import com.zangetsu.network.ActionPayload;
import com.zangetsu.network.ZangetsuNetworking;
import com.zangetsu.platform.Platform;
import com.zangetsu.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemGroups;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric: подключить общий мод к событиям Fabric API. */
public class ZangetsuFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.set(new FabricPlatform());
        ZangetsuMod.registerContent();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(ModItems.ZANGETSU));

        for (ZangetsuNetworking.Type<?> type : ZangetsuNetworking.TO_CLIENT) {
            registerToClient(type);
        }
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        // Обработчик вызывается в потоке сервера, так что с миром можно работать напрямую.
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> ZangetsuNetworking.receive(payload, context.player()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ZangetsuCommand.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(ServerEvents::serverTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerEvents.serverStopped());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ServerEvents::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> ServerEvents.allowDeath(entity, source));
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (killer instanceof ServerPlayerEntity player) {
                ServerEvents.onKill(player, killed);
            }
        });
        EntityTrackingEvents.START_TRACKING.register(ServerEvents::startTracking);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerEvents.join(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> ServerEvents.changedWorld(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerEvents.disconnect(handler.player));
        ServerPlayerEvents.COPY_FROM.register(ServerEvents::copyFrom);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ServerEvents.afterRespawn(newPlayer));
        UseBlockCallback.EVENT.register(ServerEvents::useBlock);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> ServerEvents.useEntity(player, world, hand, entity));
    }

    private static <T extends CustomPayload> void registerToClient(ZangetsuNetworking.Type<T> type) {
        @SuppressWarnings("unchecked")
        PacketCodec<RegistryByteBuf, T> codec = (PacketCodec<RegistryByteBuf, T>) (PacketCodec<?, T>) type.codec();
        PayloadTypeRegistry.playS2C().register(type.id(), codec);
    }
}
