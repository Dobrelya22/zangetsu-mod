package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Сервер → клиент: эффект на экране самого игрока. */
public record ClientFxPayload(int effect, int duration) implements CustomPayload {
    /** Меч всплывает перед глазами, как тотем бессмертия. */
    public static final int FLOATING_SWORD = 1;
    /** Экран заливает белым (сон кончается). */
    public static final int WHITE_FLASH = 2;
    /** Красная вспышка по краям (превращение в Пустого). */
    public static final int RED_FLASH = 3;
    /** Тряска камеры (меч вонзился в землю). */
    public static final int SHAKE = 4;

    public static final CustomPayload.Id<ClientFxPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("client_fx"));
    public static final PacketCodec<ByteBuf, ClientFxPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ClientFxPayload::effect,
            PacketCodecs.VAR_INT, ClientFxPayload::duration,
            ClientFxPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
