package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер → клиент: какие конечности у моба отрублены. */
public record LimbPayload(int entityId, int limbs) implements CustomPayload {
    public static final CustomPayload.Id<LimbPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("limbs"));
    public static final PacketCodec<ByteBuf, LimbPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, LimbPayload::entityId,
            PacketCodecs.VAR_INT, LimbPayload::limbs,
            LimbPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
