package com.zangetsu.trailer.mixin;

import com.zangetsu.trailer.Lockstep;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Во время съёмки сервер тикает только по команде клиента. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void trailer$waitForClient(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        Lockstep.beforeServerTick();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void trailer$tickDone(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        Lockstep.afterServerTick();
    }
}
