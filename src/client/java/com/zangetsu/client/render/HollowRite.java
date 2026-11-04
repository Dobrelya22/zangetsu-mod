package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Что видно в какой миг превращения. Уровень IV: рука проводит по лицу, и за ней проступает маска. Уровень V: злость, маска нарастает от подбородка,
 * удар молнии разом приносит плащ, цепи и огонь, глаза загораются, когда игрок поднимает голову, а с выпадом всё вспыхивает ещё раз.
 */
public final class HollowRite {
    /** Крайние пиксели маски по высоте в координатах головы: чуть выше лба и чуть ниже подбородка, рог ещё выше. */
    private static final float MASK_TOP = -9.5F, CHIN = 3.5F, HORN_TOP = -19.0F;
    private static final float NO_CLIP = 1000.0F;

    /** Какая часть маски видна (пиксели головы, y вниз) и яркость глаз 0..1. */
    public record Mask(float top, float bottom, float glow) {
        public boolean clipped() {
            return this.top > -NO_CLIP || this.bottom < NO_CLIP;
        }
    }

    private static final Mask WHOLE = new Mask(-NO_CLIP, NO_CLIP, 1.0F);

    private HollowRite() {
    }

    /** Сколько тиков идёт обряд финальной формы; −1 — не идёт. */
    public static float riteTick(Entity entity, float tickDelta) {
        ClientState.Animation animation = ClientState.animation(entity);
        return animation != null && animation.type() == AnimationPayload.HOLLOW_FINAL ? entity.age + tickDelta - animation.startAge() : -1.0F;
    }

    /** Насколько развернулись плащ, цепи и огонь: всё приходит с молнией. */
    public static float bodyGrow(Entity entity, float tickDelta) {
        float tick = riteTick(entity, tickDelta);
        return tick < 0.0F ? 1.0F : SwordMoves.smooth(HollowForm.RITE_STRIKE, HollowForm.RITE_STRIKE + 14.0F, tick);
    }

    /** Насколько рука прошла по лицу: 0 — у лба, 1 — у подбородка. */
    static float swipe(float t) {
        return SwordMoves.smooth(0.24F, 0.6F, t);
    }

    public static Mask mask(Entity entity, float tickDelta) {
        ClientState.Animation animation = ClientState.animation(entity);
        if (animation == null) {
            return WHOLE;
        }
        if (animation.type() == AnimationPayload.HOLLOW) {
            float t = animation.progress(entity, tickDelta);
            // Маска проступает прямо за ладонью, а когда рука ушла, дорастает подбородок.
            float bottom = MathHelper.lerp(swipe(t), MASK_TOP, 2.2F) + 4.0F * SwordMoves.smooth(0.6F, 0.66F, t);
            float eyes = SwordMoves.smooth(0.72F, 0.78F, t);
            return new Mask(-NO_CLIP, bottom >= CHIN + 1.0F ? NO_CLIP : bottom, eyes);
        }
        if (animation.type() == AnimationPayload.HOLLOW_FINAL) {
            float tick = riteTick(entity, tickDelta);
            // Кость ползёт от подбородка вверх, пока он кричит; с ударом молнии маска готова.
            float grown = SwordMoves.smooth(6.0F, HollowForm.RITE_STRIKE, tick);
            float top = MathHelper.lerp(grown, CHIN, HORN_TOP);
            // Глаза вспыхивают от молнии и гаснут, потом загораются, когда он поднимает голову.
            float flash = SwordMoves.smooth(HollowForm.RITE_STRIKE - 1.0F, HollowForm.RITE_STRIKE + 1.0F, tick) * (1.0F - SwordMoves.smooth(HollowForm.RITE_STRIKE + 4.0F, HollowForm.RITE_STRIKE + 14.0F, tick));
            float ignite = SwordMoves.smooth(HollowForm.RITE_IGNITE - 4.0F, HollowForm.RITE_IGNITE + 2.0F, tick);
            return new Mask(grown >= 1.0F ? -NO_CLIP : top, NO_CLIP, Math.max(flash, ignite));
        }
        return WHOLE;
    }

    /** Каждый тик: в нужные мгновения обряда огонь вспыхивает, а ткань и цепи получают толчок. */
    public static void tick(PlayerEntity player) {
        ClientState.Animation animation = ClientState.animation(player);
        if (animation == null) {
            return;
        }
        int tick = player.age - animation.startAge();
        FormPhysics physics = FormPhysics.of(player);
        if (animation.type() == AnimationPayload.HOLLOW) {
            if (tick == HollowForm.MASK_ROAR) {
                physics.kick(1.2F);
            }
            return;
        }
        if (animation.type() != AnimationPayload.HOLLOW_FINAL) {
            return;
        }
        Vec3d look = player.getRotationVector();
        if (tick == HollowForm.RITE_STRIKE) {
            physics.kick(2.2F);
            physics.blast(player.getX(), player.getY() + 0.1, player.getZ(), 0.32F);
        } else if (tick == HollowForm.RITE_IGNITE) {
            physics.kick(1.0F);
        } else if (tick == HollowForm.RITE_SHOULDER) {
            physics.kick(0.5F);
            physics.impulse(0.0F, 0.12F, 0.0F);
        } else if (tick == HollowForm.RITE_LUNGE) {
            physics.kick(1.4F);
            physics.impulse((float) -look.x * 0.5F, 0.1F, (float) -look.z * 0.5F);
        }
    }
}
