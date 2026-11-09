package com.zangetsu.network;

import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Reaction;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Все пакеты мода. Загрузчик регистрирует их по этим спискам и зовёт обработчики отсюда. */
public final class ZangetsuNetworking {
    /** С сервера на клиент: обработчик у клиента — {@link com.zangetsu.client.ZangetsuClient#receive}. */
    public static final List<Type<?>> TO_CLIENT = List.of(
            new Type<>(PlayerStatePayload.ID, PlayerStatePayload.CODEC),
            new Type<>(LimbPayload.ID, LimbPayload.CODEC),
            new Type<>(AnimationPayload.ID, AnimationPayload.CODEC),
            new Type<>(HollowPayload.ID, HollowPayload.CODEC),
            new Type<>(ClientFxPayload.ID, ClientFxPayload.CODEC),
            new Type<>(ReactionPayload.ID, ReactionPayload.CODEC),
            new Type<>(CallingPayload.ID, CallingPayload.CODEC));

    private ZangetsuNetworking() {
    }

    /** С клиента на сервер — только {@link ActionPayload}. Вызывается в потоке сервера. */
    public static void receive(ActionPayload payload, ServerPlayerEntity player) {
        switch (payload.action()) {
            case ActionPayload.REACTION -> Reaction.click(player, payload.target());
            case ActionPayload.HOLLOW -> HollowForm.toggle(player);
            default -> {
            }
        }
    }

    public record Type<T extends CustomPayload>(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
    }
}
