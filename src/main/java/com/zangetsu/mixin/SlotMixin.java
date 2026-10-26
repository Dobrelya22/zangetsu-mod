package com.zangetsu.mixin;

import com.zangetsu.logic.SwordStacks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Меч нельзя положить ни в сундук, ни в печь, ни на верстак — только в собственный инвентарь. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void zangetsu$onlyPlayerInventory(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SwordStacks.isSword(stack) && !(((Slot) (Object) this).inventory instanceof PlayerInventory)) {
            cir.setReturnValue(false);
        }
    }
}
