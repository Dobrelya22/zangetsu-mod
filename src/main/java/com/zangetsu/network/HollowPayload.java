package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер → клиент: игрок {@code entityId} принял или сбросил форму Пустого; {@code crack} — насколько треснула маска (0–3). */
public record HollowPayload(int entityId, boolean active, int crack) implements CustomPayload {
    public static final CustomPayload.Id<HollowPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("hollow"));
    public static final PacketCodec<ByteBuf, HollowPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, HollowPayload::entityId,
            PacketCodecs.BOOL, HollowPayload::active,
            PacketCodecs.VAR_INT, HollowPayload::crack,
            HollowPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
