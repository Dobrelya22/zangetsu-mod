package com.zangetsu.network;

import com.zangetsu.platform.Platform;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;

/** Отправка пакетов с сервера. Сами пакеты мода регистрирует загрузчик (см. {@link ZangetsuNetworking}). */
public final class Net {
    private Net() {
    }

    public static void send(ServerPlayerEntity player, CustomPayload payload) {
        Platform.get().send(player, payload);
    }

    /** Всем, кто видит эту сущность, кроме неё самой. */
    public static void toTrackers(Entity entity, CustomPayload payload) {
        Platform.get().sendToTrackers(entity, payload);
    }

    /** Самому игроку и всем, кто его видит. */
    public static void toSelfAndTrackers(ServerPlayerEntity player, CustomPayload payload) {
        send(player, payload);
        toTrackers(player, payload);
    }

    /** Обычный пакет игры (титры и прочее). */
    public static void packet(ServerPlayerEntity player, Packet<?> packet) {
        Platform.get().sendPacket(player, packet);
    }
}
