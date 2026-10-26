package com.zangetsu.registry;

import com.mojang.serialization.Codec;
import com.zangetsu.ZangetsuMod;
import com.zangetsu.logic.ZangetsuData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class ModAttachments {
    /** Связь игрока с мечом: дружба, форма Пустого, сон. Переживает смерть. */
    public static final AttachmentType<ZangetsuData> PLAYER = AttachmentRegistry.<ZangetsuData>builder()
            .persistent(ZangetsuData.CODEC)
            .copyOnDeath()
            .initializer(ZangetsuData::new)
            .buildAndRegister(ZangetsuMod.id("player"));

    /** Отрубленные конечности моба — набор флагов из {@link com.zangetsu.logic.Limbs}. */
    public static final AttachmentType<Integer> LIMBS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .buildAndRegister(ZangetsuMod.id("limbs"));

    private ModAttachments() {
    }

    public static ZangetsuData data(PlayerEntity player) {
        return player.getAttachedOrCreate(PLAYER);
    }

    public static int limbs(LivingEntity entity) {
        Integer value = entity.getAttached(LIMBS);
        return value == null ? 0 : value;
    }

    public static void init() {
    }
}
