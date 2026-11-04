package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер → клиент: что происходит в первом сне (широкий кадр, реплика фигуры, куда повернуть взгляд, затемнение). */
public record CallingPayload(int event, String key, int ticks, double x, double y, double z) implements CustomPayload {
    /** Сон начался: чёрный экран расходится за {@code ticks}, широкий кадр. */
    public static final int BEGIN = 0;
    /** Реплика {@code key} длиной {@code ticks}. */
    public static final int SPEECH = 1;
    /** Широкий кадр уходит, интерфейс возвращается; {@code ticks} = 1 — и показать подсказку о выборе. */
    public static final int BARS_OFF = 2;
    public static final int BARS_ON = 3;
    /** Плавно повернуть взгляд на точку за {@code ticks}. */
    public static final int LOOK = 4;
    /** Экран темнеет за {@code ticks}. */
    public static final int FADE_OUT = 5;
    /** Сон кончился: если было темно, чернота расходится за {@code ticks}. */
    public static final int END = 6;

    public static final CustomPayload.Id<CallingPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("calling"));
    public static final PacketCodec<ByteBuf, CallingPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, CallingPayload::event,
            PacketCodecs.STRING, CallingPayload::key,
            PacketCodecs.VAR_INT, CallingPayload::ticks,
            PacketCodecs.DOUBLE, CallingPayload::x,
            PacketCodecs.DOUBLE, CallingPayload::y,
            PacketCodecs.DOUBLE, CallingPayload::z,
            CallingPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
