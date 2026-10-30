package com.zangetsu.trailer;

import static com.zangetsu.trailer.Shots.arc;
import static com.zangetsu.trailer.Shots.arcAround;
import static com.zangetsu.trailer.Shots.eye;
import static com.zangetsu.trailer.Shots.follow;
import static com.zangetsu.trailer.Shots.hero;
import static com.zangetsu.trailer.Shots.mob;
import static com.zangetsu.trailer.Shots.move;
import static com.zangetsu.trailer.Shots.p;
import static com.zangetsu.trailer.Shots.shot;
import static com.zangetsu.trailer.Shots.spirit;
import static com.zangetsu.trailer.Shots.sword;

import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.Eye;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Limbs;
import com.zangetsu.logic.Scheduler;
import com.zangetsu.logic.ZangetsuData;
import com.zangetsu.registry.ModAttachments;
import com.zangetsu.trailer.Shots.P;
import com.zangetsu.trailer.TrailerCamera.Pose;
import com.zangetsu.trailer.TrailerCamera.Shot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Сценарий трейлера под музыку (153 с): пробуждение — первая кровь — глаз — эндермен — битва — форма
 * Пустого — бойня Гецугой — маска трескается — сон с мечами — финал — горящий глаз. Время везде —
 * секунды ролика; монтажные склейки стоят на ударах трека.
 *
 * <p>Герой стоит в центре площадки лицом на север (−Z). Углы камеры — как в {@link TrailerCamera#around}:
 * 0 — за спиной героя (юг), 90 — слева от него (запад), 180 — перед ним (север), 270 — справа (восток).
 */
final class Script {
    private static final int WHITE = 0xFFFFFF;
    private static final int BLACK = 0x000000;
    private static final int RED = 0x8A0000;
    /** Куда смотреть, чтобы увидеть кровавую луну. */
    private static final Vec3d MOON = new Vec3d(0.3215, 0.766, -0.557).normalize();
    private static final List<EntityType<? extends MobEntity>> HORDE = List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE,
            EntityType.SKELETON, EntityType.ZOMBIE, EntityType.STRAY, EntityType.HUSK);
    private static final List<EntityType<? extends MobEntity>> WALKERS = List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE);

    private final List<Scene> scenes = new ArrayList<>();
    private Scene s;

    static List<Scene> build() {
        Script script = new Script();
        script.write();
        script.scenes.forEach(Scene::seal);
        return script.scenes;
    }

    private void write() {
        fade(0.0, 2.6, BLACK, Overlay.FADE_IN);
        awakening();
        eyeOpens();
        enderman();
        eyeOrbit();
        battle();
        transformation();
        rampage();
        dream();
        finale();
        outro();
    }

    // ================================================================ 0:00 — пробуждение и первая кровь

    private void awakening() {
        scene("awaken", 0.0, 33.16);
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 0, false);
            Stage.ring(p, "R", 6, 15.0, 17.0, 60.0, 300.0, WALKERS, false, 2.0F);
            Stage.ring(p, "H", 10, 19.0, 25.0, 35.0, 325.0, HORDE, false, 2.0F);
        };
        s.clientSetup = c -> Director.sneaking = true;

        // Широкий план: буря, снег, крошечный герой на коленях среди мертвецов.
        cut(0.0, "wide", shot(arc(42, 206, 24, 30, 198, 14), p(0, 1.0, 0), 50, 44, 0, 0, true));
        boltAround(1.2, 70, 140);
        boltAround(4.4, 65, 215);
        // Сбоку, у самой земли: раненый, опустив голову.
        cut(5.9, "kneel", shot(arc(3.0, 104, 0.45, 2.6, 88, 0.5), p(0, 1.0, 0), 38));
        // Одно сердце: в небе проступает фигура.
        server(8.9, srv -> Awakening.start(Stage.player(srv)));
        speed(8.9, 0.4F);
        cut(8.9, "sky", shot(arc(2.2, 16, 0.35, 2.0, 26, 0.3), spirit(0, 1.5, 0), 64, 54));
        boltAround(9.9, 90, 176);
        boltAround(11.1, 80, 168);
        // Удар: фигура в профиль.
        spiritAge(11.88, 24);
        flash(11.88, 0.14, WHITE, 0.5F);
        boltAround(11.88, 45, 165);
        cut(11.88, "spirit", shot(spirit(4.6, 1.5, 3.4), spirit(0, 1.45, 0), 44, 38));
        boltAround(12.9, 70, 185);
        // Замах — снизу, с креном.
        spiritAge(14.15, 36);
        speed(14.15, 0.651F);
        cut(14.15, "windup", shot(spirit(-2.2, 0.5, 3.3), spirit(0.2, 1.65, 0), 52, 46, -6, -2, false));
        boltAround(14.9, 60, 160);
        boltAround(15.8, 75, 190);
        // Бросок: камера ведёт летящий меч.
        speed(16.3, 0.66F);
        cut(16.3, "throw", shot(p(-7, 1.2, -6), follow(sword(p(0, 12, -14)), 0.35F), 56));
        // Удар: меч вонзается прямо перед героем.
        speed(18.42, 0.5F);
        shake(18.42, 1.2F);
        flash(18.42, 0.25, WHITE, 0.8F);
        boltAround(18.42, 24, 25);
        cut(18.42, "impact", shot(move(-1.5, 0.35, -8.0, -1.3, 0.35, -7.6), p(0, 0.8, -1.4), 32, 30));
        // Герой встаёт — силуэт на фоне молнии.
        client(20.6, () -> Director.sneaking = false);
        speed(20.6, 1.0F);
        boltAround(20.6, 32, 95);
        cut(20.6, "rise", shot(arc(5.6, 262, 0.9, 5.2, 272, 1.0), p(0, 1.45, 0), 36, 34));
        // Рука на рукояти.
        cut(22.35, "touch", shot(move(0.9, 0.75, -3.7, 0.8, 0.75, -3.5), p(-0.1, 0.8, -1.4), 42, 38));
        server(22.8, srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            ThrownZangetsuEntity sword = Stage.nearestSword(p.getServerWorld(), Stage.center, 8.0, 0.0);
            if (sword != null) {
                Awakening.pickUp(p, sword);
            }
        });
        // ДРОП: вспышка, молнии кольцом, мертвецы срываются с места.
        flash(23.91, 0.45, WHITE, 1.0F);
        shake(23.91, 1.0F);
        boltAround(23.91, 7.5, 135);
        boltAround(23.95, 8.0, 225);
        boltAround(24.0, 9.0, 180);
        server(23.91, srv -> {
            Stage.wake(srv, "R");
            Stage.wake(srv, "H");
        });
        server(24.5, srv -> Stage.bond(Stage.player(srv), 4));
        cut(23.91, "hero", shot(arc(3.7, 157, 0.25, 3.2, 163, 0.3), p(0, 1.6, 0), 38, 34));
        cut(25.71, "horde", shot(arc(13, 250, 2.4, 12.4, 244, 2.0), p(0, 1.0, -2), 50, 46));
        server(25.71, srv -> {
            Vec3d at = Stage.at(-0.9, 0, -1.9);
            Stage.spawn(Stage.player(srv), "Z1", EntityType.ZOMBIFIED_PIGLIN, at, Stage.yawToCenter(at), false, 0.0F);
        });
        // Замедление: левая рука, правая рука.
        speed(27.24, 0.4F);
        cut(27.24, "cut1", shot(move(2.43, 1.4, -2.33, 2.25, 1.4, -2.25), p(-0.45, 1.3, -0.95), 40, 38));
        limb(27.6, "Z1", Limbs.LEFT_ARM);
        cut(28.96, "cut2", shot(move(-2.97, 0.85, 0.25, -2.8, 0.9, 0.1), p(-0.65, 1.45, -1.35), 40, 37));
        limb(29.15, "Z1", Limbs.RIGHT_ARM);
        // Стрелы — рассечены на лету.
        speed(30.03, 0.25F);
        server(30.1, srv -> Stage.arrows(srv, 9.0, 205.0));
        cut(30.03, "arrows", shot(arc(2.0, 335, 1.05, 2.0, 338, 1.1), p(1.0, 1.45, -3.0), 56, 52));
        // Казнь.
        speed(31.32, 0.7F);
        server(31.35, srv -> Stage.execute(srv, "Z1"));
        cut(31.32, "execute", shot(move(3.55, 1.7, -2.85, 3.3, 1.6, -2.75), mob("Z1", p(-0.9, 0, -1.9)).plus(0, 1.3, 0), 42, 38));
        speed(32.42, 0.35F);
        shake(32.42, 1.0F);
        flash(32.42, 0.12, WHITE, 0.6F);
        boltAround(32.42, 20, 200);
    }

    // ================================================================ 0:33 — глаз открывается

    private void eyeOpens() {
        scene("eye", 33.16, 35.40);
        s.setup = Stage::arena;
        s.settle = 10;
        s.sweepCenter = () -> Stage.at(0, 52, 0);
        insert(Eye.OPEN, v -> v < 33.95 ? 0.0 : smooth((v - 33.95) / 0.22), true, new Quaternionf());
        cut(33.16, "eye_open", shot(eyeMove(0.03, 0.012, 0.7, 0.0, 0.0, 0.5), eye(0, 0, 0), 30, 27, -3, 1, true));
    }

    // ================================================================ 0:35 — эндермен

    private void enderman() {
        scene("ender", 35.40, 44.42);
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 4, false);
            Stage.spawn(p, "E", EntityType.ENDERMAN, Stage.at(0, 0, -2.4), 0.0F, false, 0.0F);
            Stage.ring(p, "B", 5, 10.0, 12.0, 150.0, 210.0, WALKERS, false, 0.0F);
        };
        cut(35.40, "ender_wide", shot(arc(7.6, 232, 0.7, 7.0, 226, 0.8), p(0, 1.5, -1.2), 44, 40));
        speed(36.57, 0.45F);
        cut(36.57, "knee_left", shot(move(2.5, 0.35, -1.4, 2.3, 0.35, -1.55), p(0, 0.6, -2.4), 42, 38));
        limb(37.0, "E", Limbs.LEFT_KNEE);
        cut(38.28, "knee_right", shot(move(-2.4, 0.3, -1.6, -2.2, 0.32, -1.75), p(0, 0.65, -2.4), 42, 38));
        limb(38.5, "E", Limbs.RIGHT_KNEE);
        speed(39.82, 1.0F);
        cut(39.82, "hero_close", shot(arc(1.75, 128, 1.5, 1.55, 120, 1.55), p(0, 1.55, 0), 34, 31));
        boltAround(40.4, 30, 185);
        cut(41.50, "split_wide", shot(p(5.8, 1.0, -1.0), p(0, 0.95, -1.2), 38));
        server(41.73, srv -> Stage.execute(srv, "E"));
        speed(42.48, 0.3F);
        shake(42.48, 1.3F);
        flash(42.48, 0.18, WHITE, 0.7F);
        boltAround(42.48, 16, 200);
        cut(42.48, "split", shot(move(1.9, 0.45, -1.0, 2.1, 0.5, -1.1), p(0.2, 0.8, -2.4), 44, 40));
    }

    // ================================================================ 0:44 — глаз, облёт

    private void eyeOrbit() {
        scene("eye2", 44.42, 47.09);
        s.setup = Stage::arena;
        s.settle = 10;
        s.sweepCenter = () -> Stage.at(0, 52, 0);
        insert(Eye.OPEN, v -> 1.0 - bump((v - 45.75) / 0.22), false, new Quaternionf().rotateZ((float) Math.toRadians(14.0)));
        P orbit = (t, d) -> {
            double a = Math.toRadians(MathHelper.lerp(TrailerCamera.ease(t), -48.0, 32.0));
            double r = MathHelper.lerp(TrailerCamera.ease(t), 1.15, 0.55);
            Insert.Spec spec = Insert.current();
            Vec3d e = spec == null ? Stage.at(0, 60, 0) : spec.eyeWorld();
            return e.add(Math.sin(a) * r, MathHelper.lerp(t, 0.16, 0.02), Math.cos(a) * r);
        };
        cut(44.42, "eye_orbit", shot(orbit, eye(0, 0, 0), 36, 30));
    }

    // ================================================================ 0:47 — битва

    private void battle() {
        scene("battle", 47.09, 67.33);
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 4, false);
            Stage.ring(p, "B", 8, 8.0, 13.0, 0.0, 315.0, WALKERS, false, 2.0F);
            Stage.ring(p, "S", 2, 14.0, 14.0, 140.0, 220.0, List.of(EntityType.SKELETON, EntityType.STRAY), false, 0.0F);
            victim(p, "V1", 1.6, -2.0);
            victim(p, "V2", -2.0, -1.3);
        };
        server(47.09, srv -> {
            Stage.wake(srv, "B");
            Stage.wake(srv, "S");
        });
        waves(47.6, 65.0, 2.4, "WB");
        flash(47.09, 0.25, WHITE, 0.7F);
        boltAround(47.09, 10, 160);
        shake(47.09, 0.8F);
        cut(47.09, "battle_wide", shot(arc(16, 215, 10, 14, 195, 7), p(0, 0.8, 0), 55, 50, 0, 0, true));
        boltAround(48.0, 28, 60);
        cut(48.87, "low_orbit", shot(arc(3.4, 320, 0.35, 3.4, 270, 0.4), p(0, 1.35, 0), 48));
        speed(50.46, 0.35F);
        cut(50.46, "v1_arm", shot(p(2.83, 1.35, 0.61), p(0.9, 1.35, -1.2), 40));
        limb(50.6, "V1", Limbs.LEFT_ARM);
        speed(51.07, 1.0F);
        face(51.07, 180.0F);
        cut(51.07, "hero_face", shot(arc(2.1, 195, 1.6, 1.9, 188, 1.6), p(0, 1.6, 0), 32));
        cut(52.17, "v2_arm", shot(p(0.54, 1.35, -3.0), p(-1.1, 1.35, -0.75), 40));
        limb(52.3, "V2", Limbs.RIGHT_ARM);
        speed(53.81, 0.3F);
        server(53.85, srv -> Stage.arrows(srv, 10.0, 120.0));
        cut(53.81, "arrows", shot(arc(3.0, 60, 0.6, 3.0, 70, 0.7), p(0, 1.4, -0.5), 52));
        speed(55.08, 1.0F);
        cut(55.08, "crowd", shot(arc(7, 300, 0.25, 6.5, 290, 0.3), p(0, 1.0, 0), 50));
        cut(56.75, "v1_arm2", shot(move(-0.3, 1.1, -3.5, -0.15, 1.15, -3.4), p(1.5, 1.35, -1.9), 40, 37));
        limb(56.9, "V1", Limbs.RIGHT_ARM);
        boltAround(57.5, 22, 300);
        cut(59.03, "v2_arm2", shot(p(-0.79, 0.4, -3.15), p(-2.0, 1.3, -1.3), 42));
        limb(59.2, "V2", Limbs.LEFT_ARM);
        server(61.06, srv -> Stage.execute(srv, "V1"));
        cut(61.06, "exec1_wide", shot(move(3.8, 1.0, 0.8, 3.6, 1.0, 0.6), p(0.8, 1.2, -1.0), 42));
        cut(61.66, "exec1_head", shot(p(2.9, 1.6, -3.6), mob("V1", p(1.6, 0, -2.0)).plus(0, 1.6, 0), 44));
        shake(61.81, 1.0F);
        flash(61.81, 0.1, WHITE, 0.5F);
        server(63.03, srv -> Stage.execute(srv, "V2"));
        cut(63.03, "exec2", shot(move(-4.2, 0.9, 0.6, -4.0, 0.9, 0.4), p(-1.2, 1.3, -0.8), 40));
        shake(63.78, 1.0F);
        flash(63.78, 0.1, WHITE, 0.5F);
        boltAround(63.78, 18, 20);
        boltAround(64.64, 30, 0);
        cut(64.64, "lightning_wide", shot(arc(18, 170, 3.0, 17, 176, 3.5), p(0, 2.0, 0), 46));
        speed(66.23, 0.35F);
        cut(66.23, "hero_slow", shot(arc(2.8, 232, 0.5, 2.5, 222, 0.55), p(0, 1.55, 0), 36));
    }

    // ================================================================ 1:07 — превращение

    private void transformation() {
        scene("hollow", 67.33, 82.08);
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 4, false);
            Stage.ring(p, "W", 26, 9.0, 16.0, 0.0, 346.0, HORDE, false, 0.0F);
        };
        cut(67.33, "top", shot(move(2, 34, 10, 0, 26, 6), p(0, 0, 0), 50, 46, 0, 8, true));
        boltAround(70.5, 60, 40);
        cut(70.0, "back_low", shot(arc(3.8, 30, 0.4, 3.0, 24, 0.45), p(0, 1.7, -2), 46, 42));
        boltAround(74.0, 28, 95);
        cut(72.9, "profile", shot(arc(2.3, 100, 1.55, 2.1, 104, 1.55), p(0, 1.55, -0.5), 32, 30));
        server(75.53, srv -> HollowForm.toggle(Stage.player(srv)));
        speed(75.53, 0.5F);
        flash(75.53, 0.6, RED, 0.6F);
        shake(75.53, 1.4F);
        cut(75.53, "mask", shot(arc(3.4, 182, 0.5, 2.6, 180, 0.6), p(0, 1.65, 0), 36, 32));
        speed(77.0, 1.0F);
        boltAround(77.48, 14, 200);
        cut(77.48, "ring", shot(arc(7, 20, 4.0, 7, 30, 4.2), p(0, 1.0, -5), 50));
        // Кран вверх — к кровавой луне.
        cut(78.35, "moon", (t, v, d) -> {
            float k = TrailerCamera.ease(t);
            Vec3d from = Stage.at(MathHelper.lerp(k, 1.5, 0.6), MathHelper.lerp(k, 1.2, 7.0), MathHelper.lerp(k, -3.0, -2.2));
            Vec3d head = Stage.at(0, 1.7, 0);
            Vec3d sky = from.add(MOON.multiply(40.0));
            float look = TrailerCamera.ease(MathHelper.clamp((t - 0.15F) / 0.8F, 0.0F, 1.0F));
            return Pose.look(from, head.lerp(sky, look), MathHelper.lerp(k, 40.0F, 52.0F));
        });
    }

    // ================================================================ 1:22 — бойня Гецугой, маска трескается

    private void rampage() {
        scene("rampage", 82.08, 122.54);
        s.settle = 70;
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 4, true);
            Stage.ring(p, "G", 8, 8.0, 14.0, 20.0, 340.0, WALKERS, false, 2.0F);
        };
        // Время формы — с начала съёмки (подготовка сцены бывает долгой, пока строится мир).
        server(82.08, srv -> {
            Stage.wake(srv, "G");
            ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 1100;
        });
        waves(83.0, 116.0, 2.8, "WG");
        flash(82.08, 0.3, WHITE, 0.8F);
        shake(82.08, 1.0F);

        getsuga(82.55, 180.0F, "L1", 0.3F);
        cut(82.08, "reveal", shot(arc(3.0, 178, 0.3, 2.6, 182, 0.35), p(0, 1.7, 0), 40));
        cut(83.27, "wave1", shot(p(9, 1.5, -7), p(0, 1.0, -8), 50));
        speed(84.2, 1.0F);
        getsuga(85.45, 270.0F, "L2", 1.0F);
        cut(85.19, "east_fire", shot(move(-3.0, 0.8, 1.2, -2.8, 0.8, 1.4), p(6, 1.0, 0), 48));
        cut(86.31, "orbit", shot(arc(2.8, 200, 1.0, 2.8, 250, 1.2), p(0, 1.6, 0), 40));
        getsuga(88.3, 90.0F, "L3", 1.0F);
        cut(88.11, "west_fire", shot(p(-3.2, 0.5, 0.6), p(0, 1.6, 0), 46));
        cut(89.08, "crane", shot(arc(14, 135, 8, 12, 120, 6), p(-6, 0, 0), 50, 46, 0, 0, true));
        getsuga(90.75, 0.0F, "L4", 0.4F);
        cut(90.50, "south_fire", shot(p(3.0, 0.4, 1.5), p(-0.5, 1.2, 3.0), 50));
        speed(91.6, 1.0F);
        face(92.19, 180.0F);
        cut(92.19, "mask_close", shot(arc(2.4, 200, 1.6, 2.2, 190, 1.6), p(0, 1.65, 0), 32));
        getsuga(94.3, 225.0F, "L5", 1.0F);
        cut(93.95, "ne_wave", shot(p(9.5, 0.6, -10.5), p(0, 1.2, 0), 44));
        getsuga(96.3, 135.0F, "L6", 0.35F);
        cut(96.11, "nw_slow", shot(p(-2.0, 0.5, -6.5), p(-5, 1.0, -5), 50));
        speed(97.62, 1.0F);
        face(97.62, 180.0F);
        cut(97.62, "sword_close", shot(arcAround(hero(), 1.7, 212, 1.0, 1.5, 236, 1.15), hero().plus(0.35, 0.95, -0.2), 38));
        getsuga(99.6, 180.0F, "L7", 1.0F);
        cut(99.42, "north_high", shot(move(0, 6, -6, 0, 5.5, -5.5), p(0, 1.0, 0), 50));
        boltAround(100.9, 24, 60);
        cut(100.85, "ground_wide", shot(arc(9, 120, 0.2, 8.5, 128, 0.25), p(0, 1.2, 0), 48));
        face(101.90, 180.0F);
        cut(101.90, "push", shot(arc(4.2, 180, 1.6, 2.5, 180, 1.62), p(0, 1.62, 0), 40, 32, 0, 0, true));

        // Пауза в треке: маска трескается.
        server(102.29, srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            Stage.teleport(p, Stage.center, 180.0F, 0.0F);
            ModAttachments.data(p).hollowUsed = HollowForm.BUDGET - 601;
        });
        speed(102.29, 0.4F);
        server(104.0, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 301);
        shake(102.35, 0.4F);
        shake(104.05, 0.6F);
        cut(102.29, "crack", shot(arc(2.5, 170, 1.62, 2.2, 172, 1.62), p(0, 1.62, 0), 30, 28));

        // Пик.
        speed(105.67, 0.5F);
        flash(105.67, 0.22, WHITE, 0.8F);
        getsuga(105.8, 315.0F, "L8", 0.5F);
        cut(105.67, "se_fire", shot(p(-2.5, 0.4, -2.0), p(3, 1.2, 3), 48));
        cut(106.53, "se_hit", shot(p(8.5, 1.2, 1.0), p(5, 0.8, 5), 46));
        server(106.53, srv -> victim(Stage.player(srv), "X1", 1.7, -1.8));
        speed(107.02, 1.0F);
        cut(107.02, "x1_left", shot(p(3.5, 1.3, -0.4), p(1.7, 1.3, -1.8), 40));
        limb(107.15, "X1", Limbs.LEFT_ARM);
        cut(107.80, "x1_right", shot(p(0.4, 1.2, -3.9), p(1.7, 1.35, -1.8), 40));
        limb(107.95, "X1", Limbs.RIGHT_ARM);
        getsuga(108.7, 45.0F, "L9", 1.0F);
        cut(108.56, "sw_fire", shot(p(2.0, 0.5, 2.8), p(-3, 1.0, 3), 50));
        server(109.55, srv -> Stage.execute(srv, "X1"));
        cut(109.51, "x1_exec", shot(p(4.4, 1.0, 0.3), p(0.9, 1.2, -0.9), 42));
        cut(110.15, "x1_head", shot(p(3.0, 1.7, -3.7), mob("X1", p(1.7, 0, -1.8)).plus(0, 1.6, 0), 44));
        shake(110.3, 1.0F);
        flash(110.3, 0.1, WHITE, 0.5F);
        getsuga(111.0, 180.0F, "L10", 1.0F);
        cut(110.88, "north_fire", shot(p(0.6, 0.4, -3.2), p(0, 1.6, 0), 46));
        server(111.5, srv -> victim(Stage.player(srv), "X2", -1.9, -1.2));
        cut(111.50, "crane2", shot(arc(13, 230, 9, 12, 215, 7), p(0, 0, -2), 52, 48, 0, 0, true));
        cut(112.06, "x2_arms", shot(p(-1.0, 1.25, -3.9), p(-1.9, 1.3, -1.2), 40));
        limb(112.15, "X2", Limbs.LEFT_ARM);
        limb(112.6, "X2", Limbs.RIGHT_ARM);
        server(113.05, srv -> Stage.execute(srv, "X2"));
        cut(113.03, "x2_exec", shot(p(-4.6, 1.0, 0.6), p(-1.0, 1.2, -0.6), 42));
        cut(113.70, "x2_head", shot(p(-3.4, 1.6, -3.0), mob("X2", p(-1.9, 0, -1.2)).plus(0, 1.6, 0), 44));
        shake(113.8, 1.0F);
        flash(113.8, 0.1, WHITE, 0.5F);
        server(114.3, srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            Stage.teleport(p, Stage.center, 180.0F, 0.0F);
            ModAttachments.data(p).hollowUsed = HollowForm.BUDGET - 101;
        });
        cut(114.30, "crack3", shot(arc(2.4, 188, 1.62, 2.2, 186, 1.62), p(0, 1.62, 0), 30, 28));
        getsuga(115.4, 270.0F, "L11", 1.0F);
        cut(115.26, "east_wide", shot(arc(10, 330, 1.5, 10, 322, 1.6), p(4, 1.0, 0), 46));
        face(116.84, 180.0F);
        cut(116.84, "tension", shot(arc(2.9, 160, 1.5, 2.5, 168, 1.55), p(0, 1.6, 0), 34, 30));
        // Маска раскалывается.
        server(117.27, srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            Stage.teleport(p, Stage.center, 180.0F, 0.0F);
            HollowForm.end(p, true);
        });
        speed(117.27, 0.35F);
        flash(117.27, 0.35, WHITE, 0.9F);
        shake(117.27, 1.4F);
        cut(117.27, "shatter", shot(arc(2.9, 180, 1.55, 2.6, 180, 1.55), p(0, 1.6, 0), 34, 30));
        // Тишина после бури: герой на колене, снег, тела.
        speed(117.73, 1.0F);
        client(118.3, () -> Director.sneaking = true);
        cut(117.73, "aftermath", shot(arc(4, 150, 1.0, 16, 165, 9), p(0, 0.8, 0), 40, 50, 0, 0, true));
        fade(122.2, 0.34, WHITE, Overlay.FADE_OUT);
    }

    // ================================================================ 2:02 — сон с мечами

    private void dream() {
        scene("dream", 122.54, 132.93);
        s.settle = 100;
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 4, false);
            Dream.forceBegin(p);
        };
        s.sweepCenter = () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            return c.player != null ? c.player.getPos() : Stage.center;
        };
        fade(122.54, 0.9, WHITE, Overlay.FADE_IN);
        s.clientSetup = c -> c.options.getAutoJump().setValue(true);
        cut(122.54, "dream_field", (t, v, d) -> {
            float k = TrailerCamera.ease(t);
            Vec3d h = hero().at(t, d);
            Vec3d from = lift(h.add(MathHelper.lerp(k, -4.5, -2.5), MathHelper.lerp(k, 4.8, 2.6), MathHelper.lerp(k, -7.0, -3.5)), 1.2);
            return Pose.look(from, h.add(0, 1.0, 10), 52.0F);
        });
        client(124.9, () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.world == null || c.player == null) {
                return;
            }
            Vec3d here = c.player.getPos();
            Vec3d ahead = here.add(0, 0, 15);
            c.world.getEntitiesByClass(ThrownZangetsuEntity.class, Box.of(here, 60, 30, 60),
                            e -> e.getState() == ThrownZangetsuEntity.STUCK && e.getPos().distanceTo(here) > 13.0 && e.getPos().distanceTo(here) < 18.0)
                    .stream().min(Comparator.comparingDouble(e -> e.getPos().squaredDistanceTo(ahead)))
                    .ifPresent(sword -> {
                        Stage.IDS.put("T", sword.getId());
                        Director.walkTo = sword.getPos();
                    });
        });
        P target = mob("T", hero().plus(0, 0, 15));
        cut(125.42, "dream_walk", (t, v, d) -> {
            Vec3d h = hero().at(t, d);
            Vec3d dir = flat(target.at(t, d).subtract(h));
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            return Pose.look(lift(h.add(dir.multiply(3.4)).add(side.multiply(0.6)).add(0, 0.55, 0), 0.5), h.add(0, 1.35, 0), 40.0F);
        });
        cut(128.30, "dream_sword", (t, v, d) -> {
            Vec3d sw = target.at(t, d);
            Vec3d h = hero().at(t, d);
            Vec3d dir = flat(sw.subtract(h));
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            Vec3d from = sw.add(side.multiply(MathHelper.lerp(t, 1.5, 1.2))).add(dir.multiply(0.5)).add(0, 0.85, 0);
            return Pose.look(lift(from, 0.5), sw.add(0, 0.85, 0), 38.0F);
        });
        cut(129.80, "dream_reach", (t, v, d) -> {
            Vec3d sw = target.at(t, d);
            Vec3d h = hero().at(t, d);
            Vec3d dir = flat(sw.subtract(h));
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            Vec3d mid = h.lerp(sw, 0.5);
            return Pose.look(lift(mid.add(side.multiply(-2.6)).add(0, 1.2, 0), 0.6), mid.add(0, 1.1, 0), 40.0F);
        });
        server(130.4, srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            Entity e = p.getServerWorld().getEntityById(Stage.id("T"));
            if (e instanceof ThrownZangetsuEntity sword) {
                Dream.pull(p, sword);
            } else {
                ThrownZangetsuEntity near = Stage.nearestSword(p.getServerWorld(), p.getPos(), 6.0, 0.0);
                if (near != null) {
                    Dream.pull(p, near);
                }
            }
        });
        cut(130.34, "dream_rise", (t, v, d) -> {
            Vec3d sw = target.at(t, d);
            Vec3d h = hero().at(t, d);
            Vec3d dir = flat(sw.subtract(h));
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            return Pose.look(lift(sw.add(side.multiply(1.8)).add(dir.multiply(0.8)).add(0, 0.2, 0), 0.4), sw.add(0, 1.5, 0), 46.0F);
        });
        cut(131.75, "dream_wide", (t, v, d) -> {
            Vec3d h = hero().at(t, d);
            Vec3d dir = flat(target.at(t, d).subtract(h));
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            return Pose.look(lift(h.add(side.multiply(6.0)).add(dir.multiply(-2.0)).add(0, 2.0, 0), 1.0), h.add(dir).add(0, 1.5, 0), 48.0F);
        });
        fade(132.4, 0.53, WHITE, Overlay.FADE_OUT);
    }

    // ================================================================ 2:13 — финал

    private void finale() {
        scene("finale", 132.93, 148.35);
        s.settle = 150;
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 4, true);
            Stage.ring(p, "F", 8, 7.0, 13.0, 0.0, 345.0, WALKERS, false, 2.0F);
            Stage.ring(p, "S", 2, 14.0, 14.0, 120.0, 240.0, List.of(EntityType.SKELETON, EntityType.STRAY), false, 0.0F);
        };
        server(132.93, srv -> {
            Stage.wake(srv, "F");
            Stage.wake(srv, "S");
        });
        waves(133.5, 146.5, 2.6, "WF");
        fade(132.93, 0.6, WHITE, Overlay.FADE_IN);
        cut(132.93, "fin_reveal", shot(arc(8, 200, 0.4, 7, 192, 0.5), p(0, 1.6, 0), 42));
        getsuga(134.7, 180.0F, "M1", 1.0F);
        cut(134.56, "fin_fire1", shot(p(-2.6, 0.4, -2.2), p(0, 1.6, 0), 46));
        speed(135.65, 0.4F);
        cut(135.65, "fin_hit1", shot(p(9, 1.2, -6), p(0, 1.0, -9), 48));
        speed(137.0, 1.0F);
        server(137.53, srv -> victim(Stage.player(srv), "Y1", 1.6, -1.9));
        cut(137.53, "fin_orbit", shot(arc(2.5, 240, 1.1, 2.5, 290, 1.2), p(0, 1.6, 0), 40));
        limb(137.8, "Y1", Limbs.LEFT_ARM);
        cut(138.17, "fin_arm", shot(p(0.3, 1.2, -4.0), p(1.6, 1.35, -1.9), 40));
        limb(138.3, "Y1", Limbs.RIGHT_ARM);
        server(140.1, srv -> Stage.execute(srv, "Y1"));
        cut(140.04, "fin_exec", shot(move(4.3, 1.0, 0.4, 4.1, 1.0, 0.3), p(0.8, 1.2, -1.0), 40));
        shake(140.85, 1.0F);
        flash(140.85, 0.12, WHITE, 0.6F);
        getsuga(141.45, 270.0F, "M2", 1.0F);
        cut(141.31, "fin_fire2", shot(move(-3.0, 0.7, 1.3, -2.8, 0.7, 1.5), p(6, 1.0, 0), 48));
        cut(142.48, "fin_crane", shot(arc(15, 160, 11, 13, 145, 8), p(0, 0, 0), 52, 48, 0, 0, true));
        getsuga(143.7, 90.0F, "M3", 1.0F);
        cut(143.54, "fin_fire3", shot(p(-3.2, 0.45, 0.7), p(0, 1.6, 0), 46));
        face(145.01, 180.0F);
        cut(145.01, "fin_face", shot(arc(2.4, 186, 1.62, 2.2, 182, 1.62), p(0, 1.62, 0), 30));
        getsuga(145.9, 0.0F, "M4", 0.35F);
        cut(145.78, "fin_fire4", shot(p(3.2, 0.4, 1.6), p(-0.5, 1.2, 3.0), 50));
        // Силуэт на фоне кровавой луны; маска поворачивается к нам.
        speed(147.09, 1.0F);
        Vec3d cam = new Vec3d(-0.9, 0.15, 1.6);
        cut(147.09, "fin_moon", shot(move(cam.x, cam.y, cam.z, cam.x * 0.9, cam.y, cam.z * 0.9), p(0, 1.75, 0).plus(MOON.x * 2, MOON.y * 2, MOON.z * 2), 50, 46));
        float face = (float) Math.toDegrees(Math.atan2(cam.z, cam.x)) - 90.0F;
        for (int i = 0; i <= 8; i++) {
            float yaw = MathHelper.lerp(i / 8.0F, 180.0F, face);
            server(147.55 + i * 0.05, srv -> Stage.teleport(Stage.player(srv), Stage.center, yaw, -12.0F));
        }
        boltAround(147.6, 40, 330);
    }

    // ================================================================ 2:28 — горящий глаз

    private void outro() {
        scene("outro", 148.35, 153.16);
        s.settle = 50;
        s.setup = srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            Stage.arena(srv);
            ZangetsuData data = ModAttachments.data(p);
            if (!data.hollowActive) {
                base(srv, 4, true);
            } else {
                data.hollowUsed = 0;
            }
        };
        s.sweepCenter = () -> Stage.at(0, 52, 0);
        Vector3f toCamera = new Vector3f((float) -MOON.x, (float) -MOON.y, (float) -MOON.z);
        Quaternionf facing = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), toCamera);
        Quaternionf rotation = new Quaternionf().rotationAxis((float) Math.toRadians(28.0), toCamera).mul(facing);
        insert(Eye.BURNING, v -> v < 150.4 ? 0.0 : smooth((v - 150.4) / 0.45), true, rotation);
        Vec3d side = MOON.crossProduct(new Vec3d(0, 1, 0)).normalize();
        cut(148.35, "burning_eye", (t, v, d) -> {
            Vec3d e = eye(0, 0, 0).at(t, d);
            double dist = MathHelper.lerp(TrailerCamera.ease(t), 0.9, 0.55);
            Vec3d from = e.subtract(MOON.multiply(dist));
            return Pose.look(from, e.add(side.multiply(0.05 * dist)), MathHelper.lerp(t, 34.0F, 28.0F));
        });
        flash(151.29, 0.3, RED, 0.55F);
        shake(151.29, 0.5F);
        fade(152.08, 1.2, BLACK, Overlay.HOLD);
    }

    // ================================================================ словарь сценария

    private void scene(String name, double start, double end) {
        this.s = new Scene(name, start, end);
        this.scenes.add(this.s);
    }

    private void cut(double time, String name, Shot shot) {
        this.s.cuts.add(new Scene.Cut(time, name, shot));
    }

    private void client(double time, Runnable action) {
        this.s.events.add(new Scene.Event(time, action));
    }

    private void server(double time, Consumer<MinecraftServer> action) {
        this.s.events.add(new Scene.Event(time, () -> Lockstep.onServer(() -> action.accept(MinecraftClient.getInstance().getServer()))));
    }

    private void speed(double time, float value) {
        this.s.speeds.put(time, value);
    }

    private void shake(double time, float strength) {
        client(time, () -> TrailerCamera.shake(strength));
    }

    private void flash(double time, double duration, int rgb, float strength) {
        Overlay.add(new Overlay.Fx(time, duration, rgb, Overlay.FLASH, strength));
    }

    private void fade(double time, double duration, int rgb, int kind) {
        Overlay.add(new Overlay.Fx(time, duration, rgb, kind, 1.0F));
    }

    /** Молния (без огня и урона) на расстоянии {@code distance} от героя под углом {@code angle}. */
    private void boltAround(double time, double distance, double angle) {
        server(time, srv -> Stage.lightning(srv, TrailerCamera.around(Stage.center, distance, angle, 0.0)));
    }

    private void limb(double time, String name, int part) {
        server(time, srv -> Stage.cut(srv, name, part));
        shake(time, 0.5F);
    }

    /** Ряд мобов в направлении {@code yaw} (появляется за кадром за секунду) и Гецуга в них. */
    private void getsuga(double time, float yaw, String prefix, float slow) {
        server(time - 0.9, srv -> Stage.line(Stage.player(srv), prefix, yaw, 7.0, 12.0, 6, 4.0F));
        server(time, srv -> Stage.getsuga(srv, yaw));
        shake(time, 0.7F);
        if (slow < 1.0F) {
            speed(time, slow);
        }
    }

    /**
     * Волны: каждые {@code every} секунд — пара мертвецов с разных сторон, из-за кадра. Идут на героя и падают
     * от одного удара меча — так вокруг всё время бой, но толпа не облепляет героя и не закрывает камеры.
     */
    private void waves(double from, double to, double every, String prefix) {
        int i = 0;
        for (double t = from; t < to; t += every, i++) {
            int k = i;
            server(t, srv -> {
                ServerPlayerEntity p = Stage.player(srv);
                for (int j = 0; j < 2; j++) {
                    double angle = (k * 137.5 + j * 180.0 + 40.0) % 360.0;
                    Vec3d at = TrailerCamera.around(Stage.center, 17.0, angle, 0.0);
                    Stage.spawn(p, prefix + k + "_" + j, WALKERS.get((k + j) % WALKERS.size()), at, Stage.yawToCenter(at), true, 2.0F);
                }
            });
        }
    }

    /** Повернуть героя (например, лицом к камере перед крупным планом). */
    private void face(double time, float yaw) {
        server(time, srv -> Stage.teleport(Stage.player(srv), Stage.center, yaw, 0.0F));
    }

    /** Возраст фигуры в небе — на сервере и на клиенте (чтобы склейка попадала в нужную фазу замаха). */
    private void spiritAge(double time, int age) {
        server(time, srv -> {
            ZangetsuSpiritEntity spirit = Stage.spirit(srv);
            if (spirit != null) {
                spirit.age = age;
            }
        });
        client(time, () -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.world != null) {
                for (Entity e : c.world.getEntities()) {
                    if (e instanceof ZangetsuSpiritEntity) {
                        e.age = age;
                    }
                }
            }
        });
    }

    private void insert(int eyeState, DoubleUnaryOperator openness, boolean steady, Quaternionf rotation) {
        Scene scene = this.s;
        scene.clientSetup = c -> scene.insert = new Insert.Spec(Stage.at(0, 60, 0), rotation, eyeState, openness, steady,
                Insert.light(15, 15));
    }

    private static P eyeMove(double x0, double y0, double z0, double x1, double y1, double z1) {
        return (t, d) -> eye(MathHelper.lerp(t, x0, x1), MathHelper.lerp(t, y0, y1), MathHelper.lerp(t, z0, z1)).at(t, d);
    }

    /** Подготовка площадки: герой с мечом нужного уровня, в форме Пустого или без. */
    static ServerPlayerEntity base(MinecraftServer srv, int level, boolean hollow) {
        Stage.arena(srv);
        ServerPlayerEntity p = Stage.player(srv);
        ZangetsuData data = ModAttachments.data(p);
        if (data.hollowActive) {
            HollowForm.end(p, false);
        }
        Stage.clear(p.getServerWorld(), Stage.center);
        Stage.hero(p, level == 0);
        if (level > 0) {
            Stage.bond(p, level);
            data = ModAttachments.data(p);
            data.hollowUsed = 0;
            if (hollow) {
                hollowSoon(p, 0);
            }
        }
        return p;
    }

    /** Форма Пустого — как только герой свободен (после сна он ещё секунду-другую в сцене пробуждения). */
    private static void hollowSoon(ServerPlayerEntity p, int attempt) {
        if (!ModAttachments.data(p).hollowActive) {
            HollowForm.toggle(p);
        }
        if (!ModAttachments.data(p).hollowActive && attempt < 10) {
            Scheduler.later(15, () -> hollowSoon(p, attempt + 1));
        }
    }

    /** Камера не ниже земли и травы (во сне — холмы, валуны, высокая трава). */
    private static Vec3d lift(Vec3d pos, double clearance) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) {
            return pos;
        }
        double ground = c.world.getTopY(Heightmap.Type.WORLD_SURFACE, MathHelper.floor(pos.x), MathHelper.floor(pos.z));
        return pos.y < ground + clearance ? new Vec3d(pos.x, ground + clearance, pos.z) : pos;
    }

    /** Жертва для казни: зомби-пиглин без ИИ (меч сам его не рубит, пока тот не нападёт). */
    private static void victim(ServerPlayerEntity p, String name, double x, double z) {
        Vec3d at = Stage.at(x, 0, z);
        Stage.spawn(p, name, EntityType.ZOMBIFIED_PIGLIN, at, Stage.yawToCenter(at), false, 0.0F);
        if (Stage.entity(p.getServer(), name) instanceof LivingEntity living) {
            living.setHealth(living.getMaxHealth());
        }
    }

    private static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1.0E-6 ? new Vec3d(0, 0, 1) : f.normalize();
    }

    private static double smooth(double t) {
        t = MathHelper.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Короткое моргание: 0 → 1 → 0 за t от 0 до 1. */
    private static double bump(double t) {
        return t < 0.0 || t > 1.0 ? 0.0 : Math.sin(t * Math.PI);
    }
}
