package com.zangetsu;

import com.zangetsu.command.ZangetsuCommand;
import com.zangetsu.logic.ServerEvents;
import com.zangetsu.network.ZangetsuNetworking;
import com.zangetsu.registry.ModAttachments;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModEntities;
import com.zangetsu.registry.ModItems;
import com.zangetsu.world.DreamChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Мод «Зангецу»: меч с живым глазом. Когда у игрока остаётся одно сердце, в небе появляется фигура
 * и бросает ему меч. Дальше меч растёт вместе с дружбой (убивай злых мобов, щади добрых), в темноте
 * открывает глаз и сражается сам, а на четвёртом уровне даёт принять форму Пустого.
 */
public class ZangetsuMod implements ModInitializer {
    public static final String MOD_ID = "zangetsu";
    public static final Logger LOGGER = LoggerFactory.getLogger("Zangetsu");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModComponents.init();
        ModItems.init();
        ModEntities.init();
        ModAttachments.init();
        // Генератор земли сна (холмы, валуны, трава) — до загрузки датапаков.
        Registry.register(Registries.CHUNK_GENERATOR, id("dream"), DreamChunkGenerator.CODEC);
        ZangetsuNetworking.init();
        ServerEvents.init();
        ZangetsuCommand.init();
        LOGGER.info("Зангецу проснулся");
    }
}
