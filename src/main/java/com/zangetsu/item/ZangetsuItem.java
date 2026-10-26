package com.zangetsu.item;

import com.zangetsu.logic.Bond;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.registry.ModComponents;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Зангецу: огромный тесак без гарды с живым глазом на клинке. Не ломается, не горит, не выбрасывается
 * и навсегда привязан к хозяину. Урон растёт с уровнем дружбы.
 */
public class ZangetsuItem extends SwordItem {
    public ZangetsuItem(Settings settings) {
        super(ToolMaterials.NETHERITE, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player && HollowForm.tryGetsuga(player)) {
            user.getItemCooldownManager().set(this, HollowForm.GETSUGA_COOLDOWN);
            user.swingHand(hand, true);
            return TypedActionResult.success(stack, false);
        }
        return TypedActionResult.pass(stack);
    }

    /** Глаз меняется прямо в руке — без этого меч каждый раз «перекладывался» бы заново. */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean allowContinuingBlockBreaking(PlayerEntity player, ItemStack oldStack, ItemStack newStack) {
        return true;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey(stack)).formatted(Formatting.DARK_RED);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        Integer eye = stack.get(ModComponents.EYE);
        tooltip.add(Text.translatable("zangetsu.tooltip.eye." + (eye == null ? 0 : eye)).formatted(Formatting.YELLOW));
        ModComponents.SwordInfo info = stack.get(ModComponents.INFO);
        if (info == null) {
            tooltip.add(Text.translatable("zangetsu.tooltip.unbound").formatted(Formatting.DARK_GRAY));
            return;
        }
        tooltip.add(Text.translatable("zangetsu.tooltip.owner", info.ownerName()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("zangetsu.tooltip.level", info.level(), Text.translatable(Bond.levelKey(info.level())))
                .formatted(Formatting.GOLD));
        int next = Bond.nextThreshold(info.level());
        if (next > 0) {
            tooltip.add(Text.translatable("zangetsu.tooltip.bond", info.bond(), next).formatted(info.bond() < 0 ? Formatting.RED : Formatting.GRAY));
        } else {
            tooltip.add(Text.translatable("zangetsu.tooltip.bond_max", info.bond()).formatted(Formatting.GRAY));
        }
        if (info.bond() < 0) {
            tooltip.add(Text.translatable("zangetsu.tooltip.angry").formatted(Formatting.DARK_RED));
        }
        for (int level = 1; level <= Bond.MAX_LEVEL; level++) {
            boolean open = info.level() >= level;
            tooltip.add(Text.literal(open ? " ✔ " : " ✖ ").append(Text.translatable("zangetsu.tooltip.ability." + level))
                    .formatted(open ? Formatting.WHITE : Formatting.DARK_GRAY));
        }
        tooltip.add(Text.translatable("zangetsu.tooltip.hint").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
