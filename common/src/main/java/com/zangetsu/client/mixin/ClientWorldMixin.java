package com.zangetsu.client.mixin;

import com.zangetsu.client.render.CrescentMoon;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Под кровавой луной небо темнеет и наливается красным. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void zangetsu$bloodSky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        float k = CrescentMoon.intensity();
        if (k > 0.0F) {
            Vec3d sky = cir.getReturnValue();
            Vec3d blood = new Vec3d(0.30, 0.02, 0.04);
            cir.setReturnValue(sky.lerp(blood, k * 0.85));
        }
    }

    /** Пока Пустой забрал тело, над миром ночь: и пакет времени от сервера, и ход времени здесь её не перебьют. */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "setTimeOfDay", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), argsOnly = true)
    private long zangetsu$night(long time) {
        return com.zangetsu.client.RampageScene.night() ? 18000L : time;
    }

    /** Ночь в сцене только на небе: мир освещён, иначе в бою ничего не разобрать. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "getSkyBrightness", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), cancellable = true)
    private void zangetsu$lit(float tickDelta, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        if (com.zangetsu.client.RampageScene.night()) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.72F));
        }
    }
}
