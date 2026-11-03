package com.zangetsu.client;

import com.zangetsu.logic.Execution;
import com.zangetsu.logic.Limbs;
import com.zangetsu.logic.SwordStacks;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

/** Клавиши мода: R — казнь моба под прицелом, H — форма Пустого. */
public final class KeyBindings {
    public static final double EXECUTE_RANGE = 5.0;
    /** Как далеко от линии взгляда финальная форма ещё находит цель (радианы). */
    private static final double FINAL_AIM_CONE = 0.22;

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

    /**
     * Моб под прицелом (до 5 блоков), которого уже можно казнить. В финальной форме — любой моб из тех, кого меч умеет казнить, издалека
     * и не обязательно точно под прицелом: тот, что ближе всего к линии взгляда.
     */
    public static LivingEntity executionTarget(MinecraftClient client) {
        if (client.player == null || client.world == null || !ClientState.state.bonded() || !SwordStacks.isHolding(client.player)) {
            return null;
        }
        Vec3d look = client.player.getRotationVec(1.0F);
        Vec3d eye = client.player.getCameraPosVec(1.0F);
        if (ClientState.isFinal(client.player)) {
            return finalFormTarget(client, eye, look);
        }
        Vec3d end = eye.add(look.multiply(EXECUTE_RANGE));
        Box box = client.player.getBoundingBox().stretch(look.multiply(EXECUTE_RANGE)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(client.player, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), EXECUTE_RANGE * EXECUTE_RANGE);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity living)) {
            return null;
        }
        return Limbs.executable(living, ClientState.limbs(living), false, ClientState.state.level()) ? living : null;
    }

    private static LivingEntity finalFormTarget(MinecraftClient client, Vec3d eye, Vec3d look) {
        double range = Execution.FLASH_RANGE;
        LivingEntity best = null;
        double bestAngle = FINAL_AIM_CONE;
        for (Entity entity : client.world.getOtherEntities(client.player, new Box(eye, eye.add(look.multiply(range))).expand(3.0))) {
            if (!(entity instanceof LivingEntity living) || !Limbs.executable(living, ClientState.limbs(living), true, ClientState.state.level())) {
                continue;
            }
            Vec3d to = living.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            double along = look.dotProduct(to);
            if (distance > range || along <= 0.0) {
                continue;
            }
            // Угол к мобу, с поправкой на его ширину: вплотную прицел и так внутри него.
            double angle = Math.max(0.0, Math.acos(Math.min(1.0, along / distance)) - Math.atan2(living.getWidth() * 0.5, distance));
            if (angle < bestAngle && client.world.raycast(new RaycastContext(eye, living.getEyePos(), RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, client.player)).getType() == HitResult.Type.MISS) {
                bestAngle = angle;
                best = living;
            }
        }
        return best;
    }
}
