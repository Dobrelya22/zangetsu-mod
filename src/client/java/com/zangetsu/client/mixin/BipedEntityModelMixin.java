package com.zangetsu.client.mixin;

import com.zangetsu.client.ClientState;
import com.zangetsu.client.render.PlayerPoses;
import com.zangetsu.logic.Limbs;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Игрокам — позы сцен; мобам — обрубки вместо отсечённых рук и отсутствие головы после казни.
 * Модель одна на всех мобов вида, поэтому после «покалеченного» моба её надо вернуть в норму.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
    /** Модель сейчас покалечена (кому-то рисовали обрубки) — следующему мобу её надо выправить. */
    @Unique
    private boolean zangetsu$maimed;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void zangetsu$applyLimbs(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
            float headPitch, CallbackInfo ci) {
        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        if (entity instanceof PlayerEntity) {
            PlayerPoses.apply(model, entity, animationProgress);
            // Обычный удар Зангецу — тяжёлый взмах всем корпусом вместо ванильного тычка.
            if (ClientState.animation(entity) == null && model.handSwingProgress > 0.0F && entity.getMainHandStack().isOf(ModItems.ZANGETSU)) {
                PlayerPoses.cleave(model, model.handSwingProgress, ClientState.swingVariant(entity));
                model.hat.copyTransform(model.head);
            }
            // Под маской Пустого слой волос скина торчал бы сквозь кость.
            if (ClientState.isHollow(entity)) {
                model.hat.visible = false;
            }
            return;
        }
        int limbs = ClientState.limbs(entity);
        if (limbs == 0 && !this.zangetsu$maimed) {
            return;
        }
        boolean humanoid = Limbs.isHumanoid(entity);
        boolean leftCut = humanoid && (limbs & Limbs.LEFT_ARM) != 0;
        boolean rightCut = humanoid && (limbs & Limbs.RIGHT_ARM) != 0;
        boolean headless = (limbs & Limbs.HEAD) != 0;
        zangetsu$stump(model.leftArm, leftCut);
        zangetsu$stump(model.rightArm, rightCut);
        model.head.visible = !headless;
        model.hat.visible = !headless;
        if (model instanceof PlayerEntityModel<?> player) {
            // У пиглинов поверх рук ещё «рукава» — их тоже укорачиваем (масштаб они копируют сами).
            player.leftSleeve.visible = true;
            player.rightSleeve.visible = true;
        }
        this.zangetsu$maimed = limbs != 0;
    }

    /** Отрубленная рука — короткий обрубок у плеча. */
    @Unique
    private static void zangetsu$stump(ModelPart arm, boolean cut) {
        arm.visible = true;
        arm.yScale = cut ? 0.22F : 1.0F;
    }
}
