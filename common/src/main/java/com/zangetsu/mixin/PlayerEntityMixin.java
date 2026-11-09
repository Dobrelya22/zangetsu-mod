package com.zangetsu.mixin;

import com.zangetsu.logic.SwordStacks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Зангецу нельзя выбросить: ни клавишей Q, ни из окна инвентаря — он возвращается в руку. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void zangetsu$keepSword(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (SwordStacks.isSword(stack) && !self.getWorld().isClient) {
            SwordStacks.returnToInventory(self, stack);
            cir.setReturnValue(null);
        }
    }
}
