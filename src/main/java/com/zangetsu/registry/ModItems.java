package com.zangetsu.registry;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.item.ZangetsuItem;
import com.zangetsu.logic.SwordStacks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    public static final Item ZANGETSU = Registry.register(Registries.ITEM, ZangetsuMod.id("zangetsu"), new ZangetsuItem(new Item.Settings()
            .maxCount(1)
            .fireproof()
            .rarity(Rarity.EPIC)
            .attributeModifiers(SwordStacks.modifiersFor(1))
            .component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false))
            .component(ModComponents.EYE, 0)));

    private ModItems() {
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(ZANGETSU));
    }
}
