package com.zangetsu.registry;

import com.zangetsu.logic.ZangetsuData;
import com.zangetsu.platform.Platform;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Данные мода на сущностях. Хранит их загрузчик (см. {@link Platform}) под ключами zangetsu:player и zangetsu:limbs:
 * связь игрока с мечом (дружба, форма Пустого, сон, переживает смерть) и отрубленные конечности моба.
 */
public final class ModAttachments {
    private ModAttachments() {
    }

    public static ZangetsuData data(PlayerEntity player) {
        return Platform.get().data(player);
    }

    /** Начать с чистого листа: как будто игрок ни разу не видел меч. */
    public static void reset(PlayerEntity player) {
        Platform.get().setData(player, new ZangetsuData());
    }

    public static int limbs(LivingEntity entity) {
        return Platform.get().limbs(entity);
    }

    public static void setLimbs(LivingEntity entity, int limbs) {
        Platform.get().setLimbs(entity, limbs);
    }
}
