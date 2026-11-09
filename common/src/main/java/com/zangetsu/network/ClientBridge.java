package com.zangetsu.network;

import java.util.function.Consumer;
import net.minecraft.network.packet.CustomPayload;

/**
 * Куда отдать пакет с сервера. Клиент мода назначает сюда свой приёмник; на выделенном сервере здесь пусто,
 * и классы клиента не загружаются вовсе.
 */
public final class ClientBridge {
    private static Consumer<CustomPayload> receiver = payload -> {
    };

    private ClientBridge() {
    }

    public static void set(Consumer<CustomPayload> handler) {
        receiver = handler;
    }

    public static void receive(CustomPayload payload) {
        receiver.accept(payload);
    }
}
