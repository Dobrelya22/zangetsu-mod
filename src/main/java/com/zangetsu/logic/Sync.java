package com.zangetsu.logic;

import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.network.HollowPayload;
import com.zangetsu.network.LimbPayload;
import com.zangetsu.network.PlayerStatePayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Отправка данных мода клиентам. */
public final class Sync {
    private static final Map<UUID, PlayerStatePayload> LAST_STATE = new HashMap<>();

    private Sync() {
    }

    public static PlayerStatePayload stateOf(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        return new PlayerStatePayload(data.bonded, data.bond, data.level(), data.hollowActive,
                HollowForm.ticksLeft(player, data), data.lost);
    }

    /** Состояние связи — самому игроку. */
    public static void player(ServerPlayerEntity player) {
        PlayerStatePayload state = stateOf(player);
        LAST_STATE.put(player.getUuid(), state);
        ServerPlayNetworking.send(player, state);
    }

    /** То же, но только если что-то поменялось (вызывается каждый тик). */
    public static void playerIfChanged(ServerPlayerEntity player) {
        PlayerStatePayload state = stateOf(player);
        PlayerStatePayload last = LAST_STATE.get(player.getUuid());
        // Остаток времени Пустого тикает каждый тик — шлём его раз в секунду, остальное сразу.
        boolean changed = last == null || last.bonded() != state.bonded() || last.bond() != state.bond()
                || last.hollowActive() != state.hollowActive() || last.lost() != state.lost()
                || Math.abs(last.hollowLeft() - state.hollowLeft()) >= 20;
        if (changed) {
            player(player);
        }
    }

    public static void forget(ServerPlayerEntity player) {
        LAST_STATE.remove(player.getUuid());
    }

    public static void limbs(LivingEntity entity) {
        LimbPayload payload = new LimbPayload(entity.getId(), ModAttachments.limbs(entity));
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    public static void animation(ServerPlayerEntity player, int animation, int duration) {
        animation(player, animation, duration, 0);
    }

    public static void animation(ServerPlayerEntity player, int animation, int duration, int param) {
        toSelfAndTrackers(player, new AnimationPayload(player.getId(), animation, duration, param));
    }

    public static void hollow(ServerPlayerEntity player, boolean active) {
        toSelfAndTrackers(player, new HollowPayload(player.getId(), active, active ? HollowForm.crack(player) : 0));
    }

    public static void fx(ServerPlayerEntity player, int effect, int duration) {
        ServerPlayNetworking.send(player, new ClientFxPayload(effect, duration));
    }

    private static void toSelfAndTrackers(ServerPlayerEntity player, CustomPayload payload) {
        ServerPlayNetworking.send(player, payload);
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (viewer != player) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }
}
