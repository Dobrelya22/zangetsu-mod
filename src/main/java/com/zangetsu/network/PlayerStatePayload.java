package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер → клиент: связь с мечом для надписей на экране. */
public record PlayerStatePayload(boolean bonded, int bond, int level, boolean hollowActive, int hollowLeft, boolean lost)
        implements CustomPayload {
    public static final CustomPayload.Id<PlayerStatePayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("player_state"));
    public static final PacketCodec<ByteBuf, PlayerStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, PlayerStatePayload::bonded,
            PacketCodecs.VAR_INT, PlayerStatePayload::bond,
            PacketCodecs.VAR_INT, PlayerStatePayload::level,
            PacketCodecs.BOOL, PlayerStatePayload::hollowActive,
            PacketCodecs.VAR_INT, PlayerStatePayload::hollowLeft,
            PacketCodecs.BOOL, PlayerStatePayload::lost,
            PlayerStatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
