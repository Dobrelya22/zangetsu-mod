package com.zangetsu.client;

import java.util.function.Consumer;
import net.minecraft.network.packet.CustomPayload;

/** Пакет с клиента на сервер. Как отправлять, назначает клиент загрузчика: Fabric — ClientPlayNetworking, NeoForge — PacketDistributor. */
public final class ClientNet {
    private static Consumer<CustomPayload> sender = payload -> {
    };

    private ClientNet() {
    }

    public static void set(Consumer<CustomPayload> send) {
        sender = send;
    }

    public static void send(CustomPayload payload) {
        sender.accept(payload);
    }
}
