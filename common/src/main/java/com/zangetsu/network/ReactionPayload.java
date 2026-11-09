package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Сервер → клиент: началась проверка реакции на моба {@code targetId}. Стрелка стоит {@code lead} тиков, затем делает один оборот за {@code spin} тиков.
 * Доли круга: с {@code from} идёт отличная зона длиной {@code great} (0 — её нет), сразу за ней хорошая длиной {@code good}. {@code targetId} меньше нуля — проверка отменена.
 */
public record ReactionPayload(int targetId, int lead, int spin, float from, float great, float good) implements CustomPayload {
    public static final CustomPayload.Id<ReactionPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("reaction"));
    public static final PacketCodec<ByteBuf, ReactionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ReactionPayload::targetId,
            PacketCodecs.VAR_INT, ReactionPayload::lead,
            PacketCodecs.VAR_INT, ReactionPayload::spin,
            PacketCodecs.FLOAT, ReactionPayload::from,
            PacketCodecs.FLOAT, ReactionPayload::great,
            PacketCodecs.FLOAT, ReactionPayload::good,
            ReactionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
