package com.zangetsu.client.mixin;

import com.zangetsu.client.ZangetsuCombat;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Камера вздрагивает, когда удар Зангецу попадает в цель (вместе с миром и рукой). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void zangetsu$impactKick(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        float kick = ZangetsuCombat.kick(tickDelta);
        if (kick > 0.0F) {
            float age = ZangetsuCombat.kickAge(tickDelta);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(age * 2.3F) * kick * 1.5F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.cos(age * 1.9F) * kick * 1.1F));
        }
    }
}
