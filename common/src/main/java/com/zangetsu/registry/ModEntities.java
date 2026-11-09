package com.zangetsu.registry;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ArrowHalfEntity;
import com.zangetsu.entity.GetsugaEntity;
import com.zangetsu.entity.SeveredPartEntity;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
    public static final EntityType<ZangetsuSpiritEntity> SPIRIT = register("spirit",
            EntityType.Builder.<ZangetsuSpiritEntity>create(ZangetsuSpiritEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0F, 1.0F).maxTrackingRange(16).trackingTickInterval(2).makeFireImmune().disableSummon());
    public static final EntityType<ThrownZangetsuEntity> THROWN_ZANGETSU = register("thrown_zangetsu",
            EntityType.Builder.<ThrownZangetsuEntity>create(ThrownZangetsuEntity::new, SpawnGroup.MISC)
                    // Позиция уходит клиентам каждый тик, чтобы полёт был плавным.
                    .dimensions(0.7F, 2.0F).maxTrackingRange(10).trackingTickInterval(1).makeFireImmune().disableSummon());
    public static final EntityType<SeveredPartEntity> SEVERED_PART = register("severed_part",
            EntityType.Builder.<SeveredPartEntity>create(SeveredPartEntity::new, SpawnGroup.MISC)
                    .dimensions(0.35F, 0.35F).maxTrackingRange(6).trackingTickInterval(2).disableSummon());
    public static final EntityType<ArrowHalfEntity> ARROW_HALF = register("arrow_half",
            EntityType.Builder.<ArrowHalfEntity>create(ArrowHalfEntity::new, SpawnGroup.MISC)
                    .dimensions(0.2F, 0.2F).maxTrackingRange(4).trackingTickInterval(2).disableSummon());
    public static final EntityType<GetsugaEntity> GETSUGA = register("getsuga",
            EntityType.Builder.<GetsugaEntity>create(GetsugaEntity::new, SpawnGroup.MISC)
                    .dimensions(1.0F, 0.6F).maxTrackingRange(8).trackingTickInterval(1).makeFireImmune().disableSummon());

    private ModEntities() {
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        return Registry.register(Registries.ENTITY_TYPE, ZangetsuMod.id(name), builder.build(name));
    }

    public static void init() {
    }
}
