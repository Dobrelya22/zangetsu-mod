package com.zangetsu.mixin;

import com.zangetsu.logic.SwordStacks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** При смерти меч не выпадает: на время «рассыпания» инвентаря его прячем и кладём обратно. */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
    @Shadow
    @Final
    private List<DefaultedList<ItemStack>> combinedInventory;

    @Unique
    private final List<Object[]> zangetsu$kept = new ArrayList<>();

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void zangetsu$hideSwords(CallbackInfo ci) {
        this.zangetsu$kept.clear();
        for (DefaultedList<ItemStack> list : this.combinedInventory) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (SwordStacks.isSword(stack)) {
                    this.zangetsu$kept.add(new Object[]{list, i, stack});
                    list.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "dropAll", at = @At("TAIL"))
    private void zangetsu$restoreSwords(CallbackInfo ci) {
        for (Object[] kept : this.zangetsu$kept) {
            ((DefaultedList<ItemStack>) kept[0]).set((Integer) kept[1], (ItemStack) kept[2]);
        }
        this.zangetsu$kept.clear();
    }
}
