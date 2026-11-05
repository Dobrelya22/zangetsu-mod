package com.zangetsu.trailer;

import static com.zangetsu.trailer.Shots.arc;
import static com.zangetsu.trailer.Shots.arcAround;
import static com.zangetsu.trailer.Shots.eye;
import static com.zangetsu.trailer.Shots.follow;
import static com.zangetsu.trailer.Shots.hero;
import static com.zangetsu.trailer.Shots.move;
import static com.zangetsu.trailer.Shots.p;
import static com.zangetsu.trailer.Shots.shot;

import com.zangetsu.client.ReactionCheck;
import com.zangetsu.logic.Calling;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.Execution;
import com.zangetsu.logic.Eye;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Limbs;
import com.zangetsu.logic.Reaction;
import com.zangetsu.logic.SwordBreak;
import com.zangetsu.registry.ModAttachments;
import com.zangetsu.trailer.Shots.P;
import com.zangetsu.trailer.TrailerCamera.Shot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Дубли для музыкального клипа (-PtrailerScript=clip -PtrailerFps=240 -PtrailerTakes=папка). Каждая сцена снимается в свой файл и начинается с нуля: что откуда брать и под какой удар трека ставить, решает монтаж
 * (tools/clip). Сцены с настоящей камерой игры сняты как есть, остальные своей кино-камерой.
 */
final class Clip {
    private static final List<EntityType<? extends MobEntity>> WALKERS = List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE);

    private final List<Scene> scenes = new ArrayList<>();
    private Scene s;

    static List<Scene> build() {
        Overlay.bars = false;
        Clip clip = new Clip();
        clip.write();
        clip.scenes.forEach(Scene::seal);
        return clip.scenes;
    }

    private void write() {
        calling();
        trial();
        executions();
        reactions();
        baseFight();
        masks();
        hollowFight();
        swordBreak();
        breakClose();
        finalForm();
        rampage();
        rampageHole();
        beauty();
    }

    // ---------------------------------------------------------------- первый сон

    private void calling() {
        scene("calling_game", 0.0, 27.0);
        gameCamera();
        s.setup = srv -> base(srv, 0, false);
        server(0.05, srv -> Calling.begin(Stage.player(srv)));
        cut(0.0, "game", shot(p(0, 5, 5), p(0, 0, 0), 70));

        // Фигура стоит на +Z от героя, 70 блоков вперёд и 52 вверх; меч падает в 3,8 блока перед ним.
        scene("calling_cine", 0.0, 27.0);
        s.settle = 60;
        s.setup = srv -> base(srv, 0, false);
        server(0.05, srv -> Calling.begin(Stage.player(srv)));
        cut(0.0, "back_low", shot(heroAt(3.2, 0.35, -4.0), heroAt(0, 36, 70), 70, 64));
        cut(9.0, "face_tele", shot(heroAt(0, 1.6, -1.0), heroAt(0, 80, 70), 15, 12));
        cut(13.0, "side_wide", shot(heroAt(-46, 12, 8), heroAt(0, 52, 62), 60, 56));
        cut(17.0, "arms", shot(heroAt(0.8, 1.4, -2.0), heroAt(0, 62, 70), 36, 32));
        cut(20.3, "sky_low", shot(heroAt(2.0, 0.35, -2.4), heroAt(-0.6, 20, 30), 58));
        cut(22.45, "stuck", shot(heroAt(2.6, 0.7, 9.0), heroAt(0, 1.0, 3.4), 32, 28));
    }

    // ---------------------------------------------------------------- поле мечей

    private void trial() {
        // Мир строится секунд двадцать, а поиски с подсказками начинаются на тридцатой: снимаем только то, что между ними.
        scene("trial", 0.0, 8.0);
        s.settle = 130;
        s.sweepCenter = () -> hero().at(0.0F, 0.0F);
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 4, false);
            ModAttachments.data(p).broken = true;
            Dream.forceBegin(p);
        };
        cut(0.0, "low_field", shot(heroAt(-5, 0.6, 7), heroAt(0, 14, 40), 64, 60));
        cut(2.0, "behind", shot(heroAt(0.9, 0.45, -3.6), heroAt(0, 30, 70), 74, 70));
        cut(4.0, "close", shot(arcAround(hero(), 2.8, 150, 1.3, 2.5, 185, 1.4), hero().plus(0, 1.45, 0), 36));
        cut(6.0, "swords", shot(heroAt(-5.0, 0.7, 5.0), heroAt(1, 1.0, 16), 36));
    }

    // ---------------------------------------------------------------- казни

    private void executions() {
        // Дубль «a» — двое в профиль, низко и длинным объективом; «b» — близко, в самый удар.
        exec("kesa", EntityType.ZOMBIE, Execution.Variant.KESA, profile(1, 5.6, 0.5), shoulder(-1));
        exec("behead", EntityType.PIGLIN, Execution.Variant.BEHEAD, profile(-1, 5.4, 0.45), below(1));
        exec("waist", EntityType.ENDERMAN, Execution.Variant.WAIST, profile(1, 7.0, 0.6), face(1));
        exec("shatter", EntityType.SKELETON, Execution.Variant.SHATTER, profile(-1, 5.6, 0.5), shoulder(1));
        exec("launch", EntityType.CREEPER, Execution.Variant.LAUNCH, profile(1, 6.0, 0.4), below(-1));
        exec("gyaku", EntityType.VINDICATOR, Execution.Variant.GYAKU, profile(-1, 5.4, 0.5), face(-1));
        exec("chest", EntityType.PILLAGER, Execution.Variant.CHEST, profile(1, 5.2, 0.6), below(1));
        exec("cleave", EntityType.RAVAGER, Execution.Variant.CLEAVE, profile(-1, 9.5, 0.7), below(-1));
        exec("split", EntityType.WITCH, Execution.Variant.SPLIT_UP, profile(1, 5.8, 0.5), shoulder(-1));
        exec("slices", EntityType.WITHER_SKELETON, Execution.Variant.SLICES, profile(-1, 6.2, 0.6), face(1));
    }

    private static Shot profile(int side, double distance, double height) {
        return shot(move(side * distance, height, -0.9, side * (distance - 0.7), height, -1.1), p(0, 1.2, -1.3), 30, 28);
    }

    private static Shot shoulder(int side) {
        return shot(move(side * 2.0, 1.2, 3.8, side * 1.8, 1.15, 3.4), p(0, 1.3, -2.0), 32, 30);
    }

    private static Shot below(int side) {
        return shot(move(side * 2.3, 0.22, -0.7, side * 2.1, 0.25, -0.9), p(0, 1.75, -2.5), 42, 38);
    }

    private static Shot face(int side) {
        return shot(move(side * 2.9, 0.75, -5.4, side * 2.6, 0.8, -5.0), p(0, 1.35, -0.9), 34, 32);
    }

    private void exec(String name, EntityType<? extends MobEntity> type, Execution.Variant variant, Shot wide, Shot close) {
        execTake("x_" + name + "_a", type, variant, wide);
        execTake("x_" + name + "_b", type, variant, close);
    }

    private void execTake(String name, EntityType<? extends MobEntity> type, Execution.Variant variant, Shot camera) {
        scene(name, 0.0, 4.0);
        // Уровень II: с третьего меч сам зарубит моба, пока сцена готовится.
        s.setup = srv -> Stage.spawn(base(srv, 2, false), "V", type, Stage.at(0, 0, -2.6), 0.0F, false, 0.0F);
        speed(0.0, 0.5F);
        server(0.3, srv -> Stage.execute(srv, "V", variant));
        cut(0.0, "main", camera);
    }

    /** Проверка реакции: враг, круг, отличный клик и настоящая сцена «ты слишком медлителен». */
    private void reactions() {
        reaction("qte_good", EntityType.ZOMBIE, Execution.Variant.QUARTER, 4, false);
        reaction("qte_quarter", EntityType.ZOMBIE, Execution.Variant.QUARTER, 5, true);
        reaction("qte_cross", EntityType.ENDERMAN, Execution.Variant.CROSS, 5, true);
        reaction("qte_triple", EntityType.PIGLIN, Execution.Variant.TRIPLE, 5, true);
    }

    private void reaction(String name, EntityType<? extends MobEntity> type, Execution.Variant variant, int level, boolean hollow) {
        scene(name, 0.0, 6.0);
        s.firstPerson = true;
        s.gameCamera = true;
        s.hideHud = true;
        s.settle = 70;
        s.setup = srv -> base(srv, level, hollow);
        server(0.1, srv -> {
            Stage.spawn(Stage.player(srv), "V", type, Stage.at(0, 0, -3.4), 0.0F, false, 0.0F);
            if (Stage.entity(srv, "V") instanceof MobEntity mob) {
                mob.setInvulnerable(false);
                mob.setHealth(2.0F);
                Execution.nextBehind = variant;
                Reaction.enabled = true;
                Reaction.start(Stage.player(srv), mob);
            }
        });
        client(1.0, () -> ReactionCheck.answerAt(ReactionCheck.zoneMiddle(hollow ? ReactionCheck.GREAT : ReactionCheck.GOOD)));
        cut(0.0, "qte", shot(p(0, 5, 5), p(0, 0, 0), 70));
    }

    // ---------------------------------------------------------------- бой без формы

    private void baseFight() {
        // Идёт на камеру, низко: первый кадр героя после названия.
        scene("b_walk", 0.0, 6.0);
        s.settle = 60;
        s.setup = srv -> base(srv, 3, false);
        client(0.1, () -> Director.walkTo = Stage.at(0, 0, -30));
        cut(0.0, "walk", shot(heroAt(-0.9, 0.3, -5.2), heroAt(0, 1.3, 0), 30, 28));

        scene("b_face", 0.0, 5.0);
        s.settle = 60;
        s.setup = srv -> base(srv, 3, false);
        cut(0.0, "face", shot(arc(1.75, 210, 1.58, 1.5, 192, 1.6), p(0, 1.55, 0), 28, 26));

        // Трое вокруг: руби по очереди, камера кружит и не упирается ни в кого спиной.
        scene("b_three", 0.0, 8.0);
        s.settle = 60;
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 2, false);
            Stage.spawn(p, "A", EntityType.ZOMBIE, Stage.at(0, 0, -2.6), 0.0F, false, 0.0F);
            Stage.spawn(p, "B", EntityType.HUSK, Stage.at(2.6, 0, 0), 90.0F, false, 0.0F);
            Stage.spawn(p, "C", EntityType.VINDICATOR, Stage.at(-2.6, 0, 0), 270.0F, false, 0.0F);
        };
        speed(0.0, 0.5F);
        server(0.3, srv -> Stage.execute(srv, "A", Execution.Variant.KESA));
        server(2.8, srv -> {
            Stage.teleport(Stage.player(srv), Stage.center, 270.0F, 0.0F);
            Stage.execute(srv, "B", Execution.Variant.WAIST);
        });
        server(5.4, srv -> {
            Stage.teleport(Stage.player(srv), Stage.center, 90.0F, 0.0F);
            Stage.execute(srv, "C", Execution.Variant.BEHEAD);
        });
        cut(0.0, "circle", shot(arc(6.5, 215, 0.5, 6.0, 300, 0.7), p(0, 1.15, 0), 34));
    }

    // ---------------------------------------------------------------- маски

    private void masks() {
        rite("rite_mask", 4, 6.5, 0.5F, shot(arc(1.9, 206, 1.62, 1.55, 196, 1.62), p(0, 1.55, 0), 34, 30));
        rite("rite_mask_b", 4, 6.5, 0.5F, shot(arc(4.6, 150, 0.3, 4.2, 165, 0.3), p(0, 1.45, 0), 34));

        scene("rite_final", 0.0, 12.0);
        s.settle = 20;
        s.setup = srv -> base(srv, 5, false);
        server(0.1, srv -> {
            HollowForm.skipRite = false;
            HollowForm.toggle(Stage.player(srv));
        });
        speed(0.0, 0.8F);
        cut(0.0, "rage", shot(arc(3.0, 200, 1.4), p(0, 1.1, 0), 42));
        cut(2.5, "strike", shot(arc(8.0, 190, 2.2), p(0, 2.0, 0), 56));
        cut(3.3, "kneel", shot(arc(2.6, 140, 0.7, 2.6, 300, 0.7), p(0, 0.55, 0), 46));
        cut(5.5, "rise", shot(arc(3.2, 180, 0.6), p(0, 1.2, 0), 50));
        cut(7.5, "shoulder", shot(arc(2.8, 212, 1.5), p(0, 1.2, 0), 46));
        cut(9.0, "lunge", shot(arc(3.6, 268, 0.7, 3.6, 330, 0.7), p(0, 0.7, -0.4), 50));

        closeUp("mask_hollow", 4);
        closeUp("mask_final", 5);
    }

    private void rite(String name, int level, double length, float rate, Shot camera) {
        scene(name, 0.0, length);
        s.settle = 20;
        s.setup = srv -> base(srv, level, false);
        server(0.1, srv -> {
            HollowForm.skipRite = false;
            HollowForm.toggle(Stage.player(srv));
        });
        speed(0.0, rate);
        cut(0.0, "main", camera);
    }

    private void closeUp(String name, int level) {
        scene(name, 0.0, 6.0);
        s.settle = 70;
        s.gameCamera = true;
        s.setup = srv -> base(srv, level, true);
        cut(0.0, "front", shot(arc(1.9, 158, 1.55, 1.6, 178, 1.58), p(0, 1.56, 0), 32));
        cut(3.0, "side", shot(arc(2.1, 245, 1.62, 1.9, 225, 1.62), p(0, 1.58, 0), 34));
    }

    // ---------------------------------------------------------------- Пустой в бою

    private void hollowFight() {
        getsugaTake("h_wave_a", 4, shot(p(2.0, 0.35, 4.8), p(0, 1.5, -6), 40, 36));
        getsugaTake("h_wave_b", 4, shot(p(9.5, 0.5, -5.5), p(0, 1.2, -7), 46));
        getsugaTake("h_wave_c", 4, shot(p(-1.4, 0.5, -15), p(0, 1.4, -1), 26, 24));
        getsugaTake("ff_wave_a", 5, shot(p(-2.0, 0.35, 4.8), p(0, 1.5, -6), 40, 36));
        getsugaTake("ff_wave_b", 5, shot(p(-11, 2.2, -4), p(0, 1.0, -8), 42));

        scene("h_fight", 0.0, 21.0);
        s.settle = 70;
        s.setup = srv -> Stage.ring(base(srv, 4, true), "G", 8, 8.0, 14.0, 20.0, 340.0, WALKERS, false, 2.0F);
        server(0.0, srv -> {
            Stage.wake(srv, "G");
            ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 1100;
        });
        waves(0.9, 20.0, 2.8, "WG");
        getsuga(0.5, 180.0F, "L1");
        cut(0.0, "reveal", shot(arc(3.0, 178, 0.3, 2.6, 182, 0.35), p(0, 1.7, 0), 40));
        cut(1.2, "wave1", shot(p(9, 1.5, -7), p(0, 1.0, -8), 50));
        getsuga(3.4, 270.0F, "L2");
        cut(3.1, "east_fire", shot(move(-3.0, 0.8, 1.2, -2.8, 0.8, 1.4), p(6, 1.0, 0), 48));
        cut(4.2, "orbit", shot(arc(2.8, 200, 1.0, 2.8, 250, 1.2), p(0, 1.6, 0), 40));
        getsuga(6.2, 90.0F, "L3");
        cut(6.0, "west_fire", shot(p(-3.2, 0.5, 0.6), p(0, 1.6, 0), 46));
        cut(7.0, "crane", shot(arc(14, 135, 8, 12, 120, 6), p(-6, 0, 0), 50, 46, 0, 0, true));
        getsuga(8.7, 0.0F, "L4");
        cut(8.4, "south_fire", shot(p(3.0, 0.4, 1.5), p(-0.5, 1.2, 3.0), 50));
        face(10.1, 180.0F);
        cut(10.1, "mask_close", shot(arc(2.4, 200, 1.6, 2.2, 190, 1.6), p(0, 1.65, 0), 32));
        getsuga(12.2, 225.0F, "L5");
        cut(11.9, "ne_wave", shot(p(9.5, 0.6, -10.5), p(0, 1.2, 0), 44));
        getsuga(14.2, 135.0F, "L6");
        cut(14.0, "nw", shot(p(-2.0, 0.5, -6.5), p(-5, 1.0, -5), 50));
        face(15.5, 180.0F);
        cut(15.5, "sword_close", shot(arcAround(hero(), 1.7, 212, 1.0, 1.5, 236, 1.15), hero().plus(0.35, 0.95, -0.2), 38));
        getsuga(17.5, 180.0F, "L7");
        cut(17.3, "north_high", shot(move(0, 6, -6, 0, 5.5, -5.5), p(0, 1.0, 0), 50));
        cut(18.8, "ground", shot(arc(9, 120, 0.2, 8.5, 128, 0.25), p(0, 1.2, 0), 48));
    }

    private void getsugaTake(String name, int level, Shot camera) {
        scene(name, 0.0, 3.5);
        s.settle = 70;
        s.setup = srv -> Stage.line(base(srv, level, true), "L", 180.0F, 7.0, 14.0, 8, 4.0F);
        speed(0.0, 0.5F);
        server(0.3, srv -> Stage.getsuga(srv, 180.0F));
        cut(0.0, "main", camera);
    }

    // ---------------------------------------------------------------- Яхве ломает меч

    private void swordBreak() {
        scene("break_game", 0.0, 20.0);
        gameCamera();
        s.setup = srv -> base(srv, 4, false);
        server(0.05, srv -> SwordBreak.start(Stage.player(srv)));
        cut(0.0, "game", shot(p(0, 5, 5), p(0, 0, 0), 70));

        // Герой смотрит на север: меч висит в 1,75 блока перед ним, фигура в 14 блоках и 8 вверх.
        scene("break_cine", 0.0, 20.0);
        s.settle = 60;
        s.setup = srv -> base(srv, 4, false);
        server(0.05, srv -> SwordBreak.start(Stage.player(srv)));
        cut(0.0, "behind_low", shot(p(1.1, 0.3, 2.6), p(0, 7, -14), 62, 58));
        cut(5.6, "sword_close", shot(move(1.5, 1.2, -3.4, 1.2, 1.1, -3.1), p(0, 1.1, -1.75), 36));
        cut(7.3, "side", shot(p(12, 2.5, -7), p(0, 6, -11), 50));
        cut(9.5, "front_low", shot(p(-3.5, 0.3, -5), p(0, 9, -12), 40, 34));
        cut(14.0, "wide_side", shot(p(18, 2, -6), p(0, 6, -10), 54));
        cut(15.8, "behind_high", shot(p(-2.5, 3, 4.5), p(0, 5, -10), 52));
    }

    /** Длинный объектив от героя: лицо под маской-плащом, рука, меч в ладони. */
    private void breakClose() {
        scene("break_close", 0.0, 20.0);
        s.settle = 60;
        s.setup = srv -> base(srv, 4, false);
        server(0.05, srv -> SwordBreak.start(Stage.player(srv)));
        cut(0.0, "face", shot(p(0.7, 1.6, 3.0), p(0, 14, -14), 20, 16));
        cut(3.0, "arm", shot(p(-1.0, 1.6, 2.0), p(0, 5, -12), 30, 26));
        cut(7.0, "grip", shot(p(0.4, 1.6, 2.0), p(0, 6, -11), 18));
        cut(10.0, "grip_side", shot(p(-7, 2.0, -3), p(0, 7, -10), 22, 19));
        cut(13.0, "snap", shot(p(0.8, 1.2, 2.5), p(0, 7, -10), 24, 18));
        cut(15.5, "wide", shot(p(0.8, 0.3, 4.5), p(0, 8, -14), 58));
    }

    // ---------------------------------------------------------------- финальная форма

    private void finalForm() {
        scene("ff_orbit", 0.0, 6.0);
        s.settle = 70;
        s.setup = srv -> base(srv, 5, true);
        cut(0.0, "orbit", shot(arc(3.0, 165, 0.3, 2.7, 300, 0.35), p(0, 1.3, 0), 40));

        scene("ff_close", 0.0, 8.0);
        s.settle = 70;
        s.setup = srv -> base(srv, 5, true);
        cut(0.0, "arm", shot(arc(1.8, 225, 1.05, 1.7, 205, 1.05), p(0, 0.95, 0), 38));
        cut(2.0, "leg", shot(arc(1.6, 160, 0.35, 1.5, 140, 0.35), p(0, 0.35, 0), 40));
        cut(4.0, "back", shot(arc(2.6, 30, 1.1, 2.4, 10, 1.2), p(0, 0.9, 0), 42));
        cut(6.0, "blade", shot(arc(2.0, 300, 1.1, 1.9, 280, 1.05), p(-0.35, 0.75, -0.2), 38));

        scene("ff_run", 0.0, 6.0);
        s.settle = 70;
        s.setup = srv -> base(srv, 5, true);
        client(0.1, () -> Director.walkTo = Stage.at(0, 0, -40));
        client(1.0, () -> MinecraftClient.getInstance().options.sprintKey.setPressed(true));
        cut(0.0, "run", shot(heroAt(3.2, 0.7, -2.4), heroAt(0, 1.0, 0), 46));

        scene("ff_swing", 0.0, 6.0);
        s.settle = 70;
        s.setup = srv -> base(srv, 5, true);
        speed(0.0, 0.5F);
        for (int i = 0; i < 4; i++) {
            client(0.3 + i * 1.3, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        }
        cut(0.0, "swing", shot(arc(4.2, 240, 1.0, 4.0, 215, 1.0), p(0, 1.0, 0), 46));

        scene("ff_getsuga", 0.0, 4.0);
        s.settle = 70;
        s.setup = srv -> Stage.line(base(srv, 5, true), "L", 180.0F, 7.0, 14.0, 8, 4.0F);
        speed(0.0, 0.5F);
        server(0.4, srv -> Stage.getsuga(srv, 180.0F));
        cut(0.0, "wave", shot(arc(3.6, 28, 1.0, 3.4, 20, 1.2), p(0, 1.3, -2.5), 50));

        scene("ff_exec", 0.0, 8.0);
        s.settle = 70;
        s.setup = srv -> {
            ServerPlayerEntity p = base(srv, 5, true);
            Stage.spawn(p, "A", EntityType.ZOMBIE, Stage.at(0, 0, -10), 0.0F, false, 0.0F);
            Stage.spawn(p, "B", EntityType.PIGLIN, Stage.at(2, 0, -18), 0.0F, false, 0.0F);
            Stage.spawn(p, "C", EntityType.CREEPER, Stage.at(-2, 0, -26), 0.0F, false, 0.0F);
        };
        speed(0.0, 0.5F);
        server(0.5, srv -> Execution.request(Stage.player(srv), Stage.id("A")));
        server(3.0, srv -> Execution.request(Stage.player(srv), Stage.id("B")));
        server(5.5, srv -> Execution.request(Stage.player(srv), Stage.id("C")));
        cut(0.0, "flash", shot(follow(heroAt(4.8, 1.4, 4.0), 0.5F), follow(heroAt(0, 1.0, -1.0), 0.5F), 50));

        scene("ff_fight", 0.0, 14.0);
        s.settle = 70;
        s.setup = srv -> Stage.ring(base(srv, 5, true), "G", 9, 8.0, 14.0, 10.0, 350.0, WALKERS, false, 2.0F);
        server(0.0, srv -> {
            Stage.wake(srv, "G");
            ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 1100;
        });
        waves(0.9, 13.0, 2.4, "WF");
        getsuga(0.6, 180.0F, "L1");
        cut(0.0, "hero_low", shot(arc(3.2, 205, 0.3, 2.9, 195, 0.35), p(0, 1.6, 0), 40));
        cut(1.6, "wave_side", shot(p(7, 1.0, -6), p(-1, 1.0, -8), 50));
        getsuga(3.8, 90.0F, "L2");
        cut(3.4, "crane", shot(arc(13, 40, 9, 11, 70, 5), p(-3, 0, 0), 52, 46, 0, 0, true));
        getsuga(6.1, 300.0F, "L3");
        cut(5.8, "back_fire", shot(p(-2.6, 0.5, -1.6), p(5, 1.0, 3), 50));
        cut(7.1, "orbit", shot(arc(2.7, 120, 1.0, 2.7, 60, 1.2), p(0, 1.4, 0), 38));
        getsuga(9.2, 45.0F, "L4");
        cut(8.9, "ground", shot(p(4, 0.25, 5), p(-3, 1.3, -3), 48));
        cut(11.0, "top", shot(arc(4, 0, 12, 4, 60, 10), p(0, 0, 0), 56));
    }

    // ---------------------------------------------------------------- Пустой забирает тело

    private void rampage() {
        rampageTake("rampage_game");
        gameCamera();
        cut(0.0, "game", shot(p(0, 5, 5), p(0, 0, 0), 70));

        rampageTake("rampage_wide");
        cut(0.0, "wide", shot(arc(16, 200, 3.5, 15, 500, 5.0), p(0, 1.2, 0), 44));

        // Камера при герое: он дерётся по всей площадке, а не в центре. Едет за ним с небольшим опозданием, иначе скачет вместе с ним.
        rampageTake("rampage_follow_a");
        cut(0.0, "a", shot(arcAround(follow(hero(), 0.04F), 5.0, 180, 0.35, 4.4, 540, 0.5), follow(hero().plus(0, 1.25, 0), 0.04F), 40));

        rampageTake("rampage_follow_b");
        cut(0.0, "b", shot(arcAround(follow(hero(), 0.03F), 3.2, 40, 1.0, 3.0, -300, 1.4), follow(hero().plus(0, 1.2, 0), 0.03F), 38));
    }

    /** Дыра в груди крупно, спереди: он ещё на коленях. */
    private void rampageHole() {
        scene("rampage_hole", 0.0, 10.0);
        s.settle = 60;
        s.setup = srv -> base(srv, 5, true);
        server(0.05, srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            srv.getCommandManager().executeWithPrefix(p.getCommandSource(), "zangetsu rampage");
        });
        cut(0.0, "hole", shot(arc(4.0, 188, 0.7, 3.5, 176, 0.8), p(0, 1.1, 0), 34, 32));
    }

    private void rampageTake(String name) {
        scene(name, 0.0, 41.5);
        s.settle = 60;
        s.setup = srv -> base(srv, 5, true);
        server(0.05, srv -> {
            ServerPlayerEntity p = Stage.player(srv);
            srv.getCommandManager().executeWithPrefix(p.getCommandSource(), "zangetsu rampage");
        });
    }

    // ---------------------------------------------------------------- меч

    private void beauty() {
        swordWake();
        sword("sword_open", Eye.OPEN);
        sword("sword_burning", Eye.BURNING);
    }

    /** Глаз меча открывается: первый кадр клипа. */
    private void swordWake() {
        scene("sword_wake", 0.0, 6.0);
        s.setup = Stage::arena;
        s.settle = 10;
        s.sweepCenter = () -> Stage.at(0, 52, 0);
        Scene scene = s;
        scene.clientSetup = c -> scene.insert = new Insert.Spec(Stage.at(0, 60, 0), new Quaternionf(), Eye.OPEN, v -> {
            double k = Math.max(0.0, Math.min(1.0, (v - 1.6) / 2.2));
            return k * k * (3.0 - 2.0 * k);
        }, true, Insert.light(15, 15));
        cut(0.0, "eye", shot(eye(0.05, 0.02, 0.62), eye(0, 0, 0), 30, 26));
    }

    private void sword(String name, int eyeState) {
        scene(name, 0.0, 6.0);
        s.setup = Stage::arena;
        s.settle = 10;
        s.sweepCenter = () -> Stage.at(0, 52, 0);
        Scene scene = s;
        scene.clientSetup = c -> scene.insert = new Insert.Spec(Stage.at(0, 60, 0), new Quaternionf(), eyeState, v -> 1.0, false, Insert.light(15, 15));
        P grip = p(0, 60, 0);
        cut(0.0, "blade", shot(grip.plus(1.3, -0.5, 1.15), grip.plus(0.75, 0, 0), 44, 40));
        cut(2.0, "eye", shot(eye(0.07, 0.04, 0.46), eye(0, 0, 0), 34, 28));
        cut(4.0, "full", shot(grip.plus(0.62, 0.08, 2.0), grip.plus(0.55, 0, 0), 40, 36));
    }

    // ---------------------------------------------------------------- помощники

    /** Как в обычном трейлере, но меч всегда цел: дубли идут подряд, и предыдущий мог его сломать или разбить маску. */
    private static ServerPlayerEntity base(MinecraftServer srv, int level, boolean hollow) {
        ServerPlayerEntity player = Stage.player(srv);
        ModAttachments.data(player).broken = false;
        ModAttachments.data(player).hollowLock = 0L;
        return Script.base(srv, level, hollow);
    }

    private void gameCamera() {
        s.firstPerson = true;
        s.gameCamera = true;
        s.hideHud = true;
        s.settle = 60;
    }

    private static P heroAt(double x, double y, double z) {
        return (t, d) -> hero().at(t, d).add(x, y, z);
    }

    private static void victim(ServerPlayerEntity p, String name, double x, double z) {
        Vec3d at = Stage.at(x, 0, z);
        Stage.spawn(p, name, EntityType.ZOMBIFIED_PIGLIN, at, Stage.yawToCenter(at), false, 0.0F);
        if (Stage.entity(p.getServer(), name) instanceof LivingEntity living) {
            living.setHealth(living.getMaxHealth());
        }
    }

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

    private void boltAround(double time, double distance, double angle) {
        server(time, srv -> Stage.lightning(srv, TrailerCamera.around(Stage.center, distance, angle, 0.0)));
    }

    private void limb(double time, String name, int part) {
        server(time, srv -> Stage.cut(srv, name, part));
    }

    private void face(double time, float yaw) {
        server(time, srv -> Stage.teleport(Stage.player(srv), Stage.center, yaw, 0.0F));
    }

    private void getsuga(double time, float yaw, String prefix) {
        server(time - 0.9, srv -> Stage.line(Stage.player(srv), prefix, yaw, 7.0, 12.0, 6, 4.0F));
        server(time, srv -> Stage.getsuga(srv, yaw));
    }

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
}
