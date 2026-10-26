package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер → клиент: игрок {@code entityId} принял или сбросил форму Пустого. */
public record HollowPayload(int entityId, boolean active) implements CustomPayload {
    public static final CustomPayload.Id<HollowPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("hollow"));
    public static final PacketCodec<ByteBuf, HollowPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, HollowPayload::entityId,
            PacketCodecs.BOOL, HollowPayload::active,
            HollowPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
