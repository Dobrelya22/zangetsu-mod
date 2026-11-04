package com.zangetsu.network;

import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Reaction;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class ZangetsuNetworking {
    private ZangetsuNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(PlayerStatePayload.ID, PlayerStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LimbPayload.ID, LimbPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnimationPayload.ID, AnimationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HollowPayload.ID, HollowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClientFxPayload.ID, ClientFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReactionPayload.ID, ReactionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);

        // Обработчик вызывается в потоке сервера, так что с миром можно работать напрямую.
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> {
            switch (payload.action()) {
                case ActionPayload.REACTION -> Reaction.click(context.player(), payload.target());
                case ActionPayload.HOLLOW -> HollowForm.toggle(context.player());
                default -> {
                }
            }
        });
    }
}
