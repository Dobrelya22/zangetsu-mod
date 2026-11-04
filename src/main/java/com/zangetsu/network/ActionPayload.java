package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Клиент → сервер: действие игрока. */
public record ActionPayload(int action, int target) implements CustomPayload {
    /** Клик по кругу проверки реакции: {@code target} — положение стрелки, доля круга × 10000. */
    public static final int REACTION = 1;
    /** Принять или сбросить форму Пустого. */
    public static final int HOLLOW = 2;

    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("action"));
    public static final PacketCodec<ByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ActionPayload::action,
            PacketCodecs.VAR_INT, ActionPayload::target,
            ActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
