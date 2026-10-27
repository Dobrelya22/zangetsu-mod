package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Director;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Время кадра задаёт съёмка, а не часы компьютера: сколько тиков сделать перед кадром и на какой доле
 * тика его рисовать.
 */
@Mixin(RenderTickCounter.Dynamic.class)
public abstract class RenderTickCounterMixin {
    @Shadow
    private float tickDelta;
    @Shadow
    private float lastFrameDuration;
    @Shadow
    private float lastDuration;
    @Shadow
    private long prevTimeMillis;
    @Shadow
    private long timeMillis;

    @Inject(method = "beginRenderTick(JZ)I", at = @At("HEAD"), cancellable = true)
    private void trailer$frame(long time, boolean tick, CallbackInfoReturnable<Integer> cir) {
        if (!tick || !Director.rolling()) {
            return;
        }
        int ticks = Director.beginFrame();
        this.tickDelta = Director.tickDelta();
        this.lastFrameDuration = Director.frameTicks();
        this.lastDuration = Director.frameTicks();
        this.prevTimeMillis = time;
        this.timeMillis = time;
        cir.setReturnValue(ticks);
    }
}
