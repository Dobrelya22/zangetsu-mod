package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import com.zangetsu.trailer.TrailerCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Кино-камера трейлера: сразу после обычного расчёта камеры ставит своё положение и поворот (после всех
 * остальных правок камеры), свой угол обзора и крен кадра.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private Camera camera;

    @Inject(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER))
    private void trailer$camera(RenderTickCounter tickCounter, CallbackInfo ci) {
        TrailerCamera.apply(this.camera, tickCounter.getTickDelta(true));
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void trailer$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (TrailerCamera.isActive()) {
            cir.setReturnValue((double) TrailerCamera.fov());
        }
    }

    /** Всплывающий меч, как тотем, — это вид от первого лица; в трейлере его нет. */
    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void trailer$noFloatingItem(ItemStack stack, CallbackInfo ci) {
        if (Director.running()) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void trailer$roll(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (TrailerCamera.isActive()) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(TrailerCamera.roll()));
            ci.cancel();
        }
    }
}
