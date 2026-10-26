package com.zangetsu.client.mixin;

import com.zangetsu.logic.SwordStacks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Клавиша Q с Зангецу в руке ничего не делает — меч даже на миг не исчезает из руки. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void zangetsu$keepSword(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (SwordStacks.isSword(self.getMainHandStack())) {
            self.sendMessage(Text.translatable("zangetsu.no_drop").formatted(Formatting.DARK_RED), true);
            cir.setReturnValue(false);
        }
    }
}
