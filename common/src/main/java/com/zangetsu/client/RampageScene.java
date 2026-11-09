package com.zangetsu.client;

import com.zangetsu.logic.HollowRampage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Сцена, где Пустой забирает тело (см. {@link HollowRampage}), на клиенте: камера и то, насколько раскрылись дыра в груди и огонь в глазу. Камера здесь не привязана к взгляду игрока:
 * он мечется по полю, поэтому она следует за ним с лёгким запаздыванием и стоит на месте, когда он слишком быстр.
 */
public final class RampageScene {
    private static float started = -1.0F;

    private RampageScene() {
    }

    private static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Сколько тиков идёт сцена у этого игрока: возраст сущности с долей тика. Меньше нуля, если не идёт. */
    public static float tick(Entity entity, float age) {
        return ClientState.RAMPAGE.containsKey(entity.getId()) ? age - ClientState.RAMPAGE.get(entity.getId()) : -1.0F;
    }

    /** Насколько раскрылась дыра в груди (0..1). */
    public static float hole(Entity entity, float age) {
        float t = tick(entity, age);
        return t < 0.0F ? 0.0F : smooth(HollowRampage.RELEASE + 4.0F, HollowRampage.RELEASE + 30.0F, t) * (1.0F - smooth(HollowRampage.MASK, HollowRampage.MASK + 18.0F, t));
    }

    /** Пока в груди раскрывается дыра, огонь на руках и ногах приседает: иначе её не разглядеть. */
    public static float flameDim(Entity entity, float age) {
        float t = tick(entity, age);
        return t < 0.0F ? 1.0F : 1.0F - 0.65F * smooth(HollowRampage.RELEASE - 4.0F, HollowRampage.RELEASE + 6.0F, t) * (1.0F - smooth(HollowRampage.RELEASE + 34.0F, HollowRampage.GROWN, t));
    }

    /** Насколько горит глаз (0..1). */
    public static float eyeFire(Entity entity, float age) {
        float t = tick(entity, age);
        return t < 0.0F ? 0.0F : smooth(HollowRampage.IGNITE, HollowRampage.IGNITE + 10.0F, t);
    }

    /** Пока идёт сцена, над миром ночь: на небе красная луна. */
    public static boolean night() {
        return started >= 0.0F && ClientState.ticks - started >= HollowRampage.RELEASE;
    }

    static void begin(PlayerEntity player, int duration, int baseYaw) {
        ClientState.RAMPAGE.put(player.getId(), player.age);
        MinecraftClient client = MinecraftClient.getInstance();
        if (player != client.player) {
            return;
        }
        started = ClientState.ticks;
        fly(player, duration, baseYaw);
    }

    static void end(PlayerEntity player) {
        ClientState.RAMPAGE.remove(player.getId());
        if (player == MinecraftClient.getInstance().player) {
            started = -1.0F;
        }
    }

    static void reset() {
        started = -1.0F;
    }

    static void tick(MinecraftClient client) {
        if (night() && client.world != null) {
            client.world.setTimeOfDay(18000L);
        }
    }

    private static CameraDirector.Key key(float at, float yaw, float pitch, float distance, float lift) {
        return new CameraDirector.Key(at, yaw, pitch, distance, 0.0F, lift);
    }

    /** Весь полёт камеры: ракурсы считаются от того, куда он смотрел, когда умер. */
    private static void fly(PlayerEntity player, int duration, float base) {
        int planted = HollowRampage.PLANTED, line = HollowRampage.LINE_1, release = HollowRampage.RELEASE, grown = HollowRampage.GROWN, risen = HollowRampage.RISEN, fight = HollowRampage.FIGHT_FROM;
        int surround = HollowRampage.SURROUND, moon = HollowRampage.MOON, eye = HollowRampage.EYE, push = HollowRampage.PUSH, sweep = HollowRampage.SWEEP, behind = HollowRampage.BEHIND;
        int chiburi = HollowRampage.CHIBURI, sever = HollowRampage.SEVER, line2 = HollowRampage.LINE_2, mask = HollowRampage.MASK, kneel = HollowRampage.KNEEL, end = HollowRampage.END;
        List<CameraDirector.Key> keys = new ArrayList<>();
        // Падение и слова: сбоку, потом спереди, снизу, медленный наезд.
        keys.add(key(0.0F, 30.0F, 4.0F, 5.0F, -0.4F));
        keys.add(key(planted, 24.0F, 0.0F, 4.4F, -0.72F));
        keys.add(key(line - 4.0F, 196.0F, -2.0F, 4.0F, -0.72F));
        keys.add(key(line + 56.0F, 186.0F, -4.0F, 3.1F, -0.66F));
        // Пустой выходит: камеру отбрасывает, она обходит его.
        keys.add(key(release, 184.0F, -4.0F, 3.0F, -0.6F));
        // Дыра раскрывается: камера на его грудь.
        keys.add(key(release + 6.0F, 182.0F, -2.0F, 3.2F, -0.42F));
        keys.add(key(release + 34.0F, 178.0F, -2.0F, 2.3F, -0.4F));
        keys.add(key(grown, 150.0F, 0.0F, 4.4F, -0.3F));
        keys.add(key(risen, 120.0F, 6.0F, 7.0F, -0.1F));
        // Бой: орбита сверху, над головами толпы, чтобы никто не заслонял его.
        float yaw = 60.0F;
        int beat = 0;
        for (int at = fight; at < surround - 10; at += 52) {
            keys.add(key(at, yaw, beat % 2 == 0 ? 24.0F : 30.0F, beat % 2 == 0 ? 8.5F : 10.0F, -0.3F));
            yaw += 58.0F;
            beat++;
        }
        // Его окружили: сверху видно кольцо, потом камера опускается к ним.
        float circle = yaw + 20.0F;
        keys.add(key(surround, circle, 34.0F, 17.0F, -0.2F));
        keys.add(key(surround + 52.0F, circle - 34.0F, 16.0F, 12.0F, -0.3F));
        // Луна: из-за его плеча вверх. Луна висит в одной стороне неба, поэтому ракурс задан точно на неё.
        float toMoon = MathHelper.wrapDegrees(HollowRampage.MOON_YAW - base);
        toMoon += 360.0F * Math.round((circle - 34.0F - toMoon) / 360.0F);
        keys.add(key(moon - 8.0F, toMoon - 8.0F, 4.0F, 8.0F, 0.3F));
        keys.add(key(moon, toMoon + 7.0F, -38.0F, 3.1F, 0.9F));
        keys.add(key(eye - 1.0F, toMoon + 4.0F, -41.0F, 2.9F, 0.95F));
        // Склейка: его глаз в упор.
        keys.add(key(eye - 1.0F, toMoon + 180.0F, -3.0F, 1.7F, 0.34F));
        keys.add(key(push - 1.0F, toMoon + 180.0F, -2.0F, 1.2F, 0.34F));
        // Склейка: толчок виден широко.
        keys.add(key(push - 1.0F, circle + 20.0F, 18.0F, 10.0F, -0.3F));
        keys.add(key(sweep, circle + 24.0F, 24.0F, 18.0F, -0.3F));
        keys.add(key(behind - 1.0F, circle + 80.0F, 22.0F, 17.0F, -0.3F));
        // Склейка: он за их спинами, лицом к нам, они стоят позади него.
        keys.add(key(behind - 1.0F, 200.0F, 0.0F, 7.6F, -0.35F));
        // «Жалкие»: камера медленно подходит. Потом взмах, и её отбрасывает: толпа за его спиной разваливается.
        keys.add(key(line2, 194.0F, 0.0F, 5.2F, -0.3F));
        keys.add(key(chiburi, 190.0F, 0.0F, 4.2F, -0.25F));
        keys.add(key(sever + 3.0F, 190.0F, 0.0F, 6.6F, -0.35F));
        keys.add(key(sever + 20.0F, 194.0F, 2.0F, 8.0F, -0.35F));
        keys.add(key(mask - 16.0F, 184.0F, 0.0F, 2.8F, 0.25F));
        keys.add(key(mask + 4.0F, 182.0F, 0.0F, 4.0F, 0.1F));
        keys.add(key(kneel + 10.0F, 176.0F, 2.0F, 4.2F, -0.55F));
        keys.add(key(end + 4.0F, 120.0F, 10.0F, 4.2F, 0.0F));
        CameraDirector.fly(duration + 4, keys.toArray(new CameraDirector.Key[0]));
        CameraDirector.anchor(player, base, sweep, behind - 1.0F);
    }
}
