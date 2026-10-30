package com.zangetsu.network;

import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Сервер → клиент: игрок {@code entityId} играет анимацию (позы рук и, у себя, камера со стороны).
 * {@code param} — у удара меча куда бить: угол к цели относительно взгляда, в градусах.
 */
public record AnimationPayload(int entityId, int animation, int duration, int param) implements CustomPayload {
    public static final int TOUCH_HANDLE = 1;
    public static final int EXECUTE = 2;
    public static final int SLASH = 3;
    public static final int DREAM_PULL = 4;
    public static final int HOLLOW = 5;
    /** Меч сам рвётся к злому мобу и рубит (рука — следом). */
    public static final int AUTO_SLASH = 6;
    /** Гецуга Теншо: замах над плечом, горизонтальный взмах — и волна срывается с клинка. */
    public static final int GETSUGA = 7;

    public static final CustomPayload.Id<AnimationPayload> ID = new CustomPayload.Id<>(ZangetsuMod.id("animation"));
    public static final PacketCodec<ByteBuf, AnimationPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AnimationPayload::entityId,
            PacketCodecs.VAR_INT, AnimationPayload::animation,
            PacketCodecs.VAR_INT, AnimationPayload::duration,
            PacketCodecs.VAR_INT, AnimationPayload::param,
            AnimationPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
