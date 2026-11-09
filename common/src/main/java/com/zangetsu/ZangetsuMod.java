package com.zangetsu;

import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModEntities;
import com.zangetsu.registry.ModItems;
import com.zangetsu.world.DreamChunkGenerator;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Мод «Зангецу»: меч с живым глазом. Когда у игрока остаётся одно сердце, в небе появляется фигура
 * и бросает ему меч. Дальше меч растёт вместе с дружбой (убивай злых мобов, щади добрых), в темноте
 * открывает глаз и сражается сам, а на четвёртом уровне даёт принять форму Пустого.
 *
 * <p>Весь мод — общий код. Загрузчик (Fabric или NeoForge) назначает {@link com.zangetsu.platform.Platform},
 * зовёт {@link #registerContent()}, когда реестры открыты, и подписывает события на
 * {@link com.zangetsu.logic.ServerEvents} и {@link com.zangetsu.client.ZangetsuClient}.
 */
public final class ZangetsuMod {
    public static final String MOD_ID = "zangetsu";
    public static final Logger LOGGER = LoggerFactory.getLogger("Zangetsu");

    private static boolean registered;

    private ZangetsuMod() {
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    /** Предмет, сущности, компоненты и генератор земли сна. На NeoForge — внутри RegisterEvent, на Fabric — сразу. */
    public static void registerContent() {
        if (registered) {
            return;
        }
        registered = true;
        ModComponents.init();
        ModItems.init();
        ModEntities.init();
        // Генератор земли сна (холмы, валуны, трава) — до загрузки датапаков.
        Registry.register(Registries.CHUNK_GENERATOR, id("dream"), DreamChunkGenerator.CODEC);
        LOGGER.info("Зангецу проснулся");
    }
}
