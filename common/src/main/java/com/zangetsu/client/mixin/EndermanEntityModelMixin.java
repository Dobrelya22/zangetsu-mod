package com.zangetsu.client.mixin;

import com.zangetsu.client.ClientState;
import com.zangetsu.logic.Limbs;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EndermanEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Эндермен с рассечённым коленом стоит на одной ноге (вторая — обрубок до колена), с двумя —
 * оседает на обрубки. Поза ставится каждый кадр, потому что модель общая на всех эндерменов.
 */
@Mixin(EndermanEntityModel.class)
public abstract class EndermanEntityModelMixin {
    @Unique
    private static final float ZANGETSU_KNEE = 0.45F;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void zangetsu$applyKnees(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
            float headPitch, CallbackInfo ci) {
        EndermanEntityModel<?> model = (EndermanEntityModel<?>) (Object) this;
        int limbs = ClientState.limbs(entity);
        boolean left = (limbs & Limbs.LEFT_KNEE) != 0;
        boolean right = (limbs & Limbs.RIGHT_KNEE) != 0;
        model.leftLeg.yScale = left ? ZANGETSU_KNEE : 1.0F;
        model.rightLeg.yScale = right ? ZANGETSU_KNEE : 1.0F;
        model.body.roll = 0.0F;
        if (left && right) {
            // Оба колена — ниже на длину отрубленных голеней, ноги-обрубки чуть врозь.
            float drop = 30.0F * (1.0F - ZANGETSU_KNEE);
            for (ModelPart part : new ModelPart[]{model.head, model.hat, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg}) {
                part.pivotY += drop;
            }
            model.leftLeg.pitch = -0.35F;
            model.rightLeg.pitch = -0.35F;
            model.leftLeg.roll = -0.15F;
            model.rightLeg.roll = 0.15F;
        } else if (left || right) {
            // На одной ноге: подрубленная поджата, тело кренится на здоровую.
            ModelPart stump = left ? model.leftLeg : model.rightLeg;
            stump.pitch = -0.5F;
            model.body.roll = left ? -0.08F : 0.08F;
        }
    }
}
