package com.zangetsu.client.mixin;

import com.zangetsu.client.ClientState;
import com.zangetsu.client.render.FirstPersonMotion;
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

    /** Ванильный взмах руки с Зангецу не нужен: его заменяет приём, и он дёргал бы руку поверх него. */
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void zangetsu$noVanillaSwing(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
            float headPitch, CallbackInfo ci) {
        if (entity instanceof PlayerEntity && entity.getMainHandStack().isOf(ModItems.ZANGETSU)) {
            ((BipedEntityModel<?>) (Object) this).handSwingProgress = 0.0F;
        }
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void zangetsu$applyLimbs(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw,
            float headPitch, CallbackInfo ci) {
        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        if (entity instanceof PlayerEntity) {
            // Руки могли быть укорочены прошлым кадром (модель одна на всех).
            model.rightArm.yScale = 1.0F;
            model.leftArm.yScale = 1.0F;
            if (FirstPersonMotion.drawingArm) {
                return;
            }
            PlayerPoses.apply(model, entity, animationProgress);
            // Удары Зангецу — приёмы всем телом вместо ванильного тычка.
            if (ClientState.animation(entity) == null) {
                PlayerPoses.swing(model, entity, animationProgress);
            }
            // Под маской Пустого слой волос скина торчал бы сквозь кость.
            if (ClientState.isHollow(entity)) {
                model.hat.visible = false;
            }
            // В груди дыра: туловище рисует HollowHoleRenderer.
            if (com.zangetsu.client.RampageScene.hole(entity, animationProgress) > 0.0F) {
                model.body.visible = false;
                if (model instanceof net.minecraft.client.render.entity.model.PlayerEntityModel<?> player) {
                    player.jacket.visible = false;
                }
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
