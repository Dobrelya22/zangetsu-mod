package com.zangetsu.platform;

import com.zangetsu.logic.ZangetsuData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * То, что каждый загрузчик делает по-своему. Остальной мод живёт в общем коде и знает только этот интерфейс.
 * Fabric хранит данные во вложениях Fabric API, NeoForge — в своих data attachments; ключи одни и те же.
 */
public interface Platform {
    /** Связь игрока с мечом. Переживает смерть и сохраняется вместе с игроком. */
    ZangetsuData data(PlayerEntity player);

    void setData(PlayerEntity player, ZangetsuData data);

    /** Отрубленные конечности моба, набор флагов из {@link com.zangetsu.logic.Limbs}. */
    int limbs(LivingEntity entity);

    void setLimbs(LivingEntity entity, int limbs);

    /**
     * Пакеты — только через загрузчик. Прямой вызов отправки у обработчика сети ломается в среде разработки NeoForge:
     * там у этого метода другое имя (его перекрывает интерфейс NeoForge).
     */
    void send(ServerPlayerEntity player, CustomPayload payload);

    /** Всем, кто видит сущность, кроме неё самой. */
    void sendToTrackers(Entity entity, CustomPayload payload);

    /** Обычный пакет игры: титры и прочее. */
    void sendPacket(ServerPlayerEntity player, Packet<?> packet);

    String loader();

    static Platform get() {
        Platform platform = Holder.instance;
        if (platform == null) {
            throw new IllegalStateException("Zangetsu: загрузчик не назначил Platform");
        }
        return platform;
    }

    static void set(Platform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static Platform instance;

        private Holder() {
        }
    }
}
