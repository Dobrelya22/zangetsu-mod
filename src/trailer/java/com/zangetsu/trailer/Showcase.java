package com.zangetsu.trailer;

import static com.zangetsu.trailer.Shots.arc;
import static com.zangetsu.trailer.Shots.eye;
import static com.zangetsu.trailer.Shots.hero;
import static com.zangetsu.trailer.Shots.p;
import static com.zangetsu.trailer.Shots.shot;

import com.zangetsu.logic.Execution;
import com.zangetsu.logic.Eye;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.registry.ModAttachments;
import com.zangetsu.trailer.Shots.P;
import com.zangetsu.trailer.TrailerCamera.Shot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;

/**
 * Витрина для проверки графики (-PtrailerScript=showcase): крупные планы меча и маски со всех сторон и
 * анимации ударов — со стороны и глазами игрока. Снимается тем же движком, что и трейлер, только кадры
 * нужны не для ролика, а чтобы их рассмотреть.
 */
final class Showcase {
    private final List<Scene> scenes = new ArrayList<>();
    private Scene s;

    static List<Scene> build() {
        Showcase showcase = new Showcase();
        showcase.write();
        showcase.scenes.forEach(Scene::seal);
        return showcase.scenes;
    }

    private void write() {
        sword("sword", 0.0, Eye.OPEN);
        sword("burning", 5.0, Eye.BURNING);
        held();
        mask("mask", 12.0, 4);
        swings();
        auto();
        getsuga();
        firstPerson();
        mask("final", 36.0, 5);
        finalGetsuga();
        execute();
        finalForm();
        transformations();
        reactions();
        moves("moves_side", 50.0, 270.0F, false);
        moves("moves_front", 75.0, 205.0F, false);
        moves("moves_fp", 100.0, 0.0F, true);
        moves("moves_back", 125.0, 25.0F, false);
        damage();
    }

    // ---------------------------------------------------------------- меч крупно

    private void sword(String name, double t0, int eyeState) {
        scene(name, t0, t0 + 5.0);
        s.setup = Stage::arena;
        s.settle = 10;
        s.sweepCenter = () -> Stage.at(0, 52, 0);
        Scene scene = s;
        scene.clientSetup = c -> scene.insert = new Insert.Spec(Stage.at(0, 60, 0), new Quaternionf(), eyeState, v -> 1.0, false,
                Insert.light(15, 15));
        // Рукоять — в Stage.at(0, 60, 0), клинок вдоль +X (острие в 1,18 блока), глаз смотрит в +Z.
        P grip = p(0, 60, 0);
        cut(t0, "full", shot(grip.plus(0.62, 0.05, 2.0), grip.plus(0.55, 0, 0), 42));
        cut(t0 + 1.0, "hilt", shot(grip.plus(-0.02, 0.12, 0.5), grip.plus(-0.02, -0.02, 0), 44));
        cut(t0 + 2.0, "blade34", shot(grip.plus(1.15, -0.55, 0.85), grip.plus(0.7, 0, 0), 46));
        cut(t0 + 2.5, "spine", shot(grip.plus(0.95, -0.85, 0.28), grip.plus(0.7, 0.0, 0), 40));
        cut(t0 + 3.0, "pommel", shot(grip.plus(-0.3, -0.1, 0.55), grip.plus(-0.26, -0.22, 0), 46));
        cut(t0 + 4.0, "eye", shot(eye(0.06, 0.04, 0.42), eye(0, 0, 0), 34));
        // Обратная сторона: глаза там нет.
        cut(t0 + 4.5, "reverse", shot(grip.plus(0.62, 0.05, -2.0), grip.plus(0.55, 0, 0), 42));
    }

    // ---------------------------------------------------------------- меч в руке

    private void held() {
        scene("held", 10.0, 12.0);
        s.setup = srv -> Script.base(srv, 4, false);
        cut(10.0, "held_side", shot(arc(2.6, 265, 1.1, 2.6, 285, 1.1), hero().plus(0, 1.0, 0), 44));
        cut(11.0, "held_back", shot(arc(2.4, 25, 1.4, 2.4, 345, 1.4), hero().plus(0, 1.0, 0), 44));
        // Кулак на рукояти крупно.
        cut(11.5, "held_hand", shot(arc(1.3, 262, 0.75), p(-0.38, 0.78, -0.1), 40));
    }

    // ---------------------------------------------------------------- маска

    /** Маска со всех сторон и её трещины: уровень 4 — обычная, уровень 5 — финальная с рогом. */
    private void mask(String name, double t0, int level) {
        scene(name, t0, t0 + 5.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, level, true);
        cut(t0, name + "_front", shot(arc(1.75, 180, 1.55), p(0, 1.55, 0), 40));
        cut(t0 + 1.0, name + "_34", shot(arc(1.85, 215, 1.65), p(0, 1.58, 0), 40));
        cut(t0 + 2.0, name + "_side", shot(arc(2.0, 270, 1.6), p(0, 1.62, 0), 40));
        cut(t0 + 3.0, name + "_top", shot(arc(1.4, 160, 2.3), p(0, 1.6, 0), 42));
        cut(t0 + 4.0, name + "_crack", shot(arc(1.75, 190, 1.55), p(0, 1.55, 0), 40));
        server(t0 + 4.0, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 601);
        server(t0 + 4.33, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 301);
        server(t0 + 4.66, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 101);
    }

    /** Все приёмы подряд в замедлении: сбоку, спереди и глазами игрока. */
    private void moves(String name, double t0, float yaw, boolean firstPerson) {
        double step = 2.6;
        scene(name, t0, t0 + step * 8.0);
        s.firstPerson = firstPerson;
        s.setup = srv -> Script.base(srv, 4, false);
        speed(t0, 0.5F);
        for (int i = 0; i < 8; i++) {
            client(t0 + 0.1 + i * step, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        }
        cut(t0, name + "_all", firstPerson ? shot(p(0, 5, 5), p(0, 0, 0), 70) : shot(arc(3.4, yaw, 1.2), p(0, 1.3, -0.2), 50));
    }

    /** Трещины маски и горящие разломы меча крупно. */
    private void damage() {
        scene("damage", 150.0, 155.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 4, true);
        cut(150.0, "damage_near", shot(arc(1.15, 195, 1.72), p(0, 1.68, 0), 40));
        server(150.0, srv -> ModAttachments.data(Stage.player(srv)).hollowUsed = HollowForm.BUDGET - 101);
        cut(152.0, "damage_side", shot(arc(1.3, 235, 1.8), p(0, 1.68, 0), 40));
    }

    /** Казни: каждый приём на своём мобе, замедленно и сбоку. */
    private void execute() {
        executionScene("zombie_behead", 160.0, EntityType.ZOMBIE, Execution.Variant.BEHEAD);
        executionScene("zombie_waist", 164.0, EntityType.ZOMBIE, Execution.Variant.WAIST);
        executionScene("zombie_cleave", 168.0, EntityType.ZOMBIE, Execution.Variant.CLEAVE);
        executionScene("zombie_kesa", 172.0, EntityType.ZOMBIE, Execution.Variant.KESA);
        executionScene("skeleton_shatter", 176.0, EntityType.SKELETON, Execution.Variant.SHATTER);
        executionScene("wither_shatter", 180.0, EntityType.WITHER_SKELETON, Execution.Variant.SHATTER);
        executionScene("wither_cleave", 184.0, EntityType.WITHER_SKELETON, Execution.Variant.CLEAVE);
        executionScene("creeper_launch", 188.0, EntityType.CREEPER, Execution.Variant.LAUNCH);
        executionScene("creeper_cleave", 192.0, EntityType.CREEPER, Execution.Variant.CLEAVE);
        executionScene("pillager_behead", 196.0, EntityType.PILLAGER, Execution.Variant.BEHEAD);
        executionScene("vindicator_impale", 200.0, EntityType.VINDICATOR, Execution.Variant.IMPALE);
        executionScene("witch_kesa", 204.0, EntityType.WITCH, Execution.Variant.KESA);
        executionScene("ravager_cleave", 208.0, EntityType.RAVAGER, Execution.Variant.CLEAVE);
        executionScene("enderman_waist", 212.0, EntityType.ENDERMAN, Execution.Variant.WAIST);
        executionScene("enderman_kesa", 216.0, EntityType.ENDERMAN, Execution.Variant.KESA);
        executionScene("piglin_kesa", 220.0, EntityType.PIGLIN, Execution.Variant.KESA);
        executionScene("piglin_behead", 224.0, EntityType.PIGLIN, Execution.Variant.BEHEAD);
    }

    /** Финальная форма целиком: плащ, цепи и огонь со всех сторон, крупно, на бегу и от первого лица; мгновенная казнь издалека. */
    private void finalForm() {
        scene("ff_orbit", 230.0, 236.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        cut(230.0, "orbit", shot(arc(3.6, 180, 1.2, 3.6, 540, 1.2), p(0, 1.0, 0), 50));

        scene("ff_close", 236.0, 240.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        cut(236.0, "arm", shot(arc(1.7, 215, 1.05), p(0, 0.95, 0), 42));
        cut(237.0, "leg", shot(arc(1.5, 150, 0.4), p(0, 0.35, 0), 42));
        cut(238.0, "back", shot(arc(2.4, 20, 1.2), p(0, 0.85, 0), 46));
        cut(239.0, "blade", shot(arc(2.0, 290, 1.1), p(-0.35, 0.75, -0.2), 40));

        scene("ff_run", 240.0, 245.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        client(240.1, () -> Director.walkTo = Stage.at(0, 0, -40));
        cut(240.0, "run", shot(heroAt(3.4, 1.2, 3.2), heroAt(0, 1.0, 0), 50));

        scene("ff_fp", 245.0, 248.0);
        s.firstPerson = true;
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        speed(245.0, 0.5F);
        client(245.5, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        cut(245.0, "fp", shot(p(0, 5, 5), p(0, 0, 0), 70));

        scene("ff_exec", 248.0, 256.0);
        s.settle = 70;
        s.setup = srv -> {
            ServerPlayerEntity p = Script.base(srv, 5, true);
            Stage.spawn(p, "A", EntityType.ZOMBIE, Stage.at(0, 0, -10), 0.0F, false, 0.0F);
            Stage.spawn(p, "B", EntityType.PIGLIN, Stage.at(2, 0, -18), 0.0F, false, 0.0F);
            Stage.spawn(p, "C", EntityType.CREEPER, Stage.at(-2, 0, -26), 0.0F, false, 0.0F);
        };
        speed(248.0, 0.5F);
        server(248.5, srv -> Execution.request(Stage.player(srv), Stage.id("A")));
        server(251.0, srv -> Execution.request(Stage.player(srv), Stage.id("B")));
        server(253.5, srv -> Execution.request(Stage.player(srv), Stage.id("C")));
        cut(248.0, "flash", shot(heroAt(4.8, 1.8, 4.0), heroAt(0, 1.0, -1.0), 54));
    }

    /** Превращения: рука по лицу на уровне IV и весь обряд на уровне V, сбоку и спереди. */
    private void transformations() {
        scene("rite_mask", 260.0, 267.5);
        s.settle = 20;
        s.setup = srv -> Script.base(srv, 4, false);
        server(260.1, srv -> {
            HollowForm.skipRite = false;
            HollowForm.toggle(Stage.player(srv));
        });
        speed(260.0, 0.5F);
        cut(260.0, "mask_face", shot(arc(1.9, 208, 1.6), p(0, 1.5, 0), 42));
        cut(264.5, "mask_side", shot(arc(2.8, 190, 1.55), p(0, 1.2, 0), 46));

        scene("rite_final", 268.0, 280.0);
        s.settle = 20;
        s.setup = srv -> Script.base(srv, 5, false);
        server(268.1, srv -> {
            HollowForm.skipRite = false;
            HollowForm.toggle(Stage.player(srv));
        });
        speed(268.0, 0.8F);
        cut(268.0, "rage", shot(arc(3.0, 200, 1.4), p(0, 1.1, 0), 46));
        cut(270.5, "strike", shot(arc(8.0, 190, 2.2), p(0, 2.0, 0), 56));
        cut(271.3, "kneel", shot(arc(3.6, 200, 0.8), p(0, 0.6, 0), 46));
        cut(273.5, "rise", shot(arc(3.2, 180, 0.6), p(0, 1.2, 0), 50));
        cut(275.5, "shoulder", shot(arc(2.8, 212, 1.5), p(0, 1.2, 0), 46));
        cut(277.3, "lunge", shot(arc(5.0, 268, 1.2), p(0, 1.0, -1.0), 50));
    }

    /** Бег, удары и Гецуга: как плащ, цепи и огонь отвечают на движение. */
    private void reactions() {
        scene("react_run", 280.0, 286.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        client(280.1, () -> Director.walkTo = Stage.at(0, 0, -60));
        client(283.0, () -> MinecraftClient.getInstance().options.sprintKey.setPressed(true));
        cut(280.0, "run_side", shot(heroAt(4.0, 1.2, 1.4), heroAt(0, 1.0, 0), 48));

        scene("react_swing", 286.0, 292.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        speed(286.0, 0.5F);
        for (int i = 0; i < 4; i++) {
            client(286.3 + i * 1.3, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        }
        cut(286.0, "swing_all", shot(arc(4.2, 240, 1.3), p(0, 1.0, 0), 50));
    }

    /** Точка рядом с героем, куда бы он ни убежал. */
    private static P heroAt(double x, double y, double z) {
        return (t, d) -> hero().at(t, d).add(x, y, z);
    }

    private void executionScene(String name, double t0, EntityType<? extends MobEntity> type, Execution.Variant variant) {
        scene("x_" + name, t0, t0 + 3.6);
        // Уровень II: с третьего меч сам зарубит моба, пока сцена готовится.
        s.setup = srv -> Stage.spawn(Script.base(srv, 2, false), "V", type, Stage.at(0, 0, -2.6), 0.0F, false, 0.0F);
        speed(t0, 0.6F);
        server(t0 + 0.1, srv -> Stage.execute(srv, "V", variant));
        cut(t0, name, shot(arc(4.8, 250, 1.4), p(0, 1.0, -1.3), 46));
    }

    /** Гецуга финальной формы: та же анимация, волна крупнее. */
    private void finalGetsuga() {
        scene("final_getsuga", 41.0, 43.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 5, true);
        speed(41.0, 0.4F);
        server(41.2, srv -> Stage.getsuga(srv, 180.0F));
        cut(41.0, "wave", shot(arc(3.4, 25, 1.6), p(0, 1.3, -2.0), 52));
    }

    // ---------------------------------------------------------------- анимации со стороны

    /** Удары игрока: серия замахов в замедлении, камера сбоку и спереди. */
    private void swings() {
        scene("swing", 17.0, 21.0);
        s.setup = srv -> Script.base(srv, 4, false);
        speed(17.0, 0.35F);
        for (int i = 0; i < 6; i++) {
            client(17.1 + i * 0.62, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        }
        cut(17.0, "swing_side", shot(arc(3.2, 270, 1.1), p(0, 1.2, 0), 46));
        cut(19.0, "swing_front", shot(arc(3.2, 205, 1.2), p(0, 1.2, 0), 46));
    }

    /** Меч бьёт сам: зомби подходит — удар. */
    private void auto() {
        scene("auto", 21.0, 24.0);
        s.setup = srv -> {
            ServerPlayerEntity p = Script.base(srv, 4, false);
            Stage.spawn(p, "A1", EntityType.ZOMBIE, Stage.at(0, 0, -5), 0.0F, false, 0.0F);
            Stage.spawn(p, "A2", EntityType.ZOMBIE, Stage.at(1.5, 0, -6), 0.0F, false, 0.0F);
        };
        server(21.0, srv -> Stage.wake(srv, "A"));
        speed(21.0, 0.5F);
        cut(21.0, "auto_side", shot(arc(4.0, 255, 1.2), p(0, 1.1, -1.5), 46));
        cut(22.5, "auto_front", shot(arc(3.4, 160, 1.3), p(0, 1.2, 0), 46));
    }

    /** Гецуга Теншо: замах — волна. */
    private void getsuga() {
        scene("getsuga", 24.0, 28.0);
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 4, true);
        speed(24.0, 0.4F);
        server(24.2, srv -> Stage.getsuga(srv, 180.0F));
        server(26.2, srv -> Stage.getsuga(srv, 180.0F));
        cut(24.0, "getsuga_side", shot(arc(3.6, 268, 1.2), p(0, 1.3, -0.8), 48));
        cut(26.0, "getsuga_back", shot(arc(3.4, 25, 1.6), p(0, 1.3, -2.0), 52));
    }

    // ---------------------------------------------------------------- глазами игрока

    private void firstPerson() {
        scene("fp_swing", 28.0, 31.0);
        s.firstPerson = true;
        s.setup = srv -> Script.base(srv, 4, false);
        speed(28.0, 0.35F);
        for (int i = 0; i < 4; i++) {
            client(28.1 + i * 0.7, () -> MinecraftClient.getInstance().player.swingHand(Hand.MAIN_HAND));
        }
        cut(28.0, "fp", shot(p(0, 5, 5), p(0, 0, 0), 70));

        scene("fp_getsuga", 31.0, 34.0);
        s.firstPerson = true;
        s.settle = 70;
        s.setup = srv -> Script.base(srv, 4, true);
        speed(31.0, 0.4F);
        server(31.2, srv -> Stage.getsuga(srv, 180.0F));
        cut(31.0, "fp", shot(p(0, 5, 5), p(0, 0, 0), 70));

        scene("fp_auto", 34.0, 36.0);
        s.firstPerson = true;
        s.setup = srv -> {
            ServerPlayerEntity p = Script.base(srv, 4, false);
            Stage.spawn(p, "B1", EntityType.ZOMBIE, Stage.at(0, 0, -4), 0.0F, false, 0.0F);
        };
        server(34.0, srv -> Stage.wake(srv, "B"));
        speed(34.0, 0.5F);
        cut(34.0, "fp", shot(p(0, 5, 5), p(0, 0, 0), 70));
    }

    // ---------------------------------------------------------------- словарь

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
}
