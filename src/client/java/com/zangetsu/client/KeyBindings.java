package com.zangetsu.client;

import com.zangetsu.logic.Limbs;
import com.zangetsu.network.ActionPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Клавиши мода: R — казнь моба под прицелом, H — форма Пустого. */
public final class KeyBindings {
    public static final double EXECUTE_RANGE = 5.0;

    public static KeyBinding execute;
    public static KeyBinding hollow;

    private KeyBindings() {
    }

    static void register() {
        execute = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.zangetsu.execute", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R,
                "key.categories.zangetsu"));
        hollow = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.zangetsu.hollow", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
                "key.categories.zangetsu"));
    }

    static void tick(MinecraftClient client) {
        while (execute.wasPressed()) {
            LivingEntity target = executionTarget(client);
            if (target != null) {
                ClientPlayNetworking.send(new ActionPayload(ActionPayload.EXECUTE, target.getId()));
            }
        }
        while (hollow.wasPressed()) {
            if (ClientState.state.bonded()) {
                ClientPlayNetworking.send(new ActionPayload(ActionPayload.HOLLOW, 0));
            }
        }
    }

    /** Моб под прицелом (до 5 блоков), которого уже можно казнить. */
    public static LivingEntity executionTarget(MinecraftClient client) {
        if (client.player == null || client.world == null || !ClientState.state.bonded()) {
            return null;
        }
        Vec3d eye = client.player.getCameraPosVec(1.0F);
        Vec3d end = eye.add(client.player.getRotationVec(1.0F).multiply(EXECUTE_RANGE));
        Box box = client.player.getBoundingBox().stretch(client.player.getRotationVec(1.0F).multiply(EXECUTE_RANGE)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), EXECUTE_RANGE * EXECUTE_RANGE);
        if (hit == null) {
            return null;
        }
        Entity entity = hit.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        int limbs = ClientState.limbs(living);
        if ((limbs & (Limbs.HEAD | Limbs.SPLIT)) != 0) {
            return null;
        }
        boolean ready = (Limbs.isHumanoid(living) && Limbs.disarmed(limbs)) || (Limbs.isEnderman(living) && Limbs.hobbled(limbs));
        return ready ? living : null;
    }
}
