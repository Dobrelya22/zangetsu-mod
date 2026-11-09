package com.zangetsu.logic;

import com.zangetsu.registry.ModAttachments;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/** Поиск, создание и обновление меча в инвентаре. */
public final class SwordStacks {
    /** Урон меча на первом уровне (без бонуса материала); каждый уровень добавляет ещё 2. */
    public static final int BASE_DAMAGE = 6;
    public static final float ATTACK_SPEED = -2.6F;

    private SwordStacks() {
    }

    public static boolean isSword(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(ModItems.ZANGETSU);
    }

    public static AttributeModifiersComponent modifiersFor(int level) {
        return SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, BASE_DAMAGE + (level - 1) * 2, ATTACK_SPEED);
    }

    /** Новый меч с прокачкой игрока. */
    public static ItemStack create(ServerPlayerEntity player) {
        ItemStack stack = new ItemStack(ModItems.ZANGETSU);
        apply(stack, player);
        return stack;
    }

    /** Записать в меч хозяина, уровень, дружбу и урон по уровню. */
    public static void apply(ItemStack stack, ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        ModComponents.SwordInfo info = new ModComponents.SwordInfo(player.getUuid(), player.getGameProfile().getName(),
                data.level(), data.bond);
        if (!info.equals(stack.get(ModComponents.INFO))) {
            stack.set(ModComponents.INFO, info);
        }
        AttributeModifiersComponent modifiers = modifiersFor(data.level());
        if (!modifiers.equals(stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS))) {
            stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers);
        }
    }

    /** Все мечи у игрока: инвентарь и предмет «на курсоре» в открытом окне. */
    public static List<ItemStack> find(PlayerEntity player) {
        List<ItemStack> found = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (isSword(stack)) {
                found.add(stack);
            }
        }
        ItemStack cursor = player.currentScreenHandler.getCursorStack();
        if (isSword(cursor)) {
            found.add(cursor);
        }
        return found;
    }

    public static void refreshAll(ServerPlayerEntity player) {
        for (ItemStack stack : find(player)) {
            apply(stack, player);
        }
    }

    /** Меч в руке (сначала основная, потом вторая) или пустой стек. */
    public static ItemStack held(PlayerEntity player) {
        if (isSword(player.getMainHandStack())) {
            return player.getMainHandStack();
        }
        if (isSword(player.getOffHandStack())) {
            return player.getOffHandStack();
        }
        return ItemStack.EMPTY;
    }

    public static boolean isHolding(PlayerEntity player) {
        return !held(player).isEmpty();
    }

    /** Вложить меч в основную руку; то, что там было, уходит в инвентарь (не влезло — под ноги). */
    public static void giveToHand(ServerPlayerEntity player, ItemStack sword) {
        ItemStack previous = player.getMainHandStack();
        player.setStackInHand(Hand.MAIN_HAND, sword);
        if (!previous.isEmpty() && !player.getInventory().insertStack(previous)) {
            player.dropItem(previous, false);
        }
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Меч не выбрасывается: вместо этого он возвращается в руку (или в инвентарь). Если места нет совсем,
     * из руки выпадает другой предмет — меч важнее.
     */
    public static void returnToInventory(PlayerEntity player, ItemStack sword) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.getMainHandStack().isEmpty()) {
            inventory.setStack(inventory.selectedSlot, sword);
        } else if (!inventory.insertStack(sword)) {
            if (player.getOffHandStack().isEmpty()) {
                player.setStackInHand(Hand.OFF_HAND, sword);
            } else {
                ItemStack previous = inventory.getMainHandStack();
                inventory.setStack(inventory.selectedSlot, sword);
                player.dropItem(previous, false, true);
            }
        }
        player.currentScreenHandler.syncState();
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(net.minecraft.text.Text.translatable("zangetsu.no_drop")
                    .formatted(net.minecraft.util.Formatting.DARK_RED), true);
        }
    }

    public static boolean ownedBy(ItemStack stack, PlayerEntity player) {
        ModComponents.SwordInfo info = stack.get(ModComponents.INFO);
        return info == null || Objects.equals(info.owner(), player.getUuid());
    }
}
