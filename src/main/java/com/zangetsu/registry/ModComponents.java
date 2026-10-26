package com.zangetsu.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zangetsu.ZangetsuMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Uuids;

/** Что меч хранит в себе: состояние глаза (для текстуры) и сведения для подсказки. */
public final class ModComponents {
    /** Глаз: 0 — закрыт, 1 — открыт, 2 — горит (форма Пустого), 3 — сердится (дружба ниже нуля). */
    public static final ComponentType<Integer> EYE = register("eye",
            ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

    /** Хозяин, уровень и дружба — копия данных игрока, чтобы подсказка и чужие клиенты их видели. */
    public static final ComponentType<SwordInfo> INFO = register("info",
            ComponentType.<SwordInfo>builder().codec(SwordInfo.CODEC).packetCodec(SwordInfo.PACKET_CODEC).build());

    public record SwordInfo(UUID owner, String ownerName, int level, int bond) {
        public static final Codec<SwordInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Uuids.CODEC.fieldOf("owner").forGetter(SwordInfo::owner),
                Codec.STRING.fieldOf("owner_name").forGetter(SwordInfo::ownerName),
                Codec.INT.fieldOf("level").forGetter(SwordInfo::level),
                Codec.INT.fieldOf("bond").forGetter(SwordInfo::bond)
        ).apply(instance, SwordInfo::new));
        public static final PacketCodec<ByteBuf, SwordInfo> PACKET_CODEC = PacketCodec.tuple(
                Uuids.PACKET_CODEC, SwordInfo::owner,
                PacketCodecs.STRING, SwordInfo::ownerName,
                PacketCodecs.VAR_INT, SwordInfo::level,
                PacketCodecs.VAR_INT, SwordInfo::bond,
                SwordInfo::new);
    }

    private ModComponents() {
    }

    private static <T> ComponentType<T> register(String name, ComponentType<T> type) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, ZangetsuMod.id(name), type);
    }

    public static void init() {
        // Компоненты регистрируются при загрузке класса.
    }
}
