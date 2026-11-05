package com.zangetsu.logic;

import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.CallingPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Зангецу спасает от смерти, как тотем, но ценой себя. Удар, который должен был убить, останавливает время: мир тает в малиновом небе, игрок падает на колено, а меч сам повисает перед ним. Над ним встаёт Яхве,
 * медленно вытягивает руку, и меч сам летит к нему. Он берёт его за обух, сжимает пальцы, по клинку расходятся трещины. Сказав, что разочарован, он одним сжатием кулака разбивает меч на осколки. Игрок остаётся жив, но без меча: чтобы вернуть его, придётся найти настоящего среди фальшивых во сне.
 */
public final class SwordBreak {
    /** Сцена по тикам: мир тает, меч всплывает, фигура проступает, рука достаётся из кармана и вытягивается, меч раскручивается, летит и бьёт в ладонь, пальцы сжимаются и клинок трещит, он говорит, кулак сжимается, клинок разлетается, рука уходит, фигура растворяется, мир возвращается. */
    public static final int VOID_IN = 16, SWORD_FROM = 6, FIGURE_FROM = 14, FIGURE_TICKS = 40, ARM_FROM = 56, READY = 112, SPIN_FROM = 114, LAUNCH = 146, GRIP = 158, CRACK_FROM = 170, SPEECH = 196, CRUSH = 272, SNAP = 280,
            RELEASE = 306, FIGURE_OUT = 326, VOID_OUT = 350, TOTAL = 380;
    /** На сколько градусов всего оборачивается меч в воздухе: ровно столько, чтобы лечь плашмя, остриём вбок. */
    private static final float SPIN_TOTAL = 90.0F + 360.0F * 5.0F;

    /** Угол меча: еле начинает вращаться, потом всё быстрее, на самой большой скорости его останавливает ладонь. */
    public static float spinAngle(float t) {
        float k = net.minecraft.util.math.MathHelper.clamp((t - SPIN_FROM) / (float) (GRIP - SPIN_FROM), 0.0F, 1.0F);
        return SPIN_TOTAL * (float) Math.pow(k, 2.6F);
    }

    /** Где всё стоит относительно игрока (блоки вперёд и вверх) и масштаб фигуры: блоков на метр модели. Он парит высоко, на игрока смотрит сверху. */
    public static final float SCALE = 7.0F, AHEAD = 14.0F, UP = 8.0F, SWORD_AHEAD = 1.75F, SWORD_UP = 0.62F;

    private static final class Session {
        int tick;
        UUID figure;
        boolean hadGravity;
        final List<MobEntity> frozen = new ArrayList<>();
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SwordBreak() {
    }

    public static boolean isActive(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Пока идёт сцена, ранить игрока нечем: время стоит. */
    public static boolean protects(ServerPlayerEntity player) {
        return SESSIONS.containsKey(player.getUuid());
    }

    /** Смертельный удар: если меч с ним, он умрёт вместо игрока. От пустоты и команд не спасает, как и обычный тотем; настоящий тотем в руке срабатывает первым. */
    public static boolean interceptDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity player) || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) || SESSIONS.containsKey(player.getUuid())) {
            return false;
        }
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded || data.lost || data.dream != null || SwordStacks.find(player).isEmpty()) {
            return false;
        }
        for (Hand hand : Hand.values()) {
            if (player.getStackInHand(hand).isOf(Items.TOTEM_OF_UNDYING)) {
                return false;
            }
        }
        start(player);
        return true;
    }

    public static void start(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ZangetsuData data = ModAttachments.data(player);
        player.setHealth(1.0F);
        player.clearStatusEffects();
        player.extinguish();
        player.stopRiding();
        player.stopFallFlying();
        HollowForm.end(player, false);
        Reaction.forget(player);
        for (ItemStack sword : SwordStacks.find(player)) {
            sword.setCount(0);
        }
        data.lost = true;
        data.broken = true;

        Session session = new Session();
        session.hadGravity = !player.hasNoGravity();
        SESSIONS.put(player.getUuid(), session);
        player.setNoGravity(true);
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.fallDistance = 0.0F;

        // Время встало: все, кто рядом, замерли.
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32.0), m -> m.isAlive() && !m.isAiDisabled())) {
            mob.setAiDisabled(true);
            mob.setVelocity(Vec3d.ZERO);
            session.frozen.add(mob);
        }

        // Меч повисает перед игроком, фигура за ним. Взгляд ровный: так клинок встаёт плоскостью с глазом прямо к нему.
        float yaw = player.getYaw();
        player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), yaw, 0.0F);
        player.setBodyYaw(yaw);
        player.setHeadYaw(yaw);
        Vec3d look = Vec3d.fromPolar(0.0F, yaw);
        Vec3d at = player.getPos().add(look.multiply(AHEAD)).add(0.0, UP, 0.0);
        Vec3d sword = player.getPos().add(look.multiply(SWORD_AHEAD)).add(0.0, SWORD_UP, 0.0);
        ZangetsuSpiritEntity figure = ZangetsuSpiritEntity.summon(world, at, yaw + 180.0F, SCALE, player.getUuid());
        UUID uuid = player.getUuid();
        figure.keepWhile(() -> SESSIONS.containsKey(uuid));
        Vec3d anchor = sword.subtract(at);
        figure.beginBreak(new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z), yaw, FIGURE_FROM, FIGURE_TICKS);
        session.figure = figure.getUuid();

        Cinematic.lock(player, TOTAL);
        Sync.animation(player, AnimationPayload.SWORD_BROKEN, TOTAL);
        ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.BARS_ON, "", 0, 0.0, 0.0, 0.0));
        ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.VOID_ON, "", VOID_IN, 0.0, 0.0, 0.0));
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.BLOCK_BELL_RESONATE, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
        Sync.player(player);
    }

    public static void tick(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return;
        }
        if (!player.isAlive()) {
            cleanUp(player, session);
            return;
        }
        session.tick++;
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0F;
        ZangetsuSpiritEntity figure = player.getServerWorld().getEntity(session.figure) instanceof ZangetsuSpiritEntity f ? f : null;
        int t = session.tick;
        if (t < GRIP && t % 26 == 0) {
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.9F, 0.6F);
        }
        if (t == FIGURE_FROM) {
            Fx.sound(player, SoundEvents.ENTITY_WITHER_SPAWN, 0.8F, 0.5F);
        }
        if (t == FIGURE_FROM + 30 && figure != null) {
            figure.setPhase(ZangetsuSpiritEntity.STARE);
        }
        if (t == ARM_FROM) {
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_EMERGE, 0.8F, 0.6F);
        }
        if (t >= SPIN_FROM && t < GRIP && (int) (spinAngle(t) / 180.0F) != (int) (spinAngle(t - 1) / 180.0F)) {
            // Каждый оборот слышно, и всё чаще и выше.
            float k = (t - SPIN_FROM) / (float) (GRIP - SPIN_FROM);
            Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F + 0.6F * k, 0.55F + 0.9F * k);
        }
        if (t == LAUNCH) {
            // Меч срывается с места и летит к его руке.
            Fx.sound(player, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), 1.0F, 0.8F);
            Fx.sound(player, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 0.9F, 0.7F);
        }
        if (t >= CRACK_FROM && t < SNAP && t % 9 == 0) {
            // Клинок трещит всё чаще.
            Fx.sound(player, SoundEvents.BLOCK_GLASS_HIT, 1.0F, 0.6F + (t - CRACK_FROM) * 0.006F);
            Fx.sound(player, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 0.7F, 0.5F + (t - CRACK_FROM) * 0.007F);
        }
        if (t == SPEECH) {
            ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.SPEECH, "zangetsu.break.line", 72, 0.0, 0.0, 0.0));
            if (figure != null) {
                figure.speak(72);
            }
        }
        if (t == CRUSH) {
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 0.5F);
        }
        if (t == SNAP - 3) {
            Fx.sound(player, SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 1.0F, 0.5F);
        }
        if (t == FIGURE_OUT && figure != null) {
            figure.setPhase(ZangetsuSpiritEntity.DISSOLVING);
        }
        if (t == VOID_OUT) {
            ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.VOID_OFF, "", TOTAL - VOID_OUT, 0.0, 0.0, 0.0));
        }
        if (t >= TOTAL) {
            finish(player, session);
        }
    }

    private static void finish(ServerPlayerEntity player, Session session) {
        cleanUp(player, session);
        // Как после тотема: едва жив, но держится.
        player.setHealth(Math.max(player.getHealth(), 1.0F));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0));
        ServerWorld world = player.getServerWorld();
        // Волна от сломанного меча отбрасывает всех, кто рядом.
        for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, new Box(player.getBlockPos()).expand(8.0), e -> e != player && e.isAlive())) {
            Vec3d push = near.getPos().subtract(player.getPos()).normalize();
            near.takeKnockback(1.4, -push.x, -push.z);
        }
        Fx.sound(player, SoundEvents.ITEM_TOTEM_USE, 0.9F, 0.8F);
        ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.END, "", 0, 0.0, 0.0, 0.0));
        player.sendMessage(Text.translatable("zangetsu.break.woke").formatted(Formatting.GRAY, Formatting.ITALIC), false);
        Sync.player(player);
    }

    private static void cleanUp(ServerPlayerEntity player, Session session) {
        SESSIONS.remove(player.getUuid());
        player.setNoGravity(!session.hadGravity);
        player.fallDistance = 0.0F;
        for (MobEntity mob : session.frozen) {
            if (mob.isAlive()) {
                mob.setAiDisabled(false);
            }
        }
        if (session.figure != null) {
            Entity figure = player.getServerWorld().getEntity(session.figure);
            if (figure != null) {
                figure.discard();
            }
        }
    }

    /** Игрок вышел посреди сцены: меч всё равно потерян, а мир возвращается в норму. */
    public static void disconnect(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session != null) {
            cleanUp(player, session);
        }
    }

    public static void clear() {
        SESSIONS.clear();
    }
}
